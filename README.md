# telegram4s

[![Build Status](https://travis-ci.com/paoloboni/telegram4s.svg?branch=master)](https://travis-ci.com/paoloboni/telegram4s)
[![Latest version](https://img.shields.io/maven-central/v/io.github.paoloboni/telegram4s_2.12.svg)](https://search.maven.org/artifact/io.github.paoloboni/telegram4s_2.12)

Telegram client for Scala

## Quick start

If you use sbt add the following dependency to your build file:

```sbtshell
libraryDependencies += "io.github.paoloboni" %% "telegram4s" % "<version>"
```

## Example

```scala
import cats.effect.{ExitCode, IO, IOApp}
import log.effect.LogWriter
import org.telegram.api.updates.TLAbsUpdates
import org.telegram.bot.kernel.MainHandlerFactory
import org.telegram.bot.structure.IUser
import org.telegram4s.TelegramClient

object TelegramSampleApp extends IOApp {

  private implicit val mainHandlerFactory: MainHandlerFactory[IO] = new MainHandlerFactory[IO]

  private val log: LogWriter[IO] = implicitly

  private val clientApiKey  = 123455
  private val clientApiHash = "6e6bc4e49dd477ebc98ef4046c067b5f"
  private val phoneNumber   = "+4407700000000"

  val bob: IUser = ???

  override def run(args: List[String]) = {
    val program = for {
      client <- TelegramClient.initialise(
        clientApiKey = clientApiKey,
        clientApiHash = clientApiHash,
        phoneNumber = phoneNumber,
        stateFilePath = "/tmp/api_state",
        getAuthCode = getAuthCode
      )
      events <- client.getUpdatesStream
      processEvents <- events
        .map(eventHandler)
        .evalMap(log.info(_))
        .compile
        .drain
        .start
      _ <- client.sendMessage(bob, "Hi Bob!")
      _ <- processEvents.join
    } yield ()
    program.redeem(
      { t =>
        t.printStackTrace()
        ExitCode(1)
      },
      _ => ExitCode.Success
    )
  }

  private def eventHandler(updates: TLAbsUpdates): String = {
    // handle possible instances of org.telegram.api.updates.TLAbsUpdates
    "event received"
  }

  private def getAuthCode: IO[String] =
    for {
      _        <- IO.pure(println(s"Enter authentication code: "))
      authCode <- IO.pure(scala.io.StdIn.readLine())
    } yield authCode

}
```