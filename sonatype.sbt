lazy val contributors = Seq(
  "paoloboni" -> "Paolo Boni"
)

useGpg := true
pgpSecretRing := pgpPublicRing.value

publishTo := sonatypePublishTo.value

sonatypeProfileName := "io.github.paoloboni"
publishMavenStyle := true
pomExtra := {
  <developers>
    {for ((username, name) <- contributors) yield
    <developer>
      <id>{username}</id>
      <name>{name}</name>
      <url>http://github.com/{username}</url>
    </developer>
    }
  </developers>
}
scmInfo := Some(
  ScmInfo(
    url("https://github.com/paoloboni/telegram4s"),
    "scm:git@github.com:paoloboni/telegram4s.git"
  )
)
headerLicense := Some(HeaderLicense.MIT("2019", "Paolo Boni"))
licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))
homepage := Some(url("https://github.com/paoloboni/telegram4s"))
