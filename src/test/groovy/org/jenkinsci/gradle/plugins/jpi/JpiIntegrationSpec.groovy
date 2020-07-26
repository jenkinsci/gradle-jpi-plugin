package org.jenkinsci.gradle.plugins.jpi

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import java.nio.file.Files
import java.util.zip.ZipFile

import static org.jenkinsci.gradle.plugins.jpi.UnsupportedGradleConfigurationVerifier.JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.UnsupportedGradleConfigurationVerifier.OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.UnsupportedGradleConfigurationVerifier.PLUGINS_DEPENDENCY_CONFIGURATION_NAME

class JpiIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String projectVersion = TestDataGenerator.generateVersion()
    private File settings
    private File build

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = projectDir.newFile('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            '''.stripIndent()
    }

    def 'uses hpi file extension by default'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        gradleRunner()
                .withArguments('jpi')
                .build()

        then:
        new File(projectDir.root, "build/libs/${projectName}.hpi").exists()
    }

    @Unroll
    def 'uses #declaration'(String declaration, String expected) {
        given:
        build << """
            jenkinsPlugin {
                $declaration
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        gradleRunner()
                .withArguments('jpi')
                .build()

        then:
        new File(projectDir.root, "build/libs/${projectName}.${expected}").exists()

        where:
        declaration             | expected
        'fileExtension = null'  | 'hpi'
        'fileExtension = ""'    | 'hpi'
        'fileExtension = "hpi"' | 'hpi'
        'fileExtension "hpi"'   | 'hpi'
        'fileExtension = "jpi"' | 'jpi'
        'fileExtension "jpi"'   | 'jpi'
    }

    def 'uses project name as shortName by default'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        gradleRunner()
                .withArguments('jpi')
                .build()

        then:
        new File(projectDir.root, "build/libs/${projectName}.hpi").exists()
    }

    def 'uses project name with trimmed -plugin as shortName by default'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        def expected = 'test-333'
        settings.text = "rootProject.name = '$expected-plugin'"

        when:
        gradleRunner()
                .withArguments('jpi')
                .build()

        then:
        new File(projectDir.root, "build/libs/${expected}.hpi").exists()
    }

    @Unroll
    def 'uses #shortName'(String shortName, String expected) {
        given:
        build << """
            jenkinsPlugin {
                $shortName
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        gradleRunner()
                .withArguments('jpi')
                .build()

        then:
        new File(projectDir.root, "build/libs/${expected}.hpi").exists()

        where:
        shortName                     | expected
        "shortName = 'apple'"         | 'apple'
        "shortName 'banana'"          | 'banana'
        "shortName = 'carrot-plugin'" | 'carrot-plugin'
        "shortName 'date'"            | 'date'
    }

    def 'should bundle classes as JAR file into HPI file'() {
        given:
        def jarPathInHpi = "WEB-INF/lib/${projectName}-${projectVersion}.jar" as String

        build << """\
            repositories { mavenCentral() }
            dependencies {
                implementation 'junit:junit:4.12'
                api 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        TestSupport.CALCULATOR.writeTo(new File(projectDir.root, 'src/main/java'))

        when:
        def run = gradleRunner()
                .withArguments("-Pversion=${projectVersion}", 'jpi')
                .build()

        then:
        run.task(':jpi').outcome == TaskOutcome.SUCCESS

        def generatedHpi = new File(projectDir.root, "build/libs/${projectName}.hpi")
        def hpiFile = new ZipFile(generatedHpi)
        def hpiEntries = hpiFile.entries()*.name

        !hpiEntries.contains('WEB-INF/classes/')
        hpiEntries.contains(jarPathInHpi)
        hpiEntries.contains('WEB-INF/lib/junit-4.12.jar')
        !hpiEntries.contains('WEB-INF/lib/credentials-1.9.4.jar')

        def generatedJar = new File(projectDir.root, "${projectName}-${projectVersion}.jar")
        Files.copy(hpiFile.getInputStream(hpiFile.getEntry(jarPathInHpi)), generatedJar.toPath())
        def jarFile = new ZipFile(generatedJar)
        def jarEntries = jarFile.entries()*.name

        jarEntries.contains('org/example/Calculator.class')
    }

    @Unroll
    def '#task should run #dependency'(String task, String dependency, TaskOutcome outcome) {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(task)
                .build()

        then:
        result.task(dependency).outcome == outcome

        where:
        task                                         | dependency                                    | outcome
        'jar'                                        | ':generateManifest'                           | TaskOutcome.SUCCESS
        'jpi'                                        | ':generateManifest'                           | TaskOutcome.SUCCESS
        'processTestResources'                       | ':resolveTestDependencies'                    | TaskOutcome.SUCCESS
        'compileTestJava'                            | ':insertTest'                                 | TaskOutcome.SKIPPED
        'testClasses'                                | ':generate-test-hpl'                          | TaskOutcome.SUCCESS
        'compileJava'                                | ':localizer'                                  | TaskOutcome.SUCCESS
        'generateMetadataFileForMavenJpiPublication' | ':generateMetadataFileForMavenJpiPublication' | TaskOutcome.SUCCESS
    }

    @Unroll
    def '#task task should be setup'(String task) {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            tasks.register('describeTasks') {
                doLast {
                    def result = tasks.collectEntries {
                        [(it.name): [group: it.group, description: it.description]]
                    }
                    println groovy.json.JsonOutput.toJson(result)
                }
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
            .withArguments('describeTasks', '-q')
            .build()
        def actual = new JsonSlurper().parseText(result.output)

        then:
        actual[task]['group']
        actual[task]['description']

        where:
        task << ['jpi', 'server', 'localizer', 'insertTest']
    }

    @Unroll
    def 'compileTestJava should run :insertTest as #outcome (configured: #value)'(boolean value, TaskOutcome outcome) {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                disabledTestInjection = $value
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('compileTestJava')
                .build()

        then:
        result.task(':insertTest').outcome == outcome

        where:
        value | outcome
        true  | TaskOutcome.SKIPPED
        false | TaskOutcome.SUCCESS
    }

    def 'set buildDirectory system property in test'() {
        given:
        build << """\
            repositories { mavenCentral() }
            dependencies {
                testImplementation 'junit:junit:4.12'
            }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        def actualFile = projectDir.newFile()
        TestSupport.TEST_THAT_WRITES_SYSTEM_PROPERTIES_TO.apply(actualFile)
                .writeTo(new File(projectDir.root, 'src/test/java'))

        when:
        gradleRunner()
                .withArguments('test')
                .build()

        then:
        def actual = new Properties()
        actual.load(new FileReader(actualFile))
        def expected = new File(projectDir.root, 'build').toPath().toRealPath().toString()
        actual.get('buildDirectory') == expected
    }

    def 'sources and javadoc jars are created by default'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        gradleRunner()
                .withArguments('build')
                .build()

        then:
        new File(projectDir.root, "build/libs/${projectName}.hpi").exists()
        new File(projectDir.root, "build/libs/${projectName}.jar").exists()
        new File(projectDir.root, "build/libs/${projectName}-sources.jar").exists()
        new File(projectDir.root, "build/libs/${projectName}-javadoc.jar").exists()
    }

    def 'handles dependencies coming from ivy repository and do not fail with variants'() {
        given:
        build.text = """
            plugins {
                id 'org.jenkins-ci.jpi'
                id "maven-publish"
            }
            jenkinsPlugin {
                configurePublishing = false
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            repositories {
                ivy {
                    name 'EmbeddedIvy'
                    url '${TestSupport.EMBEDDED_IVY_URL}'
                    layout 'maven'
                }
            }

            dependencies {
                implementation 'org.example:myclient:1.0'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('build')
                .build()

        then:
        !result.output.contains('No such property: packaging for class: org.gradle.internal.component.external.model.ivy.DefaultIvyModuleResolveMetadata')
    }

    @Unroll
    def 'Should fail build with right context message when using #configuration configuration'() {
        given:
        build << """
            repositories { mavenCentral() }
            dependencies {
                $configuration 'junit:junit:4.12'
            }
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('dependencies')
                .buildAndFail()

        then:
        result.output.contains(expectedError)

        where:
        configuration                                  | expectedError
        PLUGINS_DEPENDENCY_CONFIGURATION_NAME          | "$PLUGINS_DEPENDENCY_CONFIGURATION_NAME is not supported anymore. Please use implementation configuration"
        OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME | "$OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME is not supported anymore. Please use Gradle feature variants"
        JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME     | "$JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME is not supported anymore. Please use testImplementation configuration"
    }

    @Unroll
    def 'should fail if < 1.420 (#version)'(String version) {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '$version'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('build')
                .buildAndFail()

        then:
        result.output.contains('> The gradle-jpi-plugin requires Jenkins 1.420 or later')

        where:
        version << ['1.419.99', '1.390']
    }

    def 'should use jenkinsVersion over coreVersion'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                coreVersion = '1.419' // would fail because < 1.420
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('dependencies', '--configuration', 'jenkinsServerRuntimeOnly', '--quiet')
                .build()

        then:
        result.output.contains("org.jenkins-ci.main:jenkins-war:${TestSupport.RECENT_JENKINS_VERSION}")
    }

    def 'should fallback to coreVersion when jenkinsVersion missing'() {
        given:
        build << """\
            jenkinsPlugin {
                coreVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('dependencies', '--configuration', 'jenkinsServerRuntimeOnly', '--quiet')
                .build()

        then:
        result.output.contains("org.jenkins-ci.main:jenkins-war:${TestSupport.RECENT_JENKINS_VERSION}")
    }

    @Unroll
    def 'setup publishing repo by extension (#url)'(String url, String expected) {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                repoUrl = $url
            }

            tasks.register('repos') {
                doLast {
                    publishing.repositories.each {
                        println it.url
                    }
                }
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('repos', '-q')
                .build()

        then:
        result.output.contains(expected)

        where:
        url                            | expected
        null                           | 'https://repo.jenkins-ci.org/releases'
        "''"                           | 'https://repo.jenkins-ci.org/releases'
        "'https://maven.example.org/'" | 'https://maven.example.org/'
    }

    def 'setup publishing repo by system property'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                repoUrl = 'https://maven.example.org/'
            }

            tasks.register('repos') {
                doLast {
                    publishing.repositories.each {
                        println it.url
                    }
                }
            }
            """.stripIndent()
        def expected = 'https://acme.org/'

        when:
        def result = gradleRunner()
                .withArguments('repos', '-q', "-Djpi.repoUrl=${expected}")
                .build()

        then:
        result.output.contains(expected)
    }

    @Unroll
    def 'setup publishing snapshot repo by extension (#url)'(String url, String expected) {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                snapshotRepoUrl = $url
            }
            version = '0.40.0-SNAPSHOT'

            tasks.register('repos') {
                doLast {
                    publishing.repositories.each {
                        println it.url
                    }
                }
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('repos', '-q')
                .build()

        then:
        result.output.contains(expected)

        where:
        url                            | expected
        null                           | 'https://repo.jenkins-ci.org/snapshots'
        "''"                           | 'https://repo.jenkins-ci.org/snapshots'
        "'https://maven.example.org/'" | 'https://maven.example.org/'
    }

    def 'setup publishing snapshot repo by system property'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                repoUrl = 'https://maven.example.org/'
            }
            version = '0.40.0-SNAPSHOT'

            tasks.register('repos') {
                doLast {
                    publishing.repositories.each {
                        println it.url
                    }
                }
            }
            """.stripIndent()
        def expected = 'https://acme.org/'

        when:
        def result = gradleRunner()
                .withArguments('repos', '-q', "-Djpi.snapshotRepoUrl=${expected}")
                .build()

        then:
        result.output.contains(expected)
    }
}
