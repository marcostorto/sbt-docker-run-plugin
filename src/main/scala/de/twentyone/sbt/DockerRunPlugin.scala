package de.twentyone.sbt

import sbt.Keys._
import sbt._

import scala.collection.mutable

object DockerRunPlugin extends AutoPlugin {

  object autoImport {
    case class DockerRunContainer(image: String,
                                  environment: Seq[(String, String)] = Seq.empty,
                                  containerName: Option[String] = None,
                                  containerPort: Option[Int] = None,
                                  snapshotName: Option[String] = None,
                                  waitHealthy: Boolean = true)

    val dockerRunContainers =
      SettingKey[Seq[(String, DockerRunContainer)]]("docker-run-containers")
    val dockerRunNetwork =
      SettingKey[String]("docker-run-network")
    val dockerRunStart    = TaskKey[Map[String, Int]]("docker-run-start")
    val dockerRunStop     = TaskKey[Unit]("docker-run-stop")
    val dockerRunSnapshot = TaskKey[Unit]("docker-run-snapshot")
  }

  import autoImport._

  val dockerPortMappings = new mutable.HashMap[String, Int] with mutable.SynchronizedMap[String, Int]
  val dockerContainers = new mutable.HashSet[String] with mutable.SynchronizedSet[String]
  val dockerNetworks = new mutable.HashSet[String] with mutable.SynchronizedSet[String]

  sys.addShutdownHook(cleanUpContainers)

  override def trigger = allRequirements

  override lazy val projectSettings = Seq(
    dockerRunNetwork := s"${name.value}-docker-run",
    dockerRunStart := {
      if(!dockerNetworks.contains(dockerRunNetwork.value)) {
        streams.value.log.info(s"Creating network ${dockerRunNetwork.value}")
        dockerNetworks.add(dockerRunNetwork.value)
        s"docker network create ${dockerRunNetwork.value}".!(streams.value.log)
      }

      val freePorts = findFreePorts(dockerRunContainers.value.length)
      dockerRunContainers.value.zipWithIndex.map {
        case ((ref, _), _) if dockerPortMappings.contains(ref) =>
          ref -> dockerPortMappings(ref)
        case ((ref, runContainer), idx) =>
          val envParameters = runContainer.environment
            .map {
              case (name, value) => s"-e $name=$value"
            }
            .mkString(" ")
          val publishParameters = runContainer.containerPort
            .map { containerPort =>
              s"-p${freePorts(idx)}:$containerPort"
            }
            .getOrElse("")
          val containerName =
            runContainer.containerName.getOrElse(s"${name.value}-$ref-docker-run-${version.value}")

          streams.value.log.info(s"Starting ${runContainer.image} as $containerName")
          dockerContainers.add(containerName)
          s"docker run -d --name $containerName $envParameters $publishParameters --network ${dockerRunNetwork.value} --network-alias $ref ${runContainer.image}"
            .!(streams.value.log)

          if (runContainer.waitHealthy && !waitHealthy(containerName, streams.value.log)) {
            sys.error(s"Docker container $ref did not become healthy")
          }
          dockerPortMappings.put(ref, freePorts(idx))
          ref -> freePorts(idx)
      }.toMap
    },
    dockerRunStop := {
      dockerRunContainers.value.foreach {
        case (ref, runContainer) =>
          val containerName =
            runContainer.containerName.getOrElse(s"${name.value}-$ref-docker-run-${version.value}")

          streams.value.log.info(s"Removing $containerName")
          s"docker rm -f $containerName".!(streams.value.log)
          dockerContainers.remove(containerName)
          dockerPortMappings.remove(ref)

          streams.value.log.info(s"Removing network ${dockerRunNetwork.value}")
          s"docker network rm ${dockerRunNetwork.value}".!(streams.value.log)
          dockerNetworks.remove(dockerRunNetwork.value)
      }
    },
    dockerRunSnapshot := {
      dockerRunContainers.value.foreach {
        case (ref, runContainer) =>
          val containerName =
            runContainer.containerName.getOrElse(s"${name.value}-$ref-docker-run-${version.value}")

          runContainer.snapshotName.foreach { snapshotName =>
            streams.value.log.info(s"Snapshotting $containerName to $snapshotName")

            s"docker stop $containerName".!(streams.value.log)
            s"docker commit $containerName $snapshotName".!(streams.value.log)
          }
          streams.value.log.info(s"Removing $containerName")
          s"docker rm -f $containerName".!(streams.value.log)
          dockerContainers.remove(containerName)
          dockerPortMappings.remove(ref)

          streams.value.log.info(s"Removing network ${dockerRunNetwork.value}")
          s"docker network rm ${dockerRunNetwork.value}".!(streams.value.log)
          dockerNetworks.remove(dockerRunNetwork.value)
      }
    }
  )

  def waitHealthy(containerName: String, log: Logger): Boolean = {
    Range(0, 100).exists { _ =>
      // Double querying somewhat circumvents potential hickup that might occur
      Thread.sleep(2000)

      val status1 = ("docker inspect -f \"{{.State.Health.Status}}\" " + containerName).!!

      Thread.sleep(2000)

      val status2 = ("docker inspect -f \"{{.State.Health.Status}}\" " + containerName).!!

      log.info(s"$containerName is $status1")
      status1.contains("healthy") && status2.contains("healthy")
    }
  }

  def findFreePorts(count: Int) = {
    val sockets = Range(0, count).map { _ =>
      var socket = new java.net.ServerSocket(0)
      socket.setReuseAddress(true)
      socket
    }
    val ports = sockets.map(_.getLocalPort)
    sockets.foreach(_.close())
    ports
  }

  def cleanUpContainers() = {
    dockerContainers.foreach {
      containerName =>
        println(s"Terminating docker container $containerName")
        s"docker rm -f $containerName".!
    }
    dockerNetworks.foreach {
      networkName =>
        println(s"Terminating docker network $networkName")
        s"docker network rm $networkName".!
    }
  }

  def findEnvOrSysProp(name: String): Option[String] =
    sys.props.get(name).orElse(sys.env.get(name)).filterNot(_.isEmpty)
}
