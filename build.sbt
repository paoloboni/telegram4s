name := "telegram4s"

scalaVersion := "2.12.8"

scalafmtOnCompile := false

lazy val root = (project in file("."))
  .settings(
    organization := "io.github.paoloboni",
    libraryDependencies ++= Seq(
      "org.telegram"   % "telegramapi"       % "66.2",
      "co.fs2"         %% "fs2-core"         % "1.0.5" % "provided",
      "org.typelevel"  %% "cats-core"        % "1.6.0" % "provided",
      "org.typelevel"  %% "cats-effect"      % "1.4.0" % "provided",
      "io.laserdisc"   %% "log-effect-core"  % "0.6.1",
      "io.laserdisc"   %% "log-effect-fs2"   % "0.6.1",
      "org.slf4j"      % "slf4j-api"         % "1.7.26",
      "org.slf4j"      % "slf4j-simple"      % "1.7.26" % "test",
      "org.scalacheck" %% "scalacheck"       % "1.14.0" % "test",
      "org.typelevel"  %% "cats-effect-laws" % "1.2.0" % "test",
      "org.mockito"    %% "mockito-scala"    % "1.2.1" % "test",
      "org.scalatest"  %% "scalatest"        % "3.0.5" % "test"
    ),
    scalafmtOnCompile in ThisBuild := true
  )
  .enablePlugins(AutomateHeaderPlugin)

import ReleaseTransformations._

releaseCrossBuild := false
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommand("publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeReleaseAll"),
  pushChanges
)
