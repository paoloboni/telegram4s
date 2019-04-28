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

package org.telegram.bot.kernel

import cats.effect.{Effect, IO}
import fs2.concurrent.Queue
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.bot.handlers.{DefaultUpdatesHandler, UpdatesHandlerBase}
import org.telegram.bot.kernel.differenceparameters.DifferenceParametersService

import scala.language.higherKinds
import scala.util.Try

class Telegram4sMainHandler(kernelComm: IKernelComm, updatesHandler: UpdatesHandlerBase)
    extends MainHandler(kernelComm, updatesHandler) {
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
