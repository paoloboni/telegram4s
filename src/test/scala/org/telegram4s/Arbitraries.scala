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

import java.lang
import java.util.concurrent.TimeUnit

import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.mockito.MockitoSugar
import org.telegram.api.chat.TLChat
import org.telegram.api.message.TLMessage
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.bot.structure.IUser
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

  implicit val arbitraryIUser: Arbitrary[IUser] = Arbitrary {
    for {
      userId   <- arbitrary[Int]
      userHash <- arbitrary[Long]
    } yield new IUser {
      override def getUserId: Int         = userId
      override def getUserHash: lang.Long = userHash
    }
  }
}
