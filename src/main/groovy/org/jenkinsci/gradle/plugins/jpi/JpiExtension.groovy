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

import hudson.util.VersionNumber
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.util.ConfigureUtil

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

    void setShortName(String shortName) {
        this.shortName = shortName
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
        def testHarnessVersion = v
        def servletApiArtifact = 'servlet-api'
        def servletApiVersion = '2.4'
        def findBugsGroup = 'findbugs'
        def findBugsVersion = '1.0.0'

        if (new VersionNumber(this.coreVersion) <= new VersionNumber('1.419.99')) {
            throw new GradleException('The gradle-jpi-plugin requires Jenkins 1.420 or later')
        }

        if (new VersionNumber(this.coreVersion) >= new VersionNumber('1.533')) {
            uiSamplesVersion = '2.0'
        }

        if (new VersionNumber(this.coreVersion) >= new VersionNumber('1.618')) {
            findBugsGroup = 'com.google.code.findbugs'
            findBugsVersion = '3.0.0'
        }

        if (new VersionNumber(this.coreVersion) > new VersionNumber('1.644')) {
            testHarnessVersion = '2.0'
        }

        if (new VersionNumber(this.coreVersion) >= new VersionNumber('2.0')) {
            servletApiArtifact = 'javax.servlet-api'
            servletApiVersion = '3.1.0'
        }

        // workarounds for JENKINS-26331
        if (new VersionNumber(this.coreVersion) >= new VersionNumber('1.545') &&
                new VersionNumber(this.coreVersion) < new VersionNumber('1.592')) {
            project.tasks.test.doFirst {
                project.file('target').mkdirs()
            }
        }
        if (new VersionNumber(this.coreVersion) < new VersionNumber('1.598')) {
            Delete clean = project.tasks.clean as Delete
            clean.delete('target')
        }

        if (this.coreVersion) {
            project.dependencies {
                jenkinsCore(
                        [group: 'org.jenkins-ci.main', name: 'jenkins-core', version: v],
                        [group: findBugsGroup, name: 'annotations', version: findBugsVersion],
                        [group: 'javax.servlet', name: servletApiArtifact, version: servletApiVersion],
                )

                jenkinsWar(group: 'org.jenkins-ci.main', name: 'jenkins-war', version: v, ext: 'war')

                jenkinsTest("org.jenkins-ci.main:jenkins-test-harness:${testHarnessVersion}")
                jenkinsTest("org.jenkins-ci.main:ui-samples-plugin:${uiSamplesVersion}",
                        "org.jenkins-ci.main:jenkins-war:${v}:war-for-test",
                        'junit:junit-dep:4.10')
            }
        }
    }

    private Object localizerOutputDir

    /**
     * Sets the localizer output directory
     */
    void setLocalizerOutputDir(Object localizerOutputDir) {
        this.localizerOutputDir = localizerOutputDir
    }

    /**
     * Returns the localizer output directory.
     */
    File getLocalizerOutputDir() {
        project.file(localizerOutputDir ?: "${project.buildDir}/generated-src/localizer")
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
        repoUrl ?: 'https://repo.jenkins-ci.org/releases'
    }

    void setRepoUrl(String repoUrl) {
        this.repoUrl = repoUrl
    }

    private String snapshotRepoUrl

    /**
     * The URL for the Maven snapshot repository to deploy the built plugin to.
     */
    String getSnapshotRepoUrl() {
        snapshotRepoUrl ?: 'https://repo.jenkins-ci.org/snapshots'
    }

    void setSnapshotRepoUrl(String snapshotRepoUrl) {
        this.snapshotRepoUrl = snapshotRepoUrl
    }

    /**
     * The GitHub URL. Optional. Used to construct the SCM section of the POM.
     */
    String gitHubUrl

    /**
     * The license for the plugin. Optional.
     */
    Licenses licenses = new Licenses()

    def licenses(Closure closure) {
        ConfigureUtil.configure(closure, licenses)
    }

    /**
     * If true, the automatic test injection will be skipped.
     *
     * Disabled by default because of <a href="https://issues.jenkins-ci.org/browse/JENKINS-21977">JENKINS-21977</a>.
     */
    boolean disabledTestInjection = true

    /**
     * Name of the injected test.
     */
    String injectedTestName = 'InjectedTest'

    /**
     * If true, verify that all the jelly scripts have the Jelly XSS PI in them.
     */
    boolean requirePI = true

    /**
     * Set to false to disable configuration of Maven Central, the local Maven cache and the Jenkins Maven repository.
     */
    boolean configureRepositories = true

    /**
     * If false, no publications or repositories for the Maven Publishing plugin will be configured.
     */
    boolean configurePublishing = true

    Developers developers = new Developers()

    def developers(Closure closure) {
        ConfigureUtil.configure(closure, developers)
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
            def developer = new JpiDeveloper(JpiExtension.this.project.logger)
            developer.configure(closure)
            setProperty(developer.id, developer)
        }

        def each(Closure closure) {
            developerMap.values().each(closure)
        }

        def collect(Closure closure) {
            developerMap.values().collect(closure)
        }

        def getProperties() {
            developerMap
        }

        boolean isEmpty() {
            developerMap.isEmpty()
        }
    }

    class Licenses {
        def licenseMap = [:]

        def getProperty(String name) {
            licenseMap[name]
        }

        void setProperty(String name, val) {
            licenseMap[name] = val
        }

        def license(Closure closure) {
            def license = new JpiLicense(JpiExtension.this.project.logger)
            license.configure(closure)
            setProperty(license.name, license)
        }

        def each(Closure closure) {
            licenseMap.values().each(closure)
        }

        def collect(Closure closure) {
            licenseMap.values().collect(closure)
        }

        def getProperties() {
            licenseMap
        }

        boolean isEmpty() {
            licenseMap.isEmpty()
        }
    }
}
