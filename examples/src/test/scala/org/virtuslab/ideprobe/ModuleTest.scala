package org.virtuslab.ideprobe

import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util
import java.util.concurrent.Executors

import org.junit.Assert
import org.junit.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.virtuslab.ideprobe.Extensions._
import org.virtuslab.ideprobe.dependencies.InternalPlugins
import org.virtuslab.ideprobe.dependencies.Plugin
import org.virtuslab.ideprobe.protocol.{JUnitRunConfiguration, ModuleRef, Setting}
import org.virtuslab.intellij.scala.SbtProbeDriver
import org.virtuslab.intellij.scala.protocol.{SbtProjectSettings, SbtProjectSettingsChangeRequest}

import scala.concurrent.ExecutionContext

class SbtTestSuite extends IntegrationTestSuite {
  protected def fixtureFromConfig(configName: String): IntelliJFixture =
    transformFixture(IntelliJFixture.fromConfig(Config.fromClasspath(configName)))

  val scalaProbePlugin: Plugin = InternalPlugins.bundle("ideprobe-scala")

  override protected def transformFixture(fixture: IntelliJFixture): IntelliJFixture = {
    fixture
      .withPlugin(scalaProbePlugin)
      .withAfterIntelliJInstall { (_, inteliJ) =>
        inteliJ.paths.plugins.resolve("ideprobe/lib/scala-library.jar").delete()
      }
  }

  implicit def pantsProbeDriver(driver: ProbeDriver): SbtProbeDriver = SbtProbeDriver(driver)
}

class ModuleTest extends SbtTestSuite {
  /**
   * The presence of .idea can prevent automatic import of gradle project
   */
  private def deleteIdeaSettings(intelliJ: RunningIntelliJFixture) = {
    val path = intelliJ.workspace.resolve(".idea")
    if (Files.exists(path)) path.delete()
  }

  @ParameterizedTest
  @ValueSource(
//    strings = Array("projects/shapeless/ideprobe.conf", "projects/cats/ideprobe.conf", "projects/dokka/ideprobe.conf")
    strings = Array("projects/shapeless/ideprobe.conf")
  )
  def verifyModulesPresent(configName: String): Unit = fixtureFromConfig(configName).run { intelliJ =>
    deleteIdeaSettings(intelliJ)
    intelliJ.probe.openProject(intelliJ.workspace)
    intelliJ.probe.setSbtProjectSettings(
      SbtProjectSettingsChangeRequest(
        useSbtShellForImport = Setting.Changed(true),
        useSbtShellForBuild = Setting.Changed(true),
        allowSbtVersionOverride = Setting.Changed(false)
      )
    )
    val project = intelliJ.probe.projectModel()
    val projectModules = project.modules.map(_.name)
    val modulesFromConfig = intelliJ.config[Seq[String]]("modules.verify")

    val missingModules = modulesFromConfig.diff(projectModules)
    Assert.assertTrue(s"Modules $missingModules are missing", missingModules.isEmpty)
  }

//  @ParameterizedTest
//  @ValueSource(
//    strings = Array("projects/shapeless/ideprobe.conf", "projects/cats/ideprobe.conf", "projects/dokka/ideprobe.conf")
//  )
//  def runTestsInModules(configName: String): Unit = fixtureFromConfig(configName).run { intelliJ =>
//    deleteIdeaSettings(intelliJ)
//    intelliJ.probe.openProject(intelliJ.workspace)
//    val modulesFromConfig = intelliJ.config[Seq[String]]("modules.test")
//    val moduleRefs = modulesFromConfig.map(ModuleRef(_))
//    val runConfigs = moduleRefs.map(JUnitRunConfiguration.module)
//    val result = runConfigs.map(config => config.module -> intelliJ.probe.run(config)).toMap
//
//    Assert.assertTrue(s"Tests in modules ${result.values} failed", result.values.forall(_.isSuccess))
//  }
}
