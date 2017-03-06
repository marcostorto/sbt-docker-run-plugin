# sbt-docker-run-plugin

Simple wrapper to start a docker container and potentially create a snapshot (for testing/development).

Current use-case for this is to startup a postgres instance for running integration tests and take a snapshot
of it afterwards to be used a fixture set for local development. There might be other use-cases as well ...

## Usage

In `project/plugins.sbt`:

```
resolvers += Resolver.url("21re-bintray-plugins", url("http://dl.bintray.com/21re/public"))(Resolver.ivyStylePatterns)

addSbtPlugin("de.21re" % "sbt-docker-run-plugin" % "0.1-4")
```

The following tasks with be added:
* `dockerRunStart` start the docker container in detached-mode, returns map of published ports
* `dockerRunStop` stop and delete the docker container (data will be lost)
* `dockerRunSnapshot` stop the docker container, take snapshot and then delete it (i.e. and image will be created)

The following settings are supported:
* `dockerRunContainers` (required) sequence of container definitions to start (Seq[(String, DockerRunContainer)])

## Examples

### Run a postgres on integration test

In build.sbt:

```
dockerRunContainers := Seq(
    "postgres" -> DockerRunContainer(
      // This image is not public atm, take "postgres:latest" as stating point
      image = "<postgres-with-healthcheck>/postgres:latest", 
      containerPort = Some(5432),
      environment = Seq(
        "POSTGRES_DB" -> "it-database",
        "POSTGRES_USER" -> "ituser",
        "POSTGRES_PASSWORD" -> "itpass",
        "PGDATA" -> "/data" // This ensures that the postgres database will be actually part of the snapshot image
      ),
      snapshotName = Some((dockerRepository in Docker).value
        .map(_ + "/")
        .getOrElse("") + (packageName in Docker).value + ":database-" + version.value
    )
  )

javaOptions in IntegrationTest := {  // Example how to inject the connection string to integration test
  val bindHost = LocalEnvironment.findEnvOrSysProp("BIND_HOST").getOrElse("localhost")
  val bindPorts = dockerRunStart.value
  val result = Seq(
    "DEFAULT_DB_URL"  -> s"jdbc:postgresql://$bindHost:${bindPorts("postgres")}/it-database",
    "DEFAULT_DB_USER" -> "ituser"
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