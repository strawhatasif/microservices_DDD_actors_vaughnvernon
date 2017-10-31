name := "service-registry"

organization := "donebyme"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

resolvers += Resolver.jcenterRepo
resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

scalaVersion := "2.11.11"

// must be one of:
// 9001       (Registry)
// 9002/9999  (Topics)
// 9003/9998  (Matching)
// 9004/9997  (Pricing)
// 9005/9996  (Scheduling)

initialize ~= { _ =>
  System.setProperty( "http.host", "localhost" )
  System.setProperty( "http.port", "9001" )
  System.setProperty( "broadcast.ports", "9990,9991,9992,9993,9994,9995,9996,9997,9998,9999" )
}

libraryDependencies ++= Seq(
  ws,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2" % Test,
  "org.scalactic" %% "scalactic" % "3.0.4",
  "org.scalatest" %% "scalatest" % "3.0.4" % "test",
  "com.typesafe.akka" %% "akka-actor" % "2.5.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.6" % Test
)
