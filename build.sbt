import java.util.regex.Pattern
import sbt.Keys.scalacOptions

name := "delightful-parsing"
organization := "org.sweet-delights"
homepage := Option(url("https://github.com/sweet-delights/delightful-parsing"))
licenses := List("GNU Lesser General Public License Version 3" -> url("https://www.gnu.org/licenses/lgpl-3.0.txt"))
description := "delightful-parsing is a Scala library for parsing string containing fixed-width columns"
scmInfo := Option(ScmInfo(url("https://github.com/sweet-delights/delightful-parsing"), "scm:git@github.com:sweet-delights/delightful-parsing.git"))
developers := List(
  Developer(
    id = "pgrandjean",
    name = "Patrick Grandjean",
    email = "pgrandjean.github.com@gmail.com",
    url = url("https://github.com/pgrandjean")
  )
)
scalaVersion := "2.12.13"
crossScalaVersions := Seq("2.11.12", "2.12.13", "2.13.6")
checksums in update := Nil
libraryDependencies ++= Seq(
  "org.scala-lang"     % "scala-reflect"           % scalaVersion.value % Provided,
  "org.sweet-delights" %% "delightful-typeclasses" % "0.1.1",
  "org.specs2"         %% "specs2-core"            % "4.5.1" % "test"
)
scalacOptions ++= Seq(
  "-deprecation",
  "-target:jvm-1.8",
  "-feature"
)
javacOptions in Compile ++= Seq(
  "-source",
  "1.8",
  "-target",
  "1.8"
)
scalafmtOnCompile in ThisBuild := true
publishMavenStyle := true
publishTo := Some {
  val nexus = "https://oss.sonatype.org/"
  if (isSnapshot.value)
    "snapshots" at nexus + "content/repositories/snapshots"
  else
    "releases" at nexus + "service/local/staging/deploy/maven2"
}
// sbt-release
import sbtrelease._
import ReleaseTransformations._
releaseCrossBuild := true
releaseVersion := { ver =>
  val bumpedVersion = Version(ver)
    .map { v =>
      suggestedBump.value match {
        case Version.Bump.Bugfix => v.withoutQualifier.string
        case _ => v.bump(suggestedBump.value).withoutQualifier.string
      }
    }
    .getOrElse(versionFormatError(ver))
  bumpedVersion
}
releaseNextVersion := { ver =>
  Version(ver).map(_.withoutQualifier.bump.string).getOrElse(versionFormatError(ver)) + "-SNAPSHOT"
}
releaseCommitMessage := s"[sbt-release] setting version to ${(version in ThisBuild).value}"
bugfixRegexes := List(s"${Pattern.quote("[patch]")}.*").map(_.r)
minorRegexes := List(s"${Pattern.quote("[minor]")}.*").map(_.r)
majorRegexes := List(s"${Pattern.quote("[major]")}.*").map(_.r)
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeRelease"),
  pushChanges
)
