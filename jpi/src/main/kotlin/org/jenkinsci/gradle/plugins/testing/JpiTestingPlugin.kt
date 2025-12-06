package org.jenkinsci.gradle.plugins.testing

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jenkinsci.gradle.plugins.jpi.internal.JpiExtensionBridge
import org.jenkinsci.gradle.plugins.jpi.deployment.CreateVersionlessLookupTask
import java.io.File

open class JpiTestingPlugin : Plugin<Project> {
    companion object {
        fun Test.useJenkinsRule(dir: Provider<Directory>) {
            doFirst {
                systemProperty("java.awt.headless", "true")
                // set build directory for Jenkins test harness, JENKINS-26331
                // this is the directory the war will be exploded to
                systemProperty("buildDirectory", dir.get().asFile.absolutePath)
            }
        }
    }
    override fun apply(target: Project) {
        val declaredJenkinsWar = target.configurations.create("declaredJenkinsWar") {
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = true
        }
        val javapoet = target.dependencies.create("com.squareup:javapoet:1.13.0")
        val jenkinsTestHarness = target.dependencies.create("org.jenkins-ci.main:jenkins-test-harness:2112.ve584e0edc63b_")
        val jaxBPlugin = target.dependencies.create("io.jenkins.plugins:jaxb:2.3.9-1")
        val jenkinsTestGeneration = target.configurations.create("jenkinsTestGeneration") {
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, target.objects.named(Usage.JAVA_RUNTIME))
            }
            isVisible = false
            isCanBeConsumed = false
            isCanBeResolved = true
            withDependencies {
                add(javapoet)
                add(jenkinsTestHarness)
                add(jaxBPlugin)
            }
        }
        val generatedTestsDir = target.layout.buildDirectory.dir("inject-tests")
        val generateJenkinsTests = target.tasks.register<GenerateTestTask>("generateJenkinsTests") {
            group = "Verification"
            description = "Generates a test class that runs org.jvnet.hudson.test.PluginAutomaticTestBuilder"
            val ext = project.extensions.getByType<JpiExtensionBridge>()
            onlyIf { ext.generateTests.get() }
            generatorClasspath.setFrom(jenkinsTestGeneration)
            testName.set(ext.generatedTestClassName)
            pluginId.set(ext.pluginId)
            baseDir.set(project.projectDir)
            requireEscapeByDefaultInJelly.set(ext.requireEscapeByDefaultInJelly)
            mainResourcesOutputDir.set(project.extensions.getByType<SourceSetContainer>()["main"].output.resourcesDir)
            outputDir.set(generatedTestsDir)
        }
        val mainSourceSet = target.extensions.getByType<SourceSetContainer>()["main"]
        val generatedSourceSet = target.extensions.getByType<SourceSetContainer>().create("generatedJenkinsTest") {
            java {
                setSrcDirs(listOf(generatedTestsDir.get()))
            }
            resources {
                setSrcDirs(listOf<File>())
            }
            runtimeClasspath += mainSourceSet.output
            runtimeClasspath += mainSourceSet.runtimeClasspath
        }
        val generatedTestTaskDir = target.layout.buildDirectory.dir("jpi-plugin/generatedJenkinsTest")
        val generatedJenkinsPluginsDir = generatedTestTaskDir.map { it.dir("test-dependencies") }

        val versionlessLookupTask = target.tasks.register<CreateVersionlessLookupTask>("createVersionlessLookup") {
            group = "Build"
            description = "Creates plugin lookup without versions in filenames"
            val jpiAllPlugins = project.configurations.getByName("jpiAllPlugins")
            allResolvedPlugins.from(jpiAllPlugins)
            moduleVersionToModule.set(project.provider {
                jpiAllPlugins.resolvedConfiguration.resolvedArtifacts.associate {
                    it.file.name to "${it.name}.jpi"
                }
            })
            lookupDestination.set(project.layout.buildDirectory.file("jpi-plugin/versionless.tsv"))
        }
        val versionlessOutput = versionlessLookupTask.flatMap { it.lookupDestination }
        val copyPluginsForGeneratedJenkinsTest = target.tasks.register<CopyTestPluginDependenciesTask>("copyGeneratedJenkinsTestPluginDependencies") {
            group = "Verification"
            description = "Copies plugins on runtimeClasspath into directory for jenkins-test-harness to load in generatedJenkinsTest"
            versionlessLookupFile.set(versionlessOutput)
            files.from(project.configurations.findByName("runtimeClasspathJenkins"))
            outputDir.set(generatedJenkinsPluginsDir)
        }
        target.tasks.named(generatedSourceSet.compileJavaTaskName).configure {
            dependsOn(generateJenkinsTests)
        }
        val generatedJenkinsTest = target.tasks.register<Test>("generatedJenkinsTest") {
            useJenkinsRule(generatedTestTaskDir)
            inputs.files(copyPluginsForGeneratedJenkinsTest)
            group = "Verification"
            description = "Runs tests from org.jvnet.hudson.test.PluginAutomaticTestBuilder"
            testClassesDirs = generatedSourceSet.output.classesDirs
            classpath = project.files(generatedJenkinsPluginsDir.get().asFile.parentFile) + generatedSourceSet.runtimeClasspath
        }
        target.configurations.getByName("generatedJenkinsTestRuntimeOnly") {
            extendsFrom(declaredJenkinsWar)
        }
        target.tasks.named("check").configure {
            dependsOn(generatedJenkinsTest)
        }

        // configure test task
        val testTaskDir = target.layout.buildDirectory.dir("jpi-plugin/test")
        val testPluginsDir = testTaskDir.map { it.dir("test-dependencies") }
        val copyPluginsForTest = target.tasks.register<CopyTestPluginDependenciesTask>("copyTestPluginDependencies") {
            group = "Verification"
            description = "Copies plugins on testRuntimeClasspath into directory for jenkins-test-harness to load in test"
            files.from(project.configurations.findByName("testRuntimeClasspathJenkins"))
            versionlessLookupFile.set(versionlessOutput)
            outputDir.set(testPluginsDir)
        }
        target.tasks.named<Test>("test").configure {
            useJenkinsRule(testTaskDir)
            inputs.files(copyPluginsForTest)
            classpath += project.files(testPluginsDir.get().asFile.parentFile)
        }
        target.configurations.getByName("testRuntimeOnly") {
            extendsFrom(declaredJenkinsWar)
        }
    }
}
