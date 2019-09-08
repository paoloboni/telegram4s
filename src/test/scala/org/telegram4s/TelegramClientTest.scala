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

import cats.effect.laws.util.TestContext
import cats.effect.{Effect, IO, Timer}
import fs2.concurrent.Queue
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{EitherValues, FreeSpec, Matchers}
import org.telegram.api.chat.{TLAbsChat, TLChat}
import org.telegram.api.engine.storage.AbsApiState
import org.telegram.api.engine.{RpcException, TimeoutException}
import org.telegram.api.message.{TLAbsMessage, TLMessage}
import org.telegram.api.messages.TLMessages
import org.telegram.api.messages.chats.TLMessagesChats
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.bot.TelegramFunctionCallback
import org.telegram.bot.handlers.UpdatesHandlerBase
import org.telegram.bot.kernel.{IKernelComm, KernelComm, MainHandlerFactory, Telegram4sMainHandler}
import org.telegram.bot.structure.IUser
import org.telegram.tl.{TLMethod, TLObject, TLVector}

import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration._

class TelegramClientTest
    extends FreeSpec
    with Env
    with MockitoSugar
    with Matchers
    with Eventually
    with ScalaFutures
    with GeneratorDrivenPropertyChecks
    with Arbitraries
    with EitherValues {

  var handlerStub: Telegram4sMainHandler = _

  implicit val mainHandlerFactory: MainHandlerFactory[IO] = new MainHandlerFactory[IO] {
    override def create(kernelComm: IKernelComm, queue: Queue[IO, Either[Throwable, TLAbsUpdates]])(
        implicit F: Effect[IO]
    ): IO[Telegram4sMainHandler] = {
      handlerStub = new Telegram4sMainHandler(mock[IKernelComm], mock[UpdatesHandlerBase]) {
        override def onUpdate(updates: TLAbsUpdates): Unit = queue.enqueue1(Right(updates)).unsafeRunSync()
      }
      IO.delay(handlerStub)
    }
  }

  "it should return the list of chats" in {

    forAll { expectedChats: List[TLChat] =>
      val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {

        override def doRpcCallAsync[T <: TLObject](method: TLMethod[T], callback: TelegramFunctionCallback[T]): Unit = {
          val result = new TLVector[TLAbsChat]
          result.addAll(expectedChats.asJava)

          val chats = new TLMessagesChats() {
            override def getChats: TLVector[TLAbsChat] = result
          }
          callback.onSuccess(chats.asInstanceOf[T])
        }
      }
      val client = new TelegramClient[IO](kernelCommStub)

      client.getUserChats.unsafeRunSync() should contain theSameElementsInOrderAs expectedChats
    }

  }

  "it should wait and retry on flood-wait exception" in {

    implicit val ec               = TestContext()
    implicit val timer: Timer[IO] = ec.timer[IO]

    forAll { (expectedResult: TLObject, backoff: FiniteDuration) =>
      val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {

        var hasFlooded = false

        override def doRpcCallAsync[T <: TLObject](method: TLMethod[T], callback: TelegramFunctionCallback[T]) {

          if (hasFlooded) {
            callback.onSuccess(expectedResult.asInstanceOf[T])
          } else {
            callback.onRpcError(new RpcException(1, s"FLOOD_WAIT_${backoff.toSeconds}"))
            hasFlooded = true
          }
        }
      }
      val client = new TelegramClient[IO](kernelCommStub)

      val eventualResult = client.doRpcCallAsync(mock[TLMethod[TLObject]]).unsafeToFuture()

      ec.tick(backoff)

      eventualResult.futureValue shouldBe expectedResult
    }

  }

  "it should fail on timeout exception" in {

    val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {

      override def doRpcCallAsync[T <: TLObject](method: TLMethod[T], callback: TelegramFunctionCallback[T]): Unit = {
        callback.onTimeout(new TimeoutException())
      }
    }
    val client = new TelegramClient[IO](kernelCommStub)

    val expectedException = intercept[Exception] {
      client.doRpcCallAsync(mock[TLMethod[TLObject]]).unsafeRunSync()
    }
    expectedException.getCause shouldBe a[TimeoutException]
  }

  "it should fail on unknown error" in {

    val anException = new Exception("failure")

    val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {

      override def doRpcCallAsync[T <: TLObject](method: TLMethod[T], callback: TelegramFunctionCallback[T]): Unit = {
        callback.onUnknownError(anException)
      }
    }
    val client = new TelegramClient[IO](kernelCommStub)

    val expectedException = intercept[Exception] {
      client.doRpcCallAsync(mock[TLMethod[TLObject]]).unsafeRunSync()
    }
    expectedException.getCause shouldBe anException
  }

  "it should return the channel history" in {
    forAll { expectedMessages: List[TLMessage] =>
      val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {
        var noMoreMessages = false

        override def doRpcCallAsync[T <: TLObject](method: TLMethod[T], callback: TelegramFunctionCallback[T]): Unit = {
          val result = new TLVector[TLAbsMessage]

          val messages = new TLMessages() {
            {
              if (noMoreMessages) {
                setMessages(result)
              } else {
                result.addAll(expectedMessages.asJava)
                setMessages(result)
                noMoreMessages = true
              }
            }
          }
          callback.onSuccess(messages.asInstanceOf[T])
        }
      }

      val client = new TelegramClient[IO](kernelCommStub)

      client
        .getChannelHistoryStream(1, 0L, "a channel")
        .unsafeRunSync()
        .compile
        .toList
        .unsafeRunSync() should contain theSameElementsInOrderAs expectedMessages
    }

  }

  "it should return live updates" in {

    forAll { expectedUpdates: List[TLAbsUpdates] =>
      val client = new TelegramClient[IO](mock[KernelComm])

      val results = new ListBuffer[TLAbsUpdates]

      client.getUpdatesStream
        .unsafeRunSync()
        .map(results.append(_))
        .compile
        .drain
        .unsafeToFuture()

      expectedUpdates.foreach(handlerStub.onUpdate(_))

      eventually {
        results should contain theSameElementsInOrderAs expectedUpdates
      }
    }
  }

  "it should successfully send a message to a user" in {
    forAll { (user: IUser, message: String, result: TLAbsUpdates) =>
      val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {
        override def sendMessageAsync(user: IUser, message: String, callback: TelegramFunctionCallback[TLAbsUpdates]): Unit =
          callback.onSuccess(result)
      }
      val client = new TelegramClient[IO](kernelCommStub)

      client.sendMessage(user, message).unsafeRunSync() shouldBe result
    }
  }

  "it should return an error when sending message fails" in {
    forAll { (user: IUser, message: String) =>
      val error = new RpcException(1, "message failed")

      val kernelCommStub = new KernelComm(0, mock[AbsApiState]) {
        override def sendMessageAsync(user: IUser, message: String, callback: TelegramFunctionCallback[TLAbsUpdates]): Unit =
          callback.onRpcError(error)
      }
      val client = new TelegramClient[IO](kernelCommStub)

      client.sendMessage(user, message).attempt.unsafeRunSync().left.value shouldBe error
    }
  }
}
