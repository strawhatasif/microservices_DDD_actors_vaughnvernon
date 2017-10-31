name := "donebyme/common"

organization := "donebyme"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.jcenterRepo
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

scalaVersion := "2.11.11"

libraryDependencies ++= Seq(
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "org.scalactic" %% "scalactic" % "3.0.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % Test,
  "com.typesafe.akka" %% "akka-persistence" % "2.5.6",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1",
  "com.google.code.gson" % "gson" % "2.8.2"
)
