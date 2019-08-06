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

import cats.effect.IO
import fs2.concurrent.Queue
import org.scalatest.mockito.MockitoSugar
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FreeSpec, Matchers}
import org.telegram.api.updates.TLAbsUpdates
import org.telegram4s.{Arbitraries, Env}

class Telegram4sMainHandlerTest extends FreeSpec with Env with MockitoSugar with Matchers with GeneratorDrivenPropertyChecks with Arbitraries {

  "it should feed a queue with updates signalled by the client" in {

    forAll { updates: List[TLAbsUpdates] =>
      val (stream, handler) =
        (for {
          queue   <- Queue.unbounded[IO, Either[Throwable, TLAbsUpdates]]
          handler <- new MainHandlerFactory[IO].create(mock[IKernelComm], queue)
          stream  <- IO(queue.dequeue.rethrow)
        } yield (stream, handler)).unsafeRunSync()

      updates.foreach(handler.onUpdate)

      val results = stream.take(updates.size).compile.toList.unsafeRunSync()

      results should contain theSameElementsInOrderAs updates
    }

  }
}
