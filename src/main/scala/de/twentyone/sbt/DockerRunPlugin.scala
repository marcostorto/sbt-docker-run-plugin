package de.twentyone.sbt

import sbt.Keys._
import sbt._

object DockerRunPlugin extends AutoPlugin {

  object autoImport {
    val dockerRunImage         = SettingKey[String]("docker-run-image")
    val dockerRunContainerName = SettingKey[String]("docker-run-container-name")
    val dockerRunContainerPort = SettingKey[Int]("docker-run-container-port")
    val dockerRunSnapshotName  = SettingKey[String]("docker-run-snapshot-name")
    val dockerRunEnvironment   = SettingKey[Seq[(String, String)]]("docker-run-environment")
    val dockerRunWaitHealthy   = SettingKey[Boolean]("docker-run-wait-healthy")
    val dockerRunStart         = TaskKey[Int]("docker-run-start")
    val dockerRunStop          = TaskKey[Unit]("docker-run-stop")
    val dockerRunSnapshot      = TaskKey[Unit]("docker-run-snapshot")
  }

  import autoImport._

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    dockerRunContainerName := s"${name.value}-docker-run-${version.value}",
    dockerRunEnvironment := Seq.empty,
    dockerRunWaitHealthy := true,
    dockerRunStart := {
      val envParameters = dockerRunEnvironment.value
        .map {
          case (name, value) => s"-e $name=$value"
        }
        .mkString(" ")
      val port = findFreePort()
      s"docker rm -f ${dockerRunContainerName.value}".!(streams.value.log)

      s"docker run -d --name ${dockerRunContainerName.value} $envParameters -p$port:${dockerRunContainerPort.value} ${dockerRunImage.value}"
        .!(streams.value.log)

      if (dockerRunWaitHealthy.value && !waitHealthy(dockerRunContainerName.value,
                                                     streams.value.log)) {
        sys.error("Docker container did not become healthy")
      }

      port
    },
    dockerRunStop := {
      s"docker rm -f ${dockerRunContainerName.value}".!(streams.value.log)
    },
    dockerRunSnapshot := {
      streams.value.log
        .info(s"Snapshotting ${dockerRunContainerName.value} to ${dockerRunSnapshotName.value}")
      s"docker stop ${dockerRunContainerName.value}".!(streams.value.log)
      s"docker commit ${dockerRunContainerName.value} ${dockerRunSnapshotName.value}"
        .!(streams.value.log)
      s"docker rm -f ${dockerRunContainerName.value}".!(streams.value.log)
    }
  )

  def waitHealthy(containerName: String, log: Logger): Boolean = {
    Range(0, 100).exists { _ =>
      // Double querying somewhat circumvents potential hickup that might occur
      Thread.sleep(2000)

      val status1 = ("docker inspect -f \"{{.State.Health.Status}}\" " + containerName).!!

      Thread.sleep(2000)

      val status2 = ("docker inspect -f \"{{.State.Health.Status}}\" " + containerName).!!

      log.info(s"ITDocker is $status1")
      status1.contains("healthy") && status2.contains("healthy")
    }
  }

  def findFreePort() = {
    var socket = new java.net.ServerSocket(0)
    socket.setReuseAddress(true)

    val port = socket.getLocalPort
    socket.close()
    port
  }

  def findEnvOrSysProp(name: String): Option[String] =
    sys.props.get(name).orElse(sys.env.get(name)).filterNot(_.isEmpty)
}
