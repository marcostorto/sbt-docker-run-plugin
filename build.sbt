
name := "sbt-docker-run-plugin"

organization := "de.21re"

version := {
  "0.2-21p3"
}

sbtPlugin := true

crossSbtVersions := Seq("1.2.8")

libraryDependencies ++= Seq()

resolvers += "JCenter" at "http://jcenter.bintray.com"

publishMavenStyle := true

sonatypeProfileName := "21re"


publishTo := {
  val nexus =
    "http://repo02.tecniplastgroup.com:8081/"
  Some(
    "releases" at nexus + "repository/maven-releases/"
  )
}