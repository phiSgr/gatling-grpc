val commonSettings = Seq(
  organization := "com.github.phisgr",
  scalaVersion := "2.12.10"
)

val gatlingVersion = "3.3.1"
val gatlingCore = "io.gatling" % "gatling-core" % gatlingVersion

val publishSettings = {
  import xerial.sbt.Sonatype._
  Seq(
    publishTo := SonatypeKeys.sonatypePublishTo.value,
    publishMavenStyle := true,

    licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    sonatypeProjectHosting := Some(GitHubHosting("phiSgr", "gatling-grpc", "phisgr@gmail.com")),
  )
}


lazy val root = (project in file("."))
  .enablePlugins(GatlingPlugin)
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "gatling-grpc",
    version := "0.8.2-SNAPSHOT",
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    PB.targets in Test := Seq(
      scalapb.gen() -> (sourceManaged in Test).value
    ),
    scalacOptions ++= Seq(
      "-language:existentials",
      "-language:implicitConversions",
      "-language:higherKinds",
    ),
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      gatlingCore,
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
      "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
      "org.scalatest" %% "scalatest" % "3.0.8" % "test",
    ),
  )

val gatlingJavaPbVersion = "1.0.0"
lazy val javaPb = (project in file("java-pb"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "gatling-javapb",
    version := gatlingJavaPbVersion,
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "3.9.1",
      gatlingCore,
    ),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:experimental.macros",
    ),
  )

lazy val javaPbIjExt = (project in file("java-pb-intellij"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "gatling-javapb-ijext",
    ideaPluginName in ThisBuild := "gatling-javapb-ijext",
    ideaBuild in ThisBuild := "193.3519.25",
    version := gatlingJavaPbVersion,
    ideaInternalPlugins += "java",
    ideaExternalPlugins += "org.intellij.scala".toPlugin,
  )
  .enablePlugins(SbtIdeaPlugin)

lazy val bench = (project in file("bench"))
  .dependsOn(root, javaPb)
  .enablePlugins(JmhPlugin)
  .settings(
    PB.targets in Compile := Seq(
      PB.gens.java -> (sourceManaged in Compile).value,
      scalapb.gen() -> (sourceManaged in Compile).value
    ),
  )
