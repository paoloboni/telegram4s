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
