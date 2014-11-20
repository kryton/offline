import com.typesafe.sbt.gzip.Import._

name := """offline"""

version := "1.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += (
    "Local Maven Repository" at "file:///" + Path.userHome.absolutePath + "/.m2/repository"
    )

scalaVersion := "2.11.2"

libraryDependencies ++= Seq(
    filters,
    jdbc,
    anorm,
    cache,
    ws,
    "mysql" % "mysql-connector-java" % "5.1.33",
    "com.typesafe.play" %% "play-slick" % "0.8.0",
    "com.unboundid" % "unboundid-ldapsdk" % "2.3.7",
    "org.webjars" %% "webjars-play" % "2.3.0",
    "org.webjars" % "bootstrap" % "3.3.0",
    "org.webjars" % "bootswatch" % "3.2.0-2-SNAPSHOT",
    "org.webjars" % "html5shiv" % "3.7.2",
    "org.webjars" % "requirejs" % "2.1.14-3",
    "org.webjars" % "respond" % "1.4.2",
    "org.imgscalr" % "imgscalr-lib" % "4.2",
    "com.sksamuel.scrimage" %% "scrimage-core" % "1.4.2",
    "com.sksamuel.scrimage" %% "scrimage-canvas" % "1.4.2",
    "com.sksamuel.scrimage" %% "scrimage-filters" % "1.4.2",
    "com.kenshoo" %% "metrics-play" % "2.3.0_0.1.7",
    "com.github.nscala-time" %% "nscala-time" % "1.4.0"
)

LessKeys.compress in Assets := true

pipelineStages := Seq(rjs, digest, gzip)

includeFilter in(Assets, LessKeys.less) := "*.less"

excludeFilter in(Assets, LessKeys.less) := "_*.less"
