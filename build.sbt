name := "akka-blockchain"

version := "0.1"

scalaVersion := "2.12.10"

val akkaVersion = "2.6.4"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "org.bouncycastle" % "bcprov-jdk15on" % "1.64",
  "commons-codec" % "commons-codec" % "1.14"
)
