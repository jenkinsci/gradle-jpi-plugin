/*
 * Copyright 2009-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.XmlProvider
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.component.SoftwareComponent
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.bundling.Jar
import org.gradle.execution.TaskGraphExecuter

/**
 * Loads HPI related tasks into the current project.
 *
 * @author Hans Dockter
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
class JpiPlugin implements Plugin<Project> {
    /**
     * Represents the dependency to the Jenkins core.
     */
    public static final String CORE_DEPENDENCY_CONFIGURATION_NAME = 'jenkinsCore'

    /**
     * Represents the dependency to the Jenkins war. Test scope.
     */
    public static final String WAR_DEPENDENCY_CONFIGURATION_NAME = 'jenkinsWar'

    /**
     * Represents the dependencies on other Jenkins plugins.
     */
    public static final String PLUGINS_DEPENDENCY_CONFIGURATION_NAME = 'jenkinsPlugins'

    /**
     * Represents the dependencies on other Jenkins plugins.
     *
     * Using a separate configuration until we see GRADLE-1749.
     */
    public static final String OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME = 'optionalJenkinsPlugins'

    /**
     * Represents the Jenkins plugin test dependencies.
     */
    public static final String JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME = 'jenkinsTest'

    public static final String SOURCES_JAR_TASK_NAME = 'sourcesJar'
    public static final String JAVADOC_JAR_TASK_NAME = 'javadocJar'

    void apply(final Project gradleProject) {
        gradleProject.plugins.apply(JavaPlugin)
        gradleProject.plugins.apply(WarPlugin)
        gradleProject.plugins.apply(MavenPublishPlugin)
        gradleProject.plugins.apply(GroovyPlugin)

        // never run war as it's useless
        gradleProject.tasks.getByName('war').onlyIf { false }

        def ext = new JpiExtension(gradleProject)
        gradleProject.extensions.jenkinsPlugin = ext

        gradleProject.tasks.withType(Jpi) { Jpi task ->
            task.dependsOn {
                ext.mainSourceTree().runtimeClasspath
            }
            task.setClasspath(ext.runtimeClasspath)
            task.archiveName = "${ext.shortName}.${ext.fileExtension}"
        }
        gradleProject.tasks.withType(ServerTask) { ServerTask task ->
            task.dependsOn {
                ext.mainSourceTree().runtimeClasspath
            }
        }
        gradleProject.tasks.withType(StaplerGroovyStubsTask) { StaplerGroovyStubsTask task ->
            task.destinationDir = ext.staplerStubDir
        }
        gradleProject.tasks.withType(LocalizerTask) { LocalizerTask task ->
            task.sourceDirs = gradleProject.sourceSets.main.resources.srcDirs
            task.destinationDir = ext.localizerDestDir
        }

        def jpi = gradleProject.tasks.create(Jpi.TASK_NAME, Jpi)
        jpi.description = 'Generates the JPI package'
        jpi.group = BasePlugin.BUILD_GROUP

        def server = gradleProject.tasks.create(ServerTask.TASK_NAME, ServerTask)
        server.description = 'Run Jenkins in place with the plugin being developed'
        server.group = BasePlugin.BUILD_GROUP // TODO

        def stubs = gradleProject.tasks.create(StaplerGroovyStubsTask.TASK_NAME, StaplerGroovyStubsTask)
        stubs.description = 'Generates the Java stubs from Groovy source to enable Stapler annotation processing.'
        stubs.group = BasePlugin.BUILD_GROUP

        gradleProject.sourceSets.main.java.srcDirs += ext.staplerStubDir

        gradleProject.tasks.compileJava.dependsOn(StaplerGroovyStubsTask.TASK_NAME)

        // workaround for JENKINS-26331
        Delete clean = gradleProject.tasks.clean as Delete
        clean.delete('target')

        def localizer = gradleProject.tasks.create(LocalizerTask.TASK_NAME, LocalizerTask)
        localizer.description = 'Generates the Java source for the localizer.'
        localizer.group = BasePlugin.BUILD_GROUP

        gradleProject.sourceSets.main.java.srcDirs += ext.localizerDestDir

        gradleProject.tasks.compileJava.dependsOn(LocalizerTask.TASK_NAME)

        def sourcesJar = gradleProject.task(SOURCES_JAR_TASK_NAME, type: Jar, dependsOn: 'classes') {
            classifier = 'sources'
            from gradleProject.sourceSets.main.allSource
        }
        def javadocJar = gradleProject.task(JAVADOC_JAR_TASK_NAME, type: Jar, dependsOn: 'javadoc') {
            classifier = 'javadoc'
            from gradleProject.javadoc.destinationDir
        }
        gradleProject.artifacts {
            archives sourcesJar, javadocJar
        }

        configureConfigurations(gradleProject.configurations)
        configurePublishing(gradleProject)

        // generate test hpl manifest for the current plugin, to be used during unit test
        def generateTestHpl = gradleProject.tasks.create('generate-test-hpl') << {
            def hpl = new File(ext.testSourceTree().output.classesDir, 'the.hpl')
            hpl.parentFile.mkdirs()
            hpl.withOutputStream { new JpiHplManifest(gradleProject).write(it) }
        }
        gradleProject.tasks.getByName('test').dependsOn(generateTestHpl)
    }

    private static Properties loadDotJenkinsOrg() {
        Properties props = new Properties()
        def dot = new File(new File(System.getProperty('user.home')), '.jenkins-ci.org')
        if (!dot.exists()) {
            throw new GradleException(
                    "Trying to deploy to Jenkins community repository but there's no credential file ${dot}." +
                            ' See https://wiki.jenkins-ci.org/display/JENKINS/Dot+Jenkins+Ci+Dot+Org'
            )
        }
        dot.withInputStream { i -> props.load(i) }
        props
    }

    private static configureConfigurations(ConfigurationContainer cc) {
        Configuration jenkinsCoreConfiguration = cc.create(CORE_DEPENDENCY_CONFIGURATION_NAME).
                setVisible(false).
                setDescription('Jenkins core that your plugin is built against')
        Configuration jenkinsPluginsConfiguration = cc.create(PLUGINS_DEPENDENCY_CONFIGURATION_NAME).
                setVisible(false).
                setDescription('Jenkins plugins which your plugin is built against')
        Configuration optionalJenkinsPluginsConfiguration = cc.create(OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME).
                setVisible(false).
                setDescription('Optional Jenkins plugins dependencies which your plugin is built against')
        Configuration jenkinsTestConfiguration = cc.create(JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME).
                setVisible(false).
                setDescription('Jenkins plugin test dependencies.')
                .exclude(group: 'org.jenkins-ci.modules', module: 'ssh-cli-auth')
                .exclude(group: 'org.jenkins-ci.modules', module: 'sshd')
        cc.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME).extendsFrom(jenkinsCoreConfiguration)
        cc.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME).extendsFrom(jenkinsPluginsConfiguration)
        cc.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME).extendsFrom(optionalJenkinsPluginsConfiguration)
        cc.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME).extendsFrom(jenkinsTestConfiguration)

        cc.create(WAR_DEPENDENCY_CONFIGURATION_NAME).
                setVisible(false).
                setDescription('Jenkins war that corresponds to the Jenkins core')
    }

    protected static configurePublishing(Project project) {
        PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
        JpiExtension jpiExtension = project.extensions.getByType(JpiExtension)

        AbstractArchiveTask jpi = project.tasks.getByName(Jpi.TASK_NAME) as AbstractArchiveTask
        Task sourcesJar = project.tasks.getByName(SOURCES_JAR_TASK_NAME)
        Task javadocJar = project.tasks.getByName(JAVADOC_JAR_TASK_NAME)

        ArchivePublishArtifact jpiArtifact = new ArchivePublishArtifact(jpi)
        project.extensions.getByType(DefaultArtifactPublicationSet).addCandidate(jpiArtifact)

        SoftwareComponent jpiComponent = new JpiComponent(
                jpiArtifact,
                [
                        project.configurations[JavaPlugin.RUNTIME_CONFIGURATION_NAME].allDependencies,
                        project.configurations[CORE_DEPENDENCY_CONFIGURATION_NAME].allDependencies,
                        project.configurations[PLUGINS_DEPENDENCY_CONFIGURATION_NAME].allDependencies,
                        project.configurations[OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME].allDependencies,
                ]
        )
        project.components.add(jpiComponent)

        // delay configuration until all settings are available (groupId, shortName, ...)
        project.afterEvaluate {
            publishingExtension.publications {
                mavenJpi(MavenPublication) {
                    artifactId jpiExtension.shortName

                    from jpiComponent

                    artifact sourcesJar
                    artifact javadocJar

                    pom.withXml { XmlProvider xmlProvider ->
                        new JpiPomCustomizer(project).customizePom(xmlProvider.asNode())
                    }
                }
            }
            publishingExtension.repositories {
                maven {
                    name 'jenkins'
                    if (project.version.toString().endsWith('-SNAPSHOT')) {
                        url jpiExtension.snapshotRepoUrl
                    } else {
                        url jpiExtension.repoUrl
                    }
                }
            }
        }

        // load credentials only when publishing
        project.gradle.taskGraph.whenReady { TaskGraphExecuter taskGraph ->
            if (taskGraph.hasTask(project.tasks.publish)) {
                def credentials = loadDotJenkinsOrg()
                publishingExtension.repositories.getByName('jenkins').credentials {
                    username credentials.userName
                    password credentials.password
                }
            }
        }
    }
}
