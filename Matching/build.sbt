name := "matching"

organization := "donebyme"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"
resolvers += Resolver.jcenterRepo

scalaVersion := "2.11.11"

// must be one of:
// 9001       (Registry)
// 9002/9999  (Topics)
// 9003/9998  (Matching)
// 9004/9997  (Pricing)
// 9005/9996  (Scheduling)

initialize ~= { _ =>
  System.setProperty( "http.host", "localhost" )
  System.setProperty( "http.port", "9003" )
  System.setProperty( "broadcast.port", "9998" )
}

libraryDependencies ++= Seq(
  ws,
  "org.scala-lang.modules" % "scala-async_2.11" % "0.9.6",
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "org.scalactic" %% "scalactic" % "3.0.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % Test,
  "com.typesafe.akka" %% "akka-persistence" % "2.5.6",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.1.1",
  "donebyme" %% "donebyme-common" % "1.0-SNAPSHOT",
  "com.google.code.gson" % "gson" % "2.8.2"
)
