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
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.CompatibilityCheckDetails
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.MultipleCandidatesDetails
import org.gradle.api.attributes.Usage
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.component.AdhocComponentWithVariants
import org.gradle.api.execution.TaskExecutionGraph
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.War
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.util.GradleVersion
import org.jenkinsci.gradle.plugins.jpi.internal.DependencyLookup
import org.jenkinsci.gradle.plugins.jpi.legacy.LegacyWorkaroundsPlugin
import org.jenkinsci.gradle.plugins.jpi.server.GenerateHplTask
import org.jenkinsci.gradle.plugins.jpi.server.InstallJenkinsServerPluginsTask
import org.jenkinsci.gradle.plugins.jpi.server.JenkinsServerTask
import org.jenkinsci.gradle.plugins.jpi.verification.CheckOverlappingSourcesTask

import static org.gradle.api.logging.LogLevel.INFO
import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import static org.gradle.api.tasks.SourceSet.TEST_SOURCE_SET_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiManifest.attributesToMap

/**
 * Loads HPI related tasks into the current project.
 *
 * @author Hans Dockter
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
class JpiPlugin implements Plugin<Project> {

    /**
     * Represents the extra dependencies on other Jenkins plugins for the server task.
     */
    public static final String JENKINS_SERVER_DEPENDENCY_CONFIGURATION_NAME = 'jenkinsServer'

    public static final String JENKINS_RUNTIME_ELEMENTS_CONFIGURATION_NAME = 'runtimeElementsJenkins'
    public static final String JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME = 'runtimeClasspathJenkins'
    public static final String TEST_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME = 'testRuntimeClasspathJenkins'
    public static final String SERVER_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME = 'serverRuntimeClasspathJenkins'

    public static final String JPI = 'jpi'
    public static final String JPI_TASK_NAME = 'jpi'
    public static final String LICENSE_TASK_NAME = 'generateLicenseInfo'
    public static final String WEB_APP_DIR = 'src/main/webapp'

    DependencyAnalysis dependencyAnalysis

    void apply(final Project gradleProject) {
        if (GradleVersion.current() < GradleVersion.version('6.0')) {
            throw new GradleException('This version of the JPI plugin requires Gradle 6+.' +
                    'For older Gradle versions, please use version 0.38.0 of the JPI plugin.')
        }
        UnsupportedGradleConfigurationVerifier.configureDeprecatedConfigurations(gradleProject)

        dependencyAnalysis = new DependencyAnalysis(gradleProject)

        gradleProject.plugins.apply(JavaLibraryPlugin)
        gradleProject.plugins.apply(GroovyPlugin)
        gradleProject.plugins.apply(kotlinPlugin('org.jenkinsci.gradle.plugins.accmod.AccessModifierPlugin'))

        def ext = gradleProject.extensions.create('jenkinsPlugin', JpiExtension, gradleProject)
        gradleProject.plugins.apply(LegacyWorkaroundsPlugin)

        def overlap = gradleProject.tasks.register(CheckOverlappingSourcesTask.TASK_NAME,
                CheckOverlappingSourcesTask) { CheckOverlappingSourcesTask t ->
            t.group = LifecycleBasePlugin.VERIFICATION_GROUP
            t.description = 'Validate '

            def javaPluginConvention = project.convention.getPlugin(JavaPluginConvention)
            def classDirs = javaPluginConvention.sourceSets.getByName(MAIN_SOURCE_SET_NAME).output.classesDirs
            t.classesDirs.set(classDirs)
            t.dependsOn(gradleProject.tasks.getByName('classes'))
        }

        gradleProject.tasks.getByName('check').configure {
            it.dependsOn(overlap)
        }

        def generateHpl = gradleProject.tasks.register(GenerateHplTask.TASK_NAME,
                GenerateHplTask) { GenerateHplTask t ->
            t.fileName.set(ext.shortName + '.hpl')
            t.hplDir.set(project.layout.buildDirectory.dir('hpl'))
            t.description = 'Generate hpl (Hudson plugin link) for running locally'
            t.group = 'Jenkins Server'
        }

        def installPlugins = gradleProject.tasks.register(InstallJenkinsServerPluginsTask.TASK_NAME,
                InstallJenkinsServerPluginsTask) {
            it.group = 'Jenkins Server'
            it.description = 'Install plugins to the server\'s Jenkins Home directory'
            it.jenkinsHome.set(ext.workDir)
            def serverRuntime = project.configurations.getByName(SERVER_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            it.pluginsConfiguration.set(serverRuntime)
            it.hpl.set(generateHpl.flatMap { it.hpl })
            it.dependsOn(generateHpl)
        }

        def serverRuntime = gradleProject.configurations.create('jenkinsServerRuntimeOnly') { Configuration c ->
            c.withDependencies { DependencySet deps ->
                def warNotation = "org.jenkins-ci.main:jenkins-war:${ext.jenkinsVersion.get()}@war"
                deps.add(gradleProject.dependencies.create(warNotation))
            }
        }

        gradleProject.tasks.register(JenkinsServerTask.TASK_NAME, JenkinsServerTask) {
            it.description = 'Run Jenkins server locally with the plugin being developed'
            it.group = 'Jenkins Server'
            it.dependsOn(installPlugins)
            it.jenkinsServerRuntime.set(serverRuntime)
            it.jenkinsHome.set(ext.workDir)
            def sysPropPort = System.getProperty('jenkins.httpPort')
            if (sysPropPort) {
                it.port.convention(sysPropPort)
            }
            def propPort = project.findProperty('jenkins.httpPort') as String
            if (propPort) {
                it.port.convention(propPort)
            }
        }

        // set build directory for Jenkins test harness, JENKINS-26331
        gradleProject.tasks.withType(Test).named('test').configure {
            it.systemProperty('buildDirectory', gradleProject.buildDir.absolutePath)
        }

        configureLocalizer(gradleProject)
        configureInjectedTest(gradleProject)

        if (!gradleProject.logger.isEnabled(INFO)) {
            gradleProject.tasks.withType(JavaCompile).configureEach {
                options.compilerArgs << '-Asezpoz.quiet=true'
            }
            gradleProject.tasks.withType(GroovyCompile).configureEach {
                options.compilerArgs << '-Asezpoz.quiet=true'
            }
        }

        gradleProject.tasks.withType(GroovyCompile).configureEach {
            groovyOptions.javaAnnotationProcessing = true
        }

        def lookup = new DependencyLookup()
        for (String config : lookup.configurations()) {
            gradleProject.configurations.getByName(config) { Configuration c ->
                c.withDependencies { DependencySet deps ->
                    def toAdd = lookup.find(c.name, ext.validatedJenkinsVersion.get()).collect {
                        gradleProject.dependencies.create(it) { Dependency d ->
                            d.because('Added by org.jenkins-ci.jpi plugin')
                        }
                    }
                    deps.addAll(toAdd)
                }
            }
        }
        configureRepositories(gradleProject)
        configureJpi(gradleProject)
        configureConfigurations(gradleProject)
        configureManifest(gradleProject)
        configureLicenseInfo(gradleProject)
        configureTestDependencies(gradleProject)
        configurePublishing(gradleProject)
        configureTestHpl(gradleProject)
        gradleProject.afterEvaluate {
            gradleProject.setProperty('archivesBaseName', ext.shortName)
        }
    }

    @SuppressWarnings('ClassForName')
    private static <T extends Plugin<Project>> Class<T> kotlinPlugin(String className) {
        Class.forName(className) as Class<T>
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

    private static configureManifest(Project project) {
        TaskProvider<War> jpiProvider = project.tasks.named(JPI_TASK_NAME) as TaskProvider<War>
        TaskProvider<Jar> jarProvider = project.tasks.named(JavaPlugin.JAR_TASK_NAME) as TaskProvider<Jar>

        [jpiProvider, jarProvider].each { provider ->
            provider.configure { Jar task ->
                task.doFirst {
                    Map<String, ?> attributes = attributesToMap(project)
                    task.manifest.attributes(attributes)
                }
            }
        }
    }

    private configureJpi(Project project) {
        JpiExtension jpiExtension = project.extensions.getByType(JpiExtension)

        def jar = project.tasks.named(JavaPlugin.JAR_TASK_NAME)
        def jpi = project.tasks.register(JPI_TASK_NAME, War) {
            it.description = 'Generates the JPI package'
            it.group = BasePlugin.BUILD_GROUP
            def fileName = "${jpiExtension.shortName}.${jpiExtension.fileExtension}"
            def extension = jpiExtension.fileExtension
            it.archiveFileName.set(fileName)
            it.archiveExtension.set(extension)
            it.classpath(jar, dependencyAnalysis.allLibraryDependencies)
            it.from(WEB_APP_DIR)
        }
        project.tasks.named(LifecycleBasePlugin.ASSEMBLE_TASK_NAME) {
            it.dependsOn(jpi)
        }
    }

    private static configureTestDependencies(Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)

        def testDependenciesTask = project.tasks.register(TestDependenciesTask.TASK_NAME, TestDependenciesTask) {
            it.configuration = project.configurations[TEST_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME]
        }

        project.tasks.named(javaConvention.sourceSets.test.processResourcesTaskName).configure {
            it.dependsOn(testDependenciesTask)
        }
    }

    private static configureLocalizer(Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        JpiExtension jpiExtension = project.extensions.getByType(JpiExtension)

        def localizer = project.tasks.register(LocalizerTask.TASK_NAME, LocalizerTask) {
            it.description = 'Generates the Java source for the localizer.'
            it.group = BasePlugin.BUILD_GROUP
            it.sourceDirs = javaConvention.sourceSets.main.resources.srcDirs
            it.conventionMapping.map('destinationDir') {
                jpiExtension.localizerOutputDir
            }
        }
        javaConvention.sourceSets.main.java.srcDir { localizer.get().destinationDir }
        project.tasks.named(javaConvention.sourceSets.main.compileJavaTaskName).configure {
            it.dependsOn(localizer)
        }
    }

    private configureLicenseInfo(Project project) {
        def licenseTask = project.tasks.register(LICENSE_TASK_NAME, LicenseTask) {
            it.description = 'Generates license information.'
            it.group = BasePlugin.BUILD_GROUP
            it.outputDirectory = new File(project.buildDir, 'licenses')
            it.libraryConfiguration = dependencyAnalysis.allLibraryDependencies
        }

        project.tasks.named(JPI_TASK_NAME).configure {
            it.webInf.from(licenseTask.get().outputDirectory)
            it.dependsOn(licenseTask)
        }
    }

    private static configureInjectedTest(Project project) {
        JpiExtension jpiExtension = project.extensions.getByType(JpiExtension)
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        SourceSet testSourceSet = javaConvention.sourceSets.getByName(TEST_SOURCE_SET_NAME)

        File root = new File(project.buildDir, 'inject-tests')
        testSourceSet.java.srcDirs += root

        def testInsertionTask = project.tasks.register(TestInsertionTask.TASK_NAME, TestInsertionTask) {
            it.group = 'Verification'
            it.description = 'Generates a Jenkins Test'
            it.onlyIf { !jpiExtension.disabledTestInjection }
        }

        project.tasks.named('compileTestJava').configure { it.dependsOn(testInsertionTask) }

        project.afterEvaluate {
            testInsertionTask.configure {
                it.testSuite = new File(root, "${jpiExtension.injectedTestName}.java")
            }
        }
    }

    private static configureRepositories(Project project) {
        project.afterEvaluate {
            if (project.extensions.getByType(JpiExtension).configureRepositories) {
                project.repositories {
                    mavenCentral()
                    maven {
                        name 'jenkins'
                        url('https://repo.jenkins-ci.org/public/')
                    }
                }
            }
        }
    }

    private configureConfigurations(Project project) {
        def libraryElementsStrategy =
                project.dependencies.attributesSchema.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)
        libraryElementsStrategy.compatibilityRules.add(JPILibraryElementsCompatibilityRule)
        libraryElementsStrategy.disambiguationRules.add(JPILibraryDisambiguationRule)

        project.dependencies.components.all(JpiVariantRule)
        project.dependencies.components.withModule(JenkinsWarRule.JENKINS_WAR_COORDINATES, JenkinsWarRule)

        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        AdhocComponentWithVariants component = project.components.java

        Configuration jenkinsServer =
                project.configurations.create(JENKINS_SERVER_DEPENDENCY_CONFIGURATION_NAME)
        jenkinsServer.visible = false
        jenkinsServer.canBeConsumed = false
        jenkinsServer.canBeResolved = false

        setupTestRuntimeClasspath(project, TEST_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME,
                [JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME])
        setupTestRuntimeClasspath(project, SERVER_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME,
                [JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, JENKINS_SERVER_DEPENDENCY_CONFIGURATION_NAME])

        project.afterEvaluate {
            // to make sure all optional feature configurations have been setup completely
            project.configurations.all { Configuration runtimeElements ->
                if (isRuntimeVariant(runtimeElements)) {
                    Configuration runtimeElementsJenkins =
                            project.configurations.create(toFeatureSpecificConfigurationName(
                                    runtimeElements, JENKINS_RUNTIME_ELEMENTS_CONFIGURATION_NAME))
                    runtimeElementsJenkins.canBeResolved = false
                    runtimeElementsJenkins.canBeConsumed = true
                    runtimeElementsJenkins.outgoing.artifact(project.tasks.named(JPI_TASK_NAME))
                    runtimeElementsJenkins.attributes {
                        it.attribute(Usage.USAGE_ATTRIBUTE,
                                project.objects.named(Usage, Usage.JAVA_RUNTIME))
                        it.attribute(Category.CATEGORY_ATTRIBUTE,
                                project.objects.named(Category, Category.LIBRARY))
                        it.attribute(Bundling.BUNDLING_ATTRIBUTE,
                                project.objects.named(Bundling, Bundling.EXTERNAL))
                        it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                project.objects.named(LibraryElements, JPI))
                        it.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE,
                                javaConvention.targetCompatibility.majorVersion.toInteger())
                    }
                    runtimeElements.outgoing.capabilities.each {
                        runtimeElementsJenkins.outgoing.capability(it)
                    }

                    Configuration runtimeClasspathJenkins =
                            project.configurations.create(toFeatureSpecificConfigurationName(
                                    runtimeElements, JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME))
                    runtimeClasspathJenkins.canBeResolved = true
                    runtimeClasspathJenkins.canBeConsumed = false
                    runtimeClasspathJenkins.extendsFrom(runtimeElements)
                    runtimeClasspathJenkins.attributes {
                        it.attribute(Usage.USAGE_ATTRIBUTE,
                                project.objects.named(Usage, Usage.JAVA_RUNTIME))
                        it.attribute(Category.CATEGORY_ATTRIBUTE,
                                project.objects.named(Category, Category.LIBRARY))
                        it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                                project.objects.named(LibraryElements, JPI))
                    }

                    component.addVariantsFromConfiguration(runtimeElementsJenkins) {
                        if (runtimeElements.name != JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
                            it.mapToOptional()
                        }
                    }

                    dependencyAnalysis.registerJpiConfigurations(
                            runtimeElements,
                            runtimeElementsJenkins,
                            runtimeClasspathJenkins)
                }
            }
        }
    }

    private static void setupTestRuntimeClasspath(Project project, String name, List<String> extendsFrom) {
        Configuration testRuntimeClasspathJenkins =
                project.configurations.create(name)
        testRuntimeClasspathJenkins.visible = false
        testRuntimeClasspathJenkins.canBeConsumed = false
        testRuntimeClasspathJenkins.canBeResolved = true
        testRuntimeClasspathJenkins.attributes {
            it.attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage, Usage.JAVA_RUNTIME))
            it.attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category, Category.LIBRARY))
            it.attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE,
                    project.objects.named(LibraryElements, JPI))
        }
        extendsFrom.each {
            testRuntimeClasspathJenkins.extendsFrom(project.configurations[it])
        }
    }

    private static boolean isRuntimeVariant(Configuration variant) {
        if (variant.name.startsWith('testFixtures')) {
            return false
        }
        (variant.canBeConsumed
                && variant.attributes.getAttribute(Usage.USAGE_ATTRIBUTE)?.
                name == Usage.JAVA_RUNTIME
                && variant.attributes.getAttribute(Category.CATEGORY_ATTRIBUTE)?.
                name == Category.LIBRARY
                && variant.attributes.getAttribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE)?.
                name == LibraryElements.JAR)
    }

    private static String toFeatureSpecificConfigurationName(Configuration runtimeElements, String baseName) {
        if (runtimeElements.name == JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME) {
            // main variant
            return baseName
        }
        // feature variant name
        toFeatureVariantName(runtimeElements) + baseName.capitalize()
    }

    private static String toFeatureVariantName(Configuration runtimeElements) {
        runtimeElements.name[0..runtimeElements.name.length()
                - JavaPlugin.RUNTIME_ELEMENTS_CONFIGURATION_NAME.length() - 1]
    }

    private static configurePublishing(Project project) {
        JpiExtension jpiExtension = project.extensions.getByType(JpiExtension)

        // delay configuration until all settings are available (groupId, shortName, ...)
        project.afterEvaluate {
            if (jpiExtension.configurePublishing) {
                project.plugins.apply(MavenPublishPlugin)
                PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
                publishingExtension.publications {
                    mavenJpi(MavenPublication) {
                        artifactId jpiExtension.shortName
                        from(project.components.java)

                        new JpiPomCustomizer(project).customizePom(pom)
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

                JavaPluginExtension javaPluginExtension = project.extensions.getByType(JavaPluginExtension)
                javaPluginExtension.withSourcesJar()
                javaPluginExtension.withJavadocJar()

                def localizer = project.tasks.named('localizer')
                project.tasks.named('sourcesJar').configure {
                    dependsOn(localizer)
                }
            }
        }

        // load credentials only when publishing
        project.gradle.taskGraph.whenReady { TaskExecutionGraph taskGraph ->
            if (jpiExtension.configurePublishing && taskGraph.hasTask(project.tasks.publish)) {
                def credentials = loadDotJenkinsOrg()
                PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
                publishingExtension.repositories.getByName('jenkins').credentials {
                    username credentials.userName
                    password credentials.password
                }
            }
        }
    }

    private static configureTestHpl(Project project) {
        JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention)
        SourceSet testSourceSet = javaConvention.sourceSets.getByName(TEST_SOURCE_SET_NAME)

        // generate test hpl manifest for the current plugin, to be used during unit test
        def outputDir = project.layout.buildDirectory.dir('generated-resources/test')
        testSourceSet.output.dir(outputDir)

        def generateTestHplTask = project.tasks.register('generateTestHpl', GenerateHplTask) {
            it.fileName.set('the.hpl')
            it.hplDir.set(outputDir)
        }

        project.tasks.register('generate-test-hpl') {
            it.dependsOn(generateTestHplTask)
        }

        project.tasks.named(JavaPlugin.TEST_CLASSES_TASK_NAME).configure { it.dependsOn(generateTestHplTask) }
    }

    private static class JPILibraryElementsCompatibilityRule implements
            AttributeCompatibilityRule<LibraryElements> {
        @Override
        void execute(CompatibilityCheckDetails<LibraryElements> details) {
            if (details.consumerValue.name == JPI && details.producerValue.name == LibraryElements.JAR) {
                // accept JARs for libraries that do not have JPIs so that we do not fail.
                // Non-JPI files will be filtered out later if needed (e.g. by the TestDependenciesTask)
                details.compatible()
            }
        }
    }

    private static class JPILibraryDisambiguationRule implements
            AttributeDisambiguationRule<LibraryElements> {

        @Override
        void execute(MultipleCandidatesDetails<LibraryElements> details) {
            if (details.consumerValue.name == JPI) {
                details.candidateValues.each {
                    if (it.name == JPI) {
                        details.closestMatch(it)
                    }
                }
            }
        }
    }
}
