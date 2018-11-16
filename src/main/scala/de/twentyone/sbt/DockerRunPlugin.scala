package de.twentyone.sbt

import java.util.concurrent.atomic.AtomicReference

import sbt.Keys._
import sbt._

import scala.collection.mutable
import de.twentyone.ProcessUtil.ReProcess
import de.twentyone.ProcessUtil.stringToProcess

object DockerRunPlugin extends AutoPlugin {

  object autoImport {
    case class DockerRunContainer(image: String,
                                  environment: Seq[(String, String)] = Seq.empty,
                                  containerName: Option[String] = None,
                                  containerPorts: Seq[Int] = Seq.empty,
                                  snapshotName: Option[String] = None,
                                  waitHealthy: Boolean = true,
                                  dockerArgs: Seq[String] = Seq.empty,
                                  dependsOn: Seq[String] = Seq.empty,
                                  mounts: Seq[(String, String)] = Seq.empty)

    val dockerRunContainers =
      SettingKey[Seq[(String, DockerRunContainer)]]("docker-run-containers")
    val dockerRunNetwork =
      SettingKey[String]("docker-run-network")
    val dockerRunStart    = TaskKey[Map[String, Seq[Int]]]("docker-run-start")
    val dockerRunStop     = TaskKey[Unit]("docker-run-stop")
    val dockerRunSnapshot = TaskKey[Unit]("docker-run-snapshot")
  }

  import autoImport._

  val runContainers = new AtomicReference[Option[RunContainers]](None)

  sys.addShutdownHook(cleanUpContainers)

  override def trigger = allRequirements

  lazy val baseSettings = Seq(
    dockerRunContainers := Seq(),
    dockerRunNetwork := s"${name.value}-${configuration.value.name}-docker-run",
    dockerRunStart := {
      val log = streams.value.log
      runContainers.compareAndSet(None,
                                  Some(
                                    new RunContainers(s"${name.value}-${configuration.value.name}",
                                                      version.value,
                                                      log,
                                                      dockerRunNetwork.value,
                                                      dockerRunContainers.value)))
      val run = runContainers.get().get

      if (!run.start()) {
        sys.error("Docker run: Startup failed")
      }
      run.portMappings
    },
    dockerRunStop := {
      runContainers.get().foreach(_.stop())
      runContainers.set(None)
    },
    dockerRunSnapshot := {
      val log = streams.value.log
      dockerRunContainers.value.foreach {
        case (ref, runContainer) =>
          val containerName =
            runContainer.containerName.getOrElse(
              s"${name.value}-${configuration.value.name}-$ref-docker-run-${version.value}")

          runContainer.snapshotName.foreach { snapshotName =>
            log.info(s"Snapshotting $containerName to $snapshotName")

            s"docker stop $containerName".!(log)
            s"docker commit $containerName $snapshotName".!(log)
          }
          log.info(s"Removing $containerName")
          s"docker rm -f $containerName".!(log)

          log.info(s"Removing network ${dockerRunNetwork.value}")
          s"docker network rm ${dockerRunNetwork.value}".!(log)
      }
      runContainers.set(None)
    }
  )

  override lazy val projectSettings = Seq(Test, IntegrationTest) flatMap { conf =>
    inConfig(conf)(baseSettings)
  }

  def cleanUpContainers() = {
    runContainers.get().foreach(_.stop())
  }

  def findEnvOrSysProp(name: String): Option[String] =
    sys.props.get(name).orElse(sys.env.get(name)).filterNot(_.isEmpty)
}
