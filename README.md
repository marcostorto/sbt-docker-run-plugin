# sbt-docker-run-plugin

Simple wrapper to start a docker container and potentially create a snapshot (for testing/development).

Current use-case for this is to startup a postgres instance for running integration tests and take a snapshot
of it afterwards to be used a fixture set for local development. There might be other use-cases as well ...

## Usage

In `project/plugins.sbt`:

```
resolvers += Resolver.url("21re-bintray-plugins", url("http://dl.bintray.com/21re/public"))(Resolver.ivyStylePatterns)

addSbtPlugin("de.21re" % "sbt-docker-run-plugin" % "0.1-3")
```

The following tasks with be added:
* `dockerRunStart` start the docker container in detached-mode
* `dockerRunStop` stop and delete the docker container (data will be lost)
* `dockerRunSnapshot` stop the docker container, take snapshot and then delete it (i.e. and image will be created)

The following settings are supported:
* `dockerRunImage` (required) the docker image to run
* `dockerRunContainerPort` (required) the container port to publish (atm its assumed that there is only one)
* `dockerRunWaitHealthy` wait until docker container has become healthy (defaults to true). NOTE: this only works if the docker image has defined a HEALTHCHECK.
* `dockerRunSnapshotName` (required) the name of the docker image to create
* `dockerRunContainerName` the container name to use (defaults to: `<project-name>-docker-run-<project-version>`)
* `dockerRunEnvironment` list of environment variables to set (as sequence of (String, String), default: empty)

## Examples

### Run a postgres on integration test

In build.sbt:

```
dockerRunSnapshotName := {
  (dockerRepository in Docker).value.map(_ + "/").getOrElse("") + (packageName in Docker).value + ":database-" + version.value 
}

dockerRunImage := "<postgres-with-healthcheck>/postgres:latest" // This is not public atm, take "postgres:latest" as stating point

dockerRunContainerPort := 5432

dockerRunEnvironment := Seq(
  "POSTGRES_DB" -> "it-database",
  "POSTGRES_USER" -> "ituser",
  "POSTGRES_PASSWORD" -> "itpass",
  "PGDATA" -> "/data" // This ensures that the postgres database will be actually part of the snapshot image
)

javaOptions in IntegrationTest := { // Example how to inject the connection string to integration test
  val bindHost = LocalEnvironment.findEnvOrSysProp("BIND_HOST").getOrElse("localhost")
  val result = Seq(
    "DEFAULT_DB_URL"  -> s"jdbc:postgresql://$bindHost:${dockerRunStart.value}/it-database",
    "DEFAULT_DB_USER" -> "ituser",
    "DEFAULT_DB_PASSWORD" -> "itpass"
  ).map {
    case (key, value) => s"-D$key=$value"
  }

  streams.value.log.info(s"Using environment: \n  ${result.mkString("\n  ")}")
  result
}

fork in IntegrationTest := true

test in IntegrationTest := {
  dockerRunSnapshot.dependsOn(test in IntegrationTest).value
}
```