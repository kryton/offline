import com.typesafe.sbt.gzip.Import._

name := """offline"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
  filters,
  jdbc,
  anorm,
  cache,
  ws,
  "com.unboundid" % "unboundid-ldapsdk" % "2.3.6",
    "org.webjars" %% "webjars-play" % "2.3.0",
    "org.webjars" % "bootstrap" % "3.2.0",
    "org.webjars" % "bootswatch-superhero" % "3.2.0",
    "org.webjars" % "html5shiv" % "3.7.2",
    "org.webjars" % "requirejs" % "2.1.14",
    "org.webjars" % "respond" % "1.4.2"
)

LessKeys.compress in Assets := true

pipelineStages := Seq(digest, gzip)

includeFilter in (Assets, LessKeys.less) := "*.less"

excludeFilter in (Assets, LessKeys.less) := "_*.less"
