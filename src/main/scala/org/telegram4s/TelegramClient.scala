/*
 * Copyright (c) 2019 Paolo Boni
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.telegram4s

import java.util.logging.Level

import cats.effect._
import cats.syntax.all._
import cats.{Monad, MonadError}
import fs2._
import fs2.concurrent.Queue
import log.effect.LogWriter
import log.effect.fs2.SyncLogWriter._
import org.slf4j._
import org.telegram.api.chat.TLAbsChat
import org.telegram.api.engine.{LoggerInterface, RpcException, TimeoutException}
import org.telegram.api.functions.messages.{TLRequestMessagesGetAllChats, TLRequestMessagesGetHistory}
import org.telegram.api.input.peer.TLInputPeerChannel
import org.telegram.api.message.{TLAbsMessage, TLMessage, TLMessageEmpty, TLMessageService}
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.bot.TelegramFunctionCallback
import org.telegram.bot.kernel.engine.MemoryApiState
import org.telegram.bot.kernel.{MainHandlerFactory, _}
import org.telegram.bot.services.BotLogger
import org.telegram.bot.structure.{BotConfig, LoginStatus}
import org.telegram.mtproto.log.{LogInterface, Logger}
import org.telegram.tl.{TLIntVector, TLMethod, TLObject}

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.higherKinds

class TelegramClient[F[_]: ConcurrentEffect: Timer: MainHandlerFactory: LogWriter](kernelComm: KernelComm)(
    implicit
    F: Effect[F]
) {

  def getUpdatesStream: F[Stream[F, TLAbsUpdates]] =
    F.delay(for {
      buffer  <- Stream.eval(Queue.bounded[F, Either[Throwable, TLAbsUpdates]](100))
      handler <- Stream.eval(MainHandlerFactory[F].create(kernelComm, buffer))
      _ = kernelComm.setMainHandler(handler)
      event <- buffer.dequeue.rethrow
    } yield event)

  def getUserChats: F[List[TLAbsChat]] =
    for {
      request <- F.pure {
        val request = new TLRequestMessagesGetAllChats
        request.setExceptIds(new TLIntVector)
        request
      }
      _        <- LogWriter.info("Retrieving user chats...")
      response <- doRpcCallAsync(request)
    } yield response.getChats.asScala.toList

  private def getChannelHistory(channelId: Int, channelAccessHash: Long, offset: Int = 0) = {
    def getInputPeer(accessHash: Long, channelId: Int) = F.pure {
      val chatPeer = new TLInputPeerChannel
      chatPeer.setChannelId(channelId)
      chatPeer.setAccessHash(accessHash)
      chatPeer
    }

    for {
      chatPeer <- getInputPeer(channelAccessHash, channelId)
      getMessages <- F.pure {
        val request = new TLRequestMessagesGetHistory
        request.setPeer(chatPeer)
        request.setLimit(50)
        request.setOffsetId(offset)
        request
      }
      result <- doRpcCallAsync(getMessages)
    } yield result
  }

  def getChannelHistoryStream(channelId: Int,
                              channelAccessHash: Long,
                              channelTitle: String,
                              offset: Int = 0): F[Stream[F, TLAbsMessage]] =
    for {
      _      <- LogWriter.debug(s"Retrieving history for channel '$channelTitle' with offset $offset")
      result <- getChannelHistory(channelId, channelAccessHash, offset)
      messages      = result.getMessages.asScala
      oldestMessage = messages.lastOption
    } yield
      Stream.fromIterator(result.getMessages.asScala.toIterator) ++
        oldestMessage
          .map {
            case m: TLMessage        => m.getId
            case m: TLMessageEmpty   => m.getId
            case m: TLMessageService => m.getId
          }
          .map(id =>
            Stream.eval(getChannelHistoryStream(channelId, channelAccessHash, channelTitle, id)).flatMap(identity))
          .getOrElse(Stream.empty)

  def doRpcCallAsync[Result <: TLObject](request: TLMethod[Result])(implicit E: MonadError[F, Throwable]): F[Result] =
    for {
      _ <- LogWriter.debug(s"RPC async call to: $request")
      async = F.async[Result] { cb =>
        kernelComm.doRpcCallAsync(
          request.asInstanceOf[TLMethod[TLObject]],
          new TelegramFunctionCallback[TLObject] {
            override def onSuccess(result: TLObject): Unit    = cb(Right(result.asInstanceOf[Result]))
            override def onRpcError(e: RpcException): Unit    = cb(Left(e))
            override def onTimeout(e: TimeoutException): Unit = cb(Left(e))
            override def onUnknownError(e: Throwable): Unit   = cb(Left(e))
          }
        )
      }
      result <- async.recoverWith {
        case e @ RpcExceptionFloodWait(seconds) =>
          for {
            _ <- LogWriter.warn("Received flood wait error - waiting...", e)
            _ <- Timer[F].sleep(seconds.seconds)
            _ <- LogWriter.info("Retrying now")
            r <- doRpcCallAsync(request)
          } yield r
        case t: Throwable => E.raiseError(new Exception(t))
      }
    } yield result
}

private object RpcExceptionFloodWait {
  private val floodWait = "FLOOD_WAIT_(\\d*)".r

  def unapply(arg: Throwable): Option[Int] =
    Option(arg)
      .collect { case e: RpcException => e.getErrorTag }
      .collect { case floodWait(seconds) => seconds.toInt }
}

object TelegramClient {

  def initialise[F[_]: ConcurrentEffect: Timer: MainHandlerFactory](
      clientApiKey: Int,
      clientApiHash: String,
      phoneNumber: String,
      stateFilePath: String,
      getAuthCode: F[String])(implicit F: Monad[F], E: MonadError[F, Throwable]): F[TelegramClient[F]] = {

    def createApiState(filePath: String) =
      F.pure(new MemoryApiState(filePath))

    def createKernelComm(apiKey: Int, apiState: MemoryApiState) = F.pure {
      val kernelComm = new KernelComm(apiKey, apiState)
      kernelComm.init()
      // fixes: https://github.com/rubenlagus/TelegramApi/issues/56
      kernelComm.getApi.getApiContext.registerClass(classOf[TLChatFull])
      kernelComm
    }

    def doAuthentication(apiKey: Int,
                         apiHash: String,
                         apiState: MemoryApiState,
                         botConfig: BotConfig,
                         kernelComm: KernelComm)(implicit log: LogWriter[F]): F[KernelComm] =
      F.pure(
          new KernelAuth(
            apiState,
            botConfig,
            kernelComm,
            apiKey,
            apiHash
          ))
        .flatMap {
          case kernelAuth if kernelAuth.getApiState.isAuthenticated =>
            F.pure(kernelComm)
          case kernelAuth =>
            F.pure(kernelAuth.start()).flatMap {
              case LoginStatus.CODESENT =>
                for {
                  _        <- log.info(s"Code sent to the user")
                  authCode <- getAuthCode
                  loggedIn <- F.pure(kernelAuth.setAuthCode(authCode))
                  _        <- log.info(s"Login success: $loggedIn")
                } yield kernelComm
              case LoginStatus.ALREADYLOGGED => F.pure(kernelComm)
              case other                     => E.raiseError(new Exception(s"Login status not successful: $other"))
            }
        }

    lazy val botConfig = new BotConfig {
      override def getPhoneNumber: String = phoneNumber
      override def getBotToken: String    = null
      override def isBot: Boolean         = false
    }

    val mtprotoLogger     = LoggerFactory.getLogger("MTPROTO")
    val telegramApiLogger = LoggerFactory.getLogger("TELEGRAMAPI")

    def configureLogging() = F.pure {
      BotLogger.setLevel(Level.OFF)
      Logger.registerInterface(new LogInterface() {
        override def w(tag: String, message: String): Unit = mtprotoLogger.warn(message)
        override def d(tag: String, message: String): Unit = mtprotoLogger.debug(message)
        override def e(tag: String, message: String): Unit = mtprotoLogger.error(message)
        override def e(tag: String, t: Throwable): Unit    = mtprotoLogger.error("error", t)
      })
      org.telegram.api.engine.Logger.registerInterface(new LoggerInterface() {
        override def w(tag: String, message: String): Unit = telegramApiLogger.warn(message)
        override def d(tag: String, message: String): Unit = telegramApiLogger.debug(message)
        override def e(tag: String, t: Throwable): Unit    = telegramApiLogger.error("error", t)
      })
    }

    for {
      logger              <- log4sLog[F](classOf[TelegramClient[F]])
      _                   <- configureLogging()
      apiState            <- createApiState(stateFilePath)
      kernelComm          <- createKernelComm(clientApiKey, apiState)
      kernelCommAfterAuth <- doAuthentication(clientApiKey, clientApiHash, apiState, botConfig, kernelComm)(logger)
    } yield {
      implicit val l = logger
      new TelegramClient(kernelCommAfterAuth)
    }
  }
}
