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
import org.gradle.api.model.ReplacedBy
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.util.ConfigureUtil
import org.gradle.util.GradleVersion

/**
 * This gets exposed to the project as 'jpi' to offer additional convenience methods.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
class JpiExtension {
    final Project project
    @Deprecated
    Map<String, String> jenkinsWarCoordinates
    final Property<String> jenkinsVersion
    final Provider<String> validatedJenkinsVersion
    final Property<String> id
    final Property<String> displayId

    JpiExtension(Project project) {
        this.project = project
        this.jenkinsVersion = project.objects.property(String)
        this.validatedJenkinsVersion = jenkinsVersion.map {
            def resolved = it ?: coreVersion
            if (GradleVersion.version(resolved) < GradleVersion.version('1.420')) {
                throw new IllegalArgumentException('The gradle-jpi-plugin requires Jenkins 1.420 or later')
            }
            resolved
        }
        this.id = project.objects.property(String).convention(trimOffPluginSuffix(project.name))
        this.displayId = project.objects.property(String).convention(id)
        this.compatibleSinceVersion = project.objects.property(String)
        this.maskClasses = project.objects.property(String)
        this.pluginFirstClassLoader = project.objects.property(Boolean).convention(false)
        this.sandboxStatus = project.objects.property(Boolean).convention(false)
    }

    @Deprecated
    @ReplacedBy('id')
    private String shortName

    /**
     * Short name of the plugin is the ID that uniquely identifies a plugin.
     * If unspecified, we use the project name except the trailing "-plugin"
     */
    @Deprecated
    @ReplacedBy('id')
    String getShortName() {
        id.get()
    }

    @Deprecated
    @ReplacedBy('id')
    void setShortName(String shortName) {
        id.set(shortName)
    }

    private static String trimOffPluginSuffix(String s) {
        s.endsWith('-plugin') ? s[0..-8] : s
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

    @Deprecated
    @ReplacedBy('displayId')
    private String displayName

    /**
     * One-line display name of this plugin. Should be human readable.
     * For example, "Git plugin", "Acme Executor plugin", etc.
     */
    @Deprecated
    @ReplacedBy('displayId')
    @SuppressWarnings('UnnecessaryGetter')
    String getDisplayName() {
        displayId.get()
    }

    @Deprecated
    @ReplacedBy('displayId')
    void setDisplayName(String s) {
        this.displayId.convention(s)
    }

    /**
     * URL that points to the home page of this plugin.
     */
    String url

    /**
     * TODO: document
     */
    final Property<String> compatibleSinceVersion

    /**
     * TODO: document
     */
    final Property<Boolean> sandboxStatus

    /**
     * TODO: document
     */
    final Property<String> maskClasses

    final Property<Boolean> pluginFirstClassLoader

    /**
     * Version of core that we depend on.
     */
    @Deprecated
    @ReplacedBy('jenkinsVersion')
    private String coreVersion

    @Deprecated
    @ReplacedBy('jenkinsVersion')
    String getCoreVersion() {
        coreVersion
    }

    @Deprecated
    @ReplacedBy('jenkinsVersion')
    void setCoreVersion(String v) {
        jenkinsVersion.convention(v)
        this.coreVersion = v
        if (this.coreVersion) {
            jenkinsWarCoordinates = [group: 'org.jenkins-ci.main', name: 'jenkins-war', version: v]
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
        workDir ?: new File(project.projectDir, 'work')
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
        if (System.properties.containsKey('jpi.repoUrl')) {
            return System.properties['jpi.repoUrl']
        }
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
        if (System.properties.containsKey('jpi.snapshotRepoUrl')) {
            return System.properties['jpi.snapshotRepoUrl']
        }
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
