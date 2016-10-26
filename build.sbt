import com.typesafe.sbt.packager.docker._

lazy val root = (project in file(".")).
  settings(
    name := "messaging-cluster",
    version := "0.61",
    mainClass in Compile := Some("io.bigfast.messaging.MessagingServer"),
    scalaVersion := "2.11.8"
  ).
  enablePlugins(JavaAppPackaging)

packageName in Docker := "gcr.io/deductive_tree_102115/messaging-cluster"
dockerBaseImage := "develar/java:8u45"
dockerCommands := dockerCommands.value flatMap {
  case cmd@Cmd("FROM", _) => List(cmd, Cmd("RUN", "apk update && apk add bash"))
  case other              => List(other)
}

lazy val akkaVersion = "2.4.11"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-cluster" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-tools" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-core" % akkaVersion,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.trueaccord.scalapb" %% "scalapb-runtime-grpc" % "0.5.42",
  "io.grpc" % "grpc-all" % "1.0.1",
  "io.netty" % "netty-tcnative-boringssl-static" % "1.1.33.Fork22",
  "org.scalatest" %% "scalatest" % "2.1.6" % "test")

PB.targets in Compile := Seq(
  scalapb.gen() -> (sourceManaged in Compile).value
)
