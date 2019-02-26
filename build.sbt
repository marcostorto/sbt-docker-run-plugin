name := "sbt-docker-run-plugin"

organization := "de.21re"

version := {
  "0.2-" + sys.props.get("BUILD_NUMBER").orElse(sys.env.get("BUILD_NUMBER")).getOrElse("SNAPSHOT")
}

sbtPlugin := true

crossSbtVersions := Seq("1.2.8")

libraryDependencies ++= Seq()

resolvers += "JCenter" at "http://jcenter.bintray.com"

publishMavenStyle := false

bintrayOrganization := Some("21re")

bintrayRepository := "public"

bintrayCredentialsFile := {
  sys.props
    .get("BINTRAY_CREDENTIALS")
    .orElse(sys.env.get("BINTRAY_CREDENTIALS"))
    .map(new File(_))
    .getOrElse(baseDirectory.value / ".bintray" / "credentials")
}
