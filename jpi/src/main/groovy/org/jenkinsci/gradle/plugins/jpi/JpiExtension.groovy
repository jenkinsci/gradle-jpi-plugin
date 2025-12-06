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

import com.github.spotbugs.snom.SpotBugsPlugin
import com.github.spotbugs.snom.SpotBugsTask
import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.model.ReplacedBy
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.CheckstyleExtension
import org.gradle.api.plugins.quality.CheckstylePlugin
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPlugin
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.util.GradleVersion
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloperSpec
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicense
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicenseSpec
import org.jenkinsci.gradle.plugins.jpi.internal.BackwardsCompatiblePluginDevelopers
import org.jenkinsci.gradle.plugins.jpi.internal.BackwardsCompatiblePluginLicenses
import org.jenkinsci.gradle.plugins.jpi.internal.JpiExtensionBridge
import shaded.hudson.util.VersionNumber

/**
 * This gets exposed to the project as 'jpi' to offer additional convenience methods.
 *
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
@SuppressWarnings('MethodCount')
class JpiExtension implements JpiExtensionBridge {
    public static final String JENKINS_INCREMENTALS_REPO = 'https://repo.jenkins-ci.org/incrementals'
    final Project project
    @Deprecated
    Map<String, String> jenkinsWarCoordinates
    final Property<String> jenkinsVersion
    final Provider<String> validatedJenkinsVersion

    /**
     * If true, the automatic test injection will be skipped.
     *
     * Disabled by default because of <a href="https://issues.jenkins-ci.org/browse/JENKINS-21977">JENKINS-21977</a>.
     */
    final Property<Boolean> generateTests
    /**
     * Verify that all the jelly scripts have the Jelly XSS PI in them.
     * Has no effect unless {@code generateTests} is also {@code true}
     */
    final Property<Boolean> requireEscapeByDefaultInJelly
    /**
     * Name of test class to generate.
     * Has no effect unless {@code generateTests} is also {@code true}.
     */
    final Property<String> generatedTestClassName
    private final Property<String> pluginId
    private final Property<String> humanReadableName
    private final Property<URI> homePage
    private final Property<String> minimumJenkinsVersion
    private final Property<Boolean> sandboxed
    private final Property<Boolean> usePluginFirstClassLoader
    private final SetProperty<String> maskedClassesFromCore
    private final ListProperty<PluginDeveloper> pluginDevelopers
    private final ListProperty<PluginLicense> pluginLicenses
    private final ListProperty<String> testJvmArguments
    private final Property<String> incrementalsRepoUrl
    private final GitVersionExtension gitVersion
    private final Property<String> scmTag

    /**
     * File extension for plugin archives. Must be hpi or jpi. Default: hpi
     */
    final Property<String> extension
    final Property<URI> gitHub

    @SuppressWarnings('UnnecessarySetter')
    JpiExtension(Project project) {
        this.project = project
        this.jenkinsVersion = project.objects.property(String)
        this.validatedJenkinsVersion = jenkinsVersion.map {
            def resolved = it ?: coreVersion
            if (new VersionNumber(resolved) < new VersionNumber('1.420')) {
                throw new IllegalArgumentException('The gradle-jpi-plugin requires Jenkins 1.420 or later')
            }
            resolved
        }
        this.pluginId = project.objects.property(String).convention(trimOffPluginSuffix(project.name))
        this.humanReadableName = project.objects.property(String).convention(pluginId)
        this.homePage = project.objects.property(URI)
        this.minimumJenkinsVersion = project.objects.property(String)
        this.sandboxed = project.objects.property(Boolean).convention(false)
        this.usePluginFirstClassLoader = project.objects.property(Boolean).convention(false)
        this.maskedClassesFromCore = project.objects.setProperty(String).convention([])
        this.pluginDevelopers = project.objects.listProperty(PluginDeveloper)
        this.pluginLicenses = project.objects.listProperty(PluginLicense)
        this.testJvmArguments = project.objects.listProperty(String)
                .convention([
                        '--add-opens', 'java.base/java.lang=ALL-UNNAMED',
                        '--add-opens', 'java.base/java.io=ALL-UNNAMED',
                        '--add-opens', 'java.base/java.util=ALL-UNNAMED',
                ])
        this.generateTests = project.objects.property(Boolean).convention(false)
        this.requireEscapeByDefaultInJelly = project.objects.property(Boolean).convention(true)
        this.generatedTestClassName = project.objects.property(String).convention('InjectedTest')
        this.gitVersion = project.objects.newInstance(GitVersionExtension)
        this.incrementalsRepoUrl = project.objects.property(String).convention(JENKINS_INCREMENTALS_REPO)
        this.scmTag = project.objects.property(String)
                .convention(project.providers.gradleProperty('scmTag'))
        this.extension = project.objects.property(String).convention('hpi')
        this.gitHub = project.objects.property(URI)
    }

    /**
     * Short name of the plugin is the ID that uniquely identifies a plugin.
     * If unspecified, we use the project name except the trailing "-plugin"
     */
    String getShortName() {
        pluginId.get()
    }

    void setShortName(String shortName) {
        pluginId.convention(shortName)
    }

    private static String trimOffPluginSuffix(String s) {
        s.endsWith('-plugin') ? s[0..-8] : s
    }

    @Deprecated
    @ReplacedBy('extension')
    String getFileExtension() {
        extension.get()
    }

    @Deprecated
    @ReplacedBy('extension')
    void setFileExtension(String s) {
        if (s) {
            extension.convention(s)
        }
    }

    /**
     * One-line display name of this plugin. Should be human readable.
     * For example, "Git plugin", "Acme Executor plugin", etc.
     */
    @SuppressWarnings('UnnecessaryGetter')
    String getDisplayName() {
        humanReadableName.get()
    }

    void setDisplayName(String s) {
        humanReadableName.set(s)
    }

    /**
     * URL that points to the home page of this plugin.
     */
    @SuppressWarnings('UnnecessaryGetter')
    String getUrl() {
        homePage.getOrNull()?.toASCIIString()
    }

    void setUrl(String s) {
        homePage.set(project.uri(s))
    }

    /**
     * The earliest version of Jenkins this plugin will work on.
     *
     * This option should be used sparingly. Favor automatic data upgrades where possible.
     *
     * https://wiki.jenkins.io/display/JENKINS/Marking+a+new+plugin+version+as+incompatible+with+older+versions
     */
    @SuppressWarnings('UnnecessaryGetter')
    String getCompatibleSinceVersion() {
        minimumJenkinsVersion.orNull
    }

    void setCompatibleSinceVersion(String s) {
        minimumJenkinsVersion.set(s)
    }

    /**
     * Optional - sandbox status of this plugin
     */
    @SuppressWarnings('UnnecessaryGetter')
    boolean getSandboxStatus() {
        sandboxed.get()
    }

    void setSandboxStatus(boolean sandboxed) {
        this.sandboxed.set(sandboxed)
    }

    /**
     * list of package prefixes to hide from core
     */
    @SuppressWarnings('UnnecessaryGetter')
    String getMaskClasses() {
        maskedClassesFromCore.get().join(' ')
    }

    void setMaskClasses(String spaceSeparatedPackages) {
        if (spaceSeparatedPackages) {
            maskedClassesFromCore.addAll(spaceSeparatedPackages.split('\\s+'))
        }
    }

    /**
     * https://www.jenkins.io/doc/developer/plugin-development/dependencies-and-class-loading
     */
    @SuppressWarnings('UnnecessaryGetter')
    boolean getPluginFirstClassLoader() {
        usePluginFirstClassLoader.get()
    }

    void setPluginFirstClassLoader(boolean pluginFirst) {
        usePluginFirstClassLoader.set(pluginFirst)
    }

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

    @Deprecated
    private Object localizerOutputDir

    /**
     * Sets the localizer output directory
     *
     * Favor setting <code>outputDir</code> on the 'localizeMessages' task directly.
     * <pre>
     * tasks.named('localizeMessages', org.jenkinsci.gradle.plugins.jpi.localization.LocalizationTask).configure {
     *   outputDir.set(myPreferredDirectory)
     * }
     * </pre>
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    void setLocalizerOutputDir(Object localizerOutputDir) {
        this.localizerOutputDir = localizerOutputDir
    }

    /**
     * Returns the localizer output directory.
     *
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
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
    @Deprecated
    @ReplacedBy('gitHub')
    String getGitHubUrl() {
        gitHub.orNull
    }

    @Deprecated
    @ReplacedBy('gitHub')
    void setGitHubUrl(String s) {
        gitHub.convention(project.uri(s))
    }

    /**
     * Use #getPluginLicenses instead.
     *
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    @ReplacedBy('pluginLicenses')
    Licenses licenses = new Licenses()

    @CompileStatic
    def licenses(Action<? super PluginLicenseSpec> action) {
        def l = new BackwardsCompatiblePluginLicenses(project.objects)
        action.execute(l)
        pluginLicenses.set(l.licenses)
    }

    /**
     * Replaced by inverse flag property {@code generateTests}
     * @deprecated To be removed in 1.0.0
     * @return true if tests will not be generated
     */
    @Deprecated
    @ReplacedBy('generateTests')
    boolean getDisabledTestInjection() {
        !generateTests.get()
    }

    /**
     * Replaced by inverse flag property {@code generateTests}
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    void setDisabledTestInjection(boolean disable) {
        generateTests.convention(!disable)
    }

    /**
     * Replaced by property {@code generatedTestClassName}. Test name to
     * generate if {@code generateTests} is set to {@code true}.
     *
     * @deprecated To be removed in 1.0.0
     * @return name of test to be generated
     */
    @Deprecated
    @ReplacedBy('generatedTestClassName')
    String getInjectedTestName() {
        generatedTestClassName.get()
    }

    /**
     * Replaced by property {@code generatedTestClassName}. Test name to
     * generate if {@code generateTests} is set to {@code true}.
     *
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    void setInjectedTestName(String name) {
        generatedTestClassName.convention(name)
    }

    /**
     * Replaced by property {@code requireEscapeByDefaultInJelly}. Enables
     * additional tests if {@code generateTests} is set to {@code true}.
     *
     * @deprecated To be removed in 1.0.0
     * @return true if escape by default is required
     */
    @Deprecated
    @ReplacedBy('generatedTestClassName')
    boolean getRequirePI() {
        requireEscapeByDefaultInJelly.get()
    }

    /**
     * Replaced by property {@code requireEscapeByDefaultInJelly}. Enables
     * additional tests if {@code generateTests} is set to {@code true}.
     *
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    void setRequirePI(boolean require) {
        requireEscapeByDefaultInJelly.convention(require)
    }

    /**
     * Set to false to disable configuration of Maven Central, the local Maven cache and the Jenkins Maven repository.
     */
    boolean configureRepositories = true

    /**
     * If false, no publications or repositories for the Maven Publishing plugin will be configured.
     */
    boolean configurePublishing = true

    /**
     * Use #getPluginDevelopers instead.
     *
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    @ReplacedBy('pluginDevelopers')
    Developers developers = new Developers()

    @CompileStatic
    void developers(Action<? super PluginDeveloperSpec> action) {
        def devs = new BackwardsCompatiblePluginDevelopers(project.objects)
        action.execute(devs)
        pluginDevelopers.set(devs.developers)
    }

    /**
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    SourceSet mainSourceTree() {
        project.extensions.getByType(JavaPluginExtension).sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME)
    }

    /**
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    SourceSet testSourceTree() {
        project.extensions.getByType(JavaPluginExtension).sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME)
    }

    @Override
    Property<String> getPluginId() {
        pluginId
    }

    @Override
    Property<String> getHumanReadableName() {
        humanReadableName
    }

    @Override
    Property<URI> getHomePage() {
        homePage
    }

    @Override
    Provider<String> getJenkinsCoreVersion() {
        validatedJenkinsVersion
    }

    @Override
    Property<String> getMinimumJenkinsCoreVersion() {
        minimumJenkinsVersion
    }

    @Override
    Property<Boolean> getSandboxed() {
        sandboxed
    }

    @Override
    Property<Boolean> getUsePluginFirstClassLoader() {
        usePluginFirstClassLoader
    }

    @Override
    SetProperty<String> getMaskedClassesFromCore() {
        maskedClassesFromCore
    }

    @Override
    ListProperty<PluginDeveloper> getPluginDevelopers() {
        pluginDevelopers
    }

    @Override
    ListProperty<PluginLicense> getPluginLicenses() {
        pluginLicenses
    }

    @Override
    ListProperty<String> getTestJvmArguments() {
        testJvmArguments
    }

    GitVersionExtension getGitVersion() {
        gitVersion
    }

    def gitVersion(Action<GitVersionExtension> action) {
        action.execute(gitVersion)
    }

    Property<String> getIncrementalsRepoUrl() {
        incrementalsRepoUrl
    }

    @Override
    Property<String> getScmTag() {
        scmTag
    }

    void enableCheckstyle() {
        project.plugins.apply(CheckstylePlugin)
        def checkstyle = project.extensions.getByType(CheckstyleExtension)
        checkstyle.config = project.resources.text.fromUri(JpiPlugin.getResource('/sun_checks.xml').toURI())
        project.tasks.withType(Checkstyle).configureEach {
            it.enabled = true
            it.reports {
                xml.required = true
                html.required = false
            }
        }
    }

    void enableJacoco() {
        project.plugins.apply(JacocoPlugin)
        project.tasks.withType(JacocoReport).configureEach {
            it.reports {
                xml.required = true
                html.required = false
            }
        }
        project.tasks.withType(Test).configureEach {
            it.finalizedBy(project.tasks.named('jacocoTestReport'))
        }
    }

    void enableSpotBugs() {
        if (GradleVersion.current() < GradleVersion.version('7.0')) {
            return
        }
        project.plugins.apply(SpotBugsPlugin)
        project.tasks.withType(SpotBugsTask).configureEach {
            it.reports {
                xml.required = true
                html.required = false
            }
        }
    }

    /**
     * @see PluginDeveloper
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
    class Developers {
        def developerMap = [:]

        def getProperty(String id) {
            developerMap[id]
        }

        void setProperty(String id, val) {
            developerMap[id] = val
        }

        def developer(Closure closure) {
            def developer = new JpiDeveloper(project.logger)
            project.configure(developer, closure)
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

    /**
     * @see org.jenkinsci.gradle.plugins.jpi.core.PluginLicense
     * @deprecated To be removed in 1.0.0
     */
    @Deprecated
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
