/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.GradleException
import org.gradle.util.ConfigureUtil
import hudson.util.VersionNumber

/**
 * This gets exposed to the project as 'jpi' to offer additional convenience methods.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
class JpiExtension {
    final Project project

    JpiExtension(Project project) {
        this.project = project
    }

    private String shortName

    /**
     * Short name of the plugin is the ID that uniquely identifies a plugin.
     * If unspecified, we use the project name except the trailing "-plugin"
     */
    String getShortName() {
        shortName ?: trimOffPluginSuffix(project.name)
    }

    private static String trimOffPluginSuffix(String s) {
        if (s.endsWith('-plugin')) {
            return s[0..-8]
        }
        s
    }

    private String fileExtension

    /**
     * File extension for plugin archives.
     */
    String getFileExtension() {
        fileExtension ?: 'hpi'
    }

    void setFileExtension(String s) {
        this.fileExtension = s
    }

    private String displayName

    /**
     * One-line display name of this plugin. Should be human readable.
     * For example, "Git plugin", "Acme Executor plugin", etc.
     */
    @SuppressWarnings('UnnecessaryGetter')
    String getDisplayName() {
        displayName ?: getShortName()
    }

    void setDisplayName(String s) {
        this.displayName = s
    }

    /**
     * URL that points to the home page of this plugin.
     */
    String url

    /**
     * TODO: document
     */
    String compatibleSinceVersion

    /**
     * TODO: document
     */
    boolean sandboxStatus

    /**
     * TODO: document
     */
    String maskClasses

    boolean pluginFirstClassLoader

    /**
     * Version of core that we depend on.
     */
    private String coreVersion

    String getCoreVersion() {
        coreVersion
    }

    void setCoreVersion(String v) {
        this.coreVersion = v
        def uiSamplesVersion = v

        if (new VersionNumber(this.coreVersion) <= new VersionNumber('1.419.99')) {
            throw new GradleException('The gradle-jpi-plugin requires Jenkins 1.420 or later')
        }

        if (new VersionNumber(this.coreVersion) >= new VersionNumber('1.533')) {
            uiSamplesVersion = '2.0'
        }

        if (this.coreVersion) {
            project.repositories {
                mavenCentral()
                mavenLocal()
                maven {
                    name 'jenkins'
                    delegate.url('http://repo.jenkins-ci.org/public/')
                }
            }

            project.dependencies {
                jenkinsCore(
                        [group: 'org.jenkins-ci.main', name: 'jenkins-core', version: v, ext: 'jar', transitive: true],
                        [group: 'javax.servlet', name: 'servlet-api', version: '2.4']
                )

                jenkinsWar(group: 'org.jenkins-ci.main', name: 'jenkins-war', version: v, ext: 'war')

                jenkinsTest("org.jenkins-ci.main:jenkins-test-harness:${v}@jar") { transitive = true }
                jenkinsTest("org.jenkins-ci.main:ui-samples-plugin:${uiSamplesVersion}@jar",
                        "org.jenkins-ci.main:maven-plugin:${v}@jar",
                        "org.jenkins-ci.main:jenkins-war:${v}:war-for-test@jar",
                        'junit:junit-dep:4.10@jar')
            }
        }
    }

    private String staplerStubDir

    /**
     * Sets the stapler stubs output directory
     */
    void setStaplerStubDir(String staplerStubDir) {
        this.staplerStubDir = staplerStubDir
    }

    /**
     * Returns the Stapler stubs directory.
     */
    File getStaplerStubDir() {
        def stubDir = staplerStubDir ?: 'generated-src/stubs'
        project.file("${project.buildDir}/${stubDir}")
    }

    private String localizerDestDir

    /**
     * Sets the localizer output directory
     */
    void setLocalizerDestDir(String localizerDestDir) {
        this.localizerDestDir = localizerDestDir
    }

    /**
     * Returns the localizer dest directory.
     */
    File getLocalizerDestDir() {
        def destDir = localizerDestDir ?: 'generated-src/localizer'
        project.file("${project.buildDir}/${destDir}")
    }

    private File workDir

    File getWorkDir() {
        workDir ?: new File(project.rootDir, 'work')
    }

    /**
     * Work directory to run Jenkins.war with.
     */
    void setWorkDir(File workDir) {
        this.workDir = workDir
    }

    private String repoUrl

    /**
     * The URL for the Maven repository to deploy the built plugin to.
     */
    String getRepoUrl() {
        repoUrl ?: 'http://maven.jenkins-ci.org:8081/content/repositories/releases'
    }

    void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl
    }

    private String snapshotRepoUrl

    /**
     * The URL for the Maven snapshot repository to deploy the built plugin to.
     */
    String getSnapshotRepoUrl() {
        repoUrl ?: 'http://maven.jenkins-ci.org:8081/content/repositories/snapshots'
    }

    void setSnapshotRepoUrl(String snapshotRepoUrl) {
        this.snapshotRepoUrl = snapshotRepoUrl
    }

    /**
     * The GitHub URL. Optional. Used to construct the SCM section of the POM.
     */
    String gitHubUrl

    String getGitHubSCMConnection() {
        if (gitHubUrl != null && gitHubUrl =~ /^https:\/\/github\.com/) {
            return gitHubUrl.replaceFirst(~/https:/, 'scm:git:git:') + '.git'
        }
        ''
    }

    String getGitHubSCMDevConnection() {
        if (gitHubUrl != null && gitHubUrl =~ /^https:\/\/github\.com/) {
            return gitHubUrl.replaceFirst(~/https:\/\//, 'scm:git:ssh://git@') + '.git'
        }
        ''
    }

    Developers developers = new Developers()

    def developers(Closure closure) {
        ConfigureUtil.configure(closure, developers)
    }

    /**
     * Runtime dependencies
     */
    FileCollection getRuntimeClasspath() {
        def providedRuntime = project.configurations.getByName(WarPlugin.PROVIDED_RUNTIME_CONFIGURATION_NAME)
        def groovyRuntime = project.configurations.getByName('compile')
        mainSourceTree().runtimeClasspath - providedRuntime - groovyRuntime
    }

    SourceSet mainSourceTree() {
        project.convention.getPlugin(JavaPluginConvention).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    }

    SourceSet testSourceTree() {
        project.convention.getPlugin(JavaPluginConvention).sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
    }

    class Developers {
        def developerMap = [:]

        def getProperty(String id) {
            developerMap[id]
        }

        void setProperty(String id, val) {
            developerMap[id] = val
        }

        def developer(Closure closure) {
            def d = new JpiDeveloper(JpiExtension.this.project.logger)
            ConfigureUtil.configure(closure, d)
            setProperty(d.id, d)
        }

        def each(Closure closure) {
            developerMap.values().each(closure)
        }

        def getProperties() {
            developerMap
        }
    }
}
