name := "ideprobe"

organization.in(ThisBuild) := "org.virtuslab.ideprobe"
scalaVersion.in(ThisBuild) := "2.13.1"
intellijBuild.in(ThisBuild) := "202.5792.28-EAP-SNAPSHOT"
licenses.in(ThisBuild) := List("Apache-2.0" -> url("http://www.apache.org/licenses/LICENSE-2.0"))
skip in publish.in(ThisBuild) := true

import IdeaPluginAdapter._

/**
 * By default, the sbt-idea-plugin gets applied to all of the projects.
 * We want it only in the plugin projects, so we need to disable it here
 * as well as for each created project separately.
 */
disableIdeaPluginDevelopment()

lazy val ci = project("ci", "ci", publish = false)
  .settings(
    CI.generateScripts := {
      CI.groupedProjects().value.toList.map {
        case (group, projects) => CI.generateTestScript(group, projects)
      }
    }
  )

/**
 * Not a [[module]] so it can be bundled with the idea plugin
 * (doesn't work when used disableIdeaPluginDevelopment on a project)
 */
lazy val api = project("api", "api", publish = true)
  .settings(
    libraryDependencies ++= Dependencies.pureConfig,
    libraryDependencies ++= Seq(
      Dependencies.gson,
      Dependencies.ammonite
    )
  )

lazy val driver = module("driver", "driver/sources")
  .dependsOn(api)
  .enablePlugins(BuildInfoPlugin)
  .usesIdeaPlugin(probePlugin)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.scalaParallelCollections,
      Dependencies.nuProcess
    ),
    buildInfoKeys := Seq[BuildInfoKey](version),
    buildInfoPackage := "org.virtuslab.ideprobe"
  )

lazy val driverTests = testModule("driver-tests", "driver/tests")
  .dependsOn(driver, junitDriver, api % "compile->compile;test->test")
  .usesIdeaPlugin(driverTestPlugin)

lazy val probePlugin = ideaPluginModule("probe-plugin", "probePlugin", publish = true)
  .dependsOn(api)
  .settings(
    intellijPluginName := "ideprobe"
  )

lazy val driverTestPlugin = ideaPluginModule("probe-test-plugin", "driver/test-plugin")
  .settings(
    intellijPluginName := "driver-test-plugin"
  )

lazy val junitDriver = module("junit-driver", "driver/bindings/junit")
  .dependsOn(driver, api % "compile->compile;test->test")
  .settings(
    libraryDependencies ++= Dependencies.junit
  )

lazy val scalaTests = testModule("scala-tests", "extensions/scala/tests")
  .dependsOn(junitDriver)

val commonSettings = Seq(
  libraryDependencies ++= Dependencies.junit,
  test in assembly := {},
  assemblyExcludedJars in assembly := {
    val cp = (fullClasspath in assembly).value
    cp.filter(file => file.data.toString.contains(".ideprobePluginIC"))
  }
)

def project(id: String, path: String, publish: Boolean): Project = {
  Project(id, sbt.file(path))
    .settings(
      skip in Keys.publish := !publish,
      libraryDependencies ++= Dependencies.junit,
      test in assembly := {},
      assemblyExcludedJars in assembly := {
        val cp = (fullClasspath in assembly).value
        cp.filter(file => file.data.toString.contains(".ideprobePluginIC"))
      }
    )
}

def module(id: String, path: String): Project = {
  project(id, path, publish = true).disableIdeaPluginDevelopment
    .settings(
      skip in Keys.publish := false
    )
}

def testModule(id: String, path: String): Project = {
  project(id, path, publish = false).disableIdeaPluginDevelopment
    .settings(
      Test / publishArtifact := true
    )
}

def ideaPluginModule(id: String, path: String, publish: Boolean = false): Project = {
  project(id, path, publish).enableIdeaPluginDevelopment
    .settings(
      packageMethod := PackagingMethod.Standalone(),
      intellijPlugins ++= Seq(
        "com.intellij.java".toPlugin,
        "JUnit".toPlugin
      )
    )
}
