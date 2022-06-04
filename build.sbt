val commonSettings = Seq(
  organization := "com.github.phisgr",
  scalaVersion := "2.13.8",
  crossPaths := false,
)

val gatlingVersion = "3.7.5-SNAPSHOT" // from publishLocal
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
    version := "0.13.1-SNAPSHOT",
    inConfig(Test)(sbtprotoc.ProtocPlugin.protobufConfigSettings),
    Test / PB.targets := Seq(
      scalapb.gen() -> (Test / sourceManaged).value,
      PB.gens.java -> (Test / sourceManaged).value,
      PB.gens.plugin("grpc-java") -> (Test / sourceManaged).value
    ),
    libraryDependencies += ("io.grpc" % "protoc-gen-grpc-java" % "1.46.0") asProtocPlugin(),
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
      "com.github.phisgr" % "gatling-ext" % "0.3.0" intransitive(),
//      "io.gatling.highcharts" % "gatling-charts-highcharts" % "3.7.6" % "test",
      "io.gatling" % "gatling-test-framework" % gatlingVersion % "test",
      "org.scalatest" %% "scalatest" % "3.2.12" % "test",
    ),
  )
  .dependsOn(macroSub % "compile-internal")

lazy val macroSub = (project in file("macro"))
  .settings(commonSettings: _*)
  .settings(
    name := "macro",
    libraryDependencies ++= Seq(
      gatlingCore,
    ),
    scalacOptions ++= Seq(
      "-language:experimental.macros",
    ),
  )

// Usually the two update together (for specifying IntelliJ compatibility)
val gatlingJavaPbVersion = "1.2.0"
val gatlingJavaPbExtVersion = "1.2.0"
lazy val javaPb = (project in file("java-pb"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "gatling-javapb",
    version := gatlingJavaPbVersion,
    libraryDependencies ++= Seq(
      "com.google.protobuf" % "protobuf-java" % "3.20.1",
      gatlingCore,
    ),
    scalacOptions ++= Seq(
      "-language:implicitConversions",
      "-language:experimental.macros",
    ),
  )
  .dependsOn(root % "test->test")

lazy val javaPbIjExt = (project in file("java-pb-intellij"))
  .settings(commonSettings: _*)
  .settings(publishSettings: _*)
  .settings(
    name := "gatling-javapb-ijext",
    ThisBuild / intellijPluginName := "gatling-javapb-ijext",
    // https://www.jetbrains.com/idea/download/other.html
    ThisBuild / intellijBuild := "221.5591.52",
    version := gatlingJavaPbExtVersion,
    // https://plugins.jetbrains.com/plugin/1347-scala/versions/stable
    intellijPlugins += "org.intellij.scala:2022.1.14".toPlugin,
  )
  .enablePlugins(SbtIdeaPlugin)

lazy val bench = (project in file("bench"))
  .settings(commonSettings: _*)
  .dependsOn(root, javaPb, kt)
  .enablePlugins(JmhPlugin)
  .settings(
    Compile / PB.targets := Seq(
      PB.gens.java -> (Compile / sourceManaged).value,
      scalapb.gen() -> (Compile / sourceManaged).value
    ),
    kotlinVersion := "1.6.21",
  )
  .dependsOn(macroSub % "compile-internal")

lazy val kt = (project in file("kt"))
  .settings(commonSettings: _*)
  .settings(
    kotlinVersion := "1.6.21",
    kotlinLib("stdlib"),
    libraryDependencies += "io.gatling" % "gatling-core-java" % gatlingVersion,
    javacOptions ++= Seq("-Xlint:unchecked", "-Xdiags:verbose"),
  )
  .dependsOn(root % "compile->compile;test->test")
