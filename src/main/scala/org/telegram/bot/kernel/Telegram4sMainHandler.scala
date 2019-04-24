package org.telegram.bot.kernel

import cats.effect.{Effect, IO}
import fs2.concurrent.Queue
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.bot.handlers.{DefaultUpdatesHandler, UpdatesHandlerBase}
import org.telegram.bot.kernel.differenceparameters.DifferenceParametersService

import scala.language.higherKinds
import scala.util.Try

class Telegram4sMainHandler(kernelComm: IKernelComm, updatesHandler: UpdatesHandlerBase) extends MainHandler(kernelComm, updatesHandler) {
  override def onUpdate(updates: TLAbsUpdates): Unit = super.onUpdate(updates)
}

class MainHandlerFactory[F[_]] {
  def create(kernelComm: IKernelComm, queue: Queue[F, Either[Throwable, TLAbsUpdates]])(
      implicit
      F: Effect[F]
  ): F[Telegram4sMainHandler] = F.delay {
    val dbManager                   = new InMemoryDatabaseManager()
    val differenceParametersService = new DifferenceParametersService(dbManager)
    new Telegram4sMainHandler(kernelComm, new DefaultUpdatesHandler(kernelComm, differenceParametersService, dbManager)) {
      override def onUpdate(updates: TLAbsUpdates): Unit = {
        F.runAsync(queue.enqueue1 {
            Try {
              super.onUpdate(updates)
              updates
            }.toEither
          })(_ => IO.unit)
          .unsafeRunSync()
      }
    }
  }
}

object MainHandlerFactory {
  def apply[F[_]](implicit factory: MainHandlerFactory[F]): MainHandlerFactory[F] = factory
}
