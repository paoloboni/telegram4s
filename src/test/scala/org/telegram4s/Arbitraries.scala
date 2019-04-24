package org.telegram4s

import java.util.concurrent.TimeUnit

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.mockito.MockitoSugar
import org.telegram.api.chat.TLChat
import org.telegram.api.message.TLMessage
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.tl.TLObject

import scala.concurrent.duration.{Duration, FiniteDuration}

trait Arbitraries { self: MockitoSugar =>
  implicit val arbitraryTLChat: Arbitrary[TLChat] = Arbitrary(arbitrary[Int].map(_ => mock[TLChat]))

  implicit val arbitraryTLMessage: Arbitrary[TLMessage] = Arbitrary(arbitrary[Int].map(_ => mock[TLMessage]))

  implicit val arbitraryTLAbsUpdates: Arbitrary[TLAbsUpdates] = Arbitrary(arbitrary[Int].map(_ => mock[TLAbsUpdates]))

  implicit val arbitraryTLObject: Arbitrary[TLObject] = Arbitrary(arbitrary[Int].map(_ => mock[TLObject]))

  implicit val arbitraryShortDuration: Arbitrary[FiniteDuration] = Arbitrary {
    for {
      millis <- Gen.choose[Long](0, Int.MaxValue)
    } yield Duration(millis, TimeUnit.MILLISECONDS)
  }
}
