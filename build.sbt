val commonSettings = Seq(
  organization := "com.github.phisgr",
  scalaVersion := "2.12.8"
)

val gatlingVersion = "3.2.0"

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
    version := "0.5.0",
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    PB.targets in Test := Seq(
      scalapb.gen() -> (sourceManaged in Test).value
    ),
    scalacOptions ++= Seq(
      "-language:existentials",
      "-language:implicitConversions",
    ),
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion % "protobuf",
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "io.gatling" % "gatling-core" % gatlingVersion
    ),
    libraryDependencies ++= Seq(
      "io.gatling.highcharts" % "gatling-charts-highcharts" % gatlingVersion % "test",
      "io.gatling" % "gatling-test-framework" % gatlingVersion % "test"
    ),
  )

lazy val bench = (project in file("bench"))
  .dependsOn(root)
  .enablePlugins(JmhPlugin)
  .settings(
    PB.targets in Compile := Seq(
      scalapb.gen() -> (sourceManaged in Compile).value
    )
  )
