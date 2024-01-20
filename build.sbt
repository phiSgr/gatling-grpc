// the enterprisePackage task is confused
ThisBuild / Gatling / publishArtifact := false
ThisBuild / GatlingIt / publishArtifact := false

val commonSettings = Seq(
  organization := "com.github.phisgr",
  scalaVersion := "2.13.12",
  crossPaths := false,
)

val gatlingVersion = "3.9.5"
val gatlingCore = "io.gatling" % "gatling-core" % gatlingVersion

val publishSettings = {
  import xerial.sbt.Sonatype.*
  Seq(
    publishTo := SonatypeKeys.sonatypePublishTo.value,
    publishMavenStyle := true,

    licenses := Seq("APL2" -> url("http://www.apache.org/licenses/LICENSE-2.0.txt")),
    sonatypeProjectHosting := Some(GitHubHosting("phiSgr", "gatling-grpc", "phisgr@gmail.com")),
  )
}

lazy val root = (project in file("."))
  .enablePlugins(GatlingPlugin)
  .settings(commonSettings *)
  .settings(publishSettings *)
  .settings(
    name := "gatling-grpc",
    version := "0.17.0",
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    Test / PB.targets := Seq(
      scalapb.gen() -> (Test / sourceManaged).value,
      PB.gens.java -> (Test / sourceManaged).value,
    ),
    scalacOptions ++= Seq(
      "-language:existentials",
      "-language:implicitConversions",
      "-language:higherKinds",
      "-Xlint",
      "-opt:l:method",
    ),
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      gatlingCore,
      "com.github.phisgr" % "gatling-ext" % "0.5.0",
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
      "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
      "org.scalatest" %% "scalatest" % "3.2.12" % "test",
    ),
  )
  .dependsOn(macroSub % "compile-internal")

lazy val macroSub = (project in file("macro"))
  .settings(commonSettings *)
  .settings(
    name := "macro",
    libraryDependencies ++= Seq(
      gatlingCore,
    ),
    scalacOptions ++= Seq(
      "-language:experimental.macros",
    ),
  )

lazy val bench = (project in file("bench"))
  .settings(commonSettings *)
  .dependsOn(root)
  .enablePlugins(JmhPlugin)
  .settings(
    Compile / PB.targets := Seq(
      PB.gens.java -> (Compile / sourceManaged).value,
      scalapb.gen() -> (Compile / sourceManaged).value
    ),
  )
  .dependsOn(macroSub % "compile-internal")
