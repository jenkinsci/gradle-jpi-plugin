package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import spock.lang.Unroll

import java.util.jar.JarInputStream
import java.util.jar.Manifest

abstract class AbstractManifestIntegrationSpec extends IntegrationSpec {
    protected final String projectName = TestDataGenerator.generateName()
    protected final String projectVersion = TestDataGenerator.generateVersion()
    protected File settings
    protected File build

    abstract String taskToRun()

    abstract String generatedFileName(String base = "${projectName}-${projectVersion}")

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = projectDir.newFile('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            """.stripIndent()
    }

    def 'should have defaults'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        def expectedM = new Manifest()
        [
                'Long-Name'              : projectName,
                'Support-Dynamic-Loading': 'true',
                'Plugin-Version'         : projectVersion,
                'Extension-Name'         : projectName,
                'Manifest-Version'       : '1.0',
                'Short-Name'             : projectName,
                'Minimum-Java-Version'   : System.getProperty('java.specification.version'),
                'Jenkins-Version'        : TestSupport.RECENT_JENKINS_VERSION,
        ].each { k, v ->
            expectedM.mainAttributes.putValue(k, v)
        }
        when:
        def actual = generateManifestThroughGradle()
        def actualM = new Manifest()
        actual.each { k, v ->
            actualM.mainAttributes.putValue(k, v)
        }

        then:
        actualM == expectedM
    }

    def 'should populate Long-Name from Short-Name if unset'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                shortName = 'hello'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle(projectVersion, 'hello')

        then:
        actual['Long-Name'] == 'hello'
    }

    def 'should populate Long-Name'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                shortName = 'hello'
                displayName = 'The Hello Plugin'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle(projectVersion, 'hello')

        then:
        actual['Long-Name'] == 'The Hello Plugin'
    }

    def 'should populate Plugin-Version from project version if not defined'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        when:
        def actual = generateManifestThroughGradle(null)

        then:
        actual['Plugin-Version'] =~ /^1\.0-SNAPSHOT \(private-\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z-\S+\)$/
    }

    def 'should populate Plugin-Version from calculated project version if defined as -SNAPSHOT'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        def snapshotVersion = "${projectVersion}-SNAPSHOT"

        when:
        def actual = generateManifestThroughGradle(snapshotVersion)

        then:
        actual['Plugin-Version'] =~ /^$snapshotVersion \(private-\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z-\S+\)$/
    }

    def 'should populate group name if defined'() {
        given:
        String expected = 'org.example.myplugin'
        build << """\
            group = "$expected"
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Group-Id'] == expected
    }

    def 'should populate Jenkins-Version if defined'() {
        given:
        String expected = '2.150.2'
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '$expected'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Jenkins-Version'] == expected
    }

    def 'should populate Plugin-Class from legacy hudson.Plugin implementation'() {
        given:
        String pkg = 'my.example'
        String name = 'TestPlugin'
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        projectDir.newFolder('src', 'main', 'java', 'my', 'example')
        projectDir.newFile('src/main/java/my/example/TestPlugin.java') << """\
            package $pkg;

            class $name extends hudson.Plugin {
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Class'] == "$pkg.$name"
    }

    def 'should populate Compatible-Since-Version if defined'() {
        given:
        String expected = '1.409.1'
        build << """\
            jenkinsPlugin {
                compatibleSinceVersion = '$expected'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Compatible-Since-Version'] == expected
    }

    def 'should populate Minimum-Java-Version from targetCompatibility (case: #input)'(String input, String expected) {
        given:
        build << """\
            targetCompatibility = $input
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        expect:
        def actual = generateManifestThroughGradle()
        actual['Minimum-Java-Version'] == expected

        where:
        input                     | expected
        '\'1.8\''                 | '1.8'
        'JavaVersion.VERSION_1_8' | '1.8'
        '\'11\''                  | '11'
        'JavaVersion.VERSION_11'  | '11'
    }

    def 'should populate Mask-Classes if defined'() {
        given:
        String expected = 'org.example.test org.example2.test'
        build << """\
            jenkinsPlugin {
                maskClasses = '$expected'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Mask-Classes'] == expected
    }

    @Unroll
    def 'should only populate PluginFirstClassLoader if true (case: #value)'(boolean value, String expected) {
        given:
        build << """\
            jenkinsPlugin {
                pluginFirstClassLoader = $value
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        expect:
        def actual = generateManifestThroughGradle()
        actual['PluginFirstClassLoader'] == expected

        where:
        value | expected
        true  | 'true'
        false | null
    }

    @Unroll
    def 'should only populate Sandbox-Status if defined as true (case: #value)'(boolean value, String expected) {
        given:
        build << """\
            jenkinsPlugin {
                sandboxStatus = $value
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()

        expect:
        def actual = generateManifestThroughGradle()
        actual['Sandbox-Status'] == expected

        where:
        value | expected
        true  | 'true'
        false | null
    }

    def 'should populate Plugin-Dependencies with expected format for sole required dependency'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            dependencies {
                api 'org.jenkins-ci.plugins:ant:1.2'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Dependencies'] == 'ant:1.2'
    }

    def 'should populate Plugin-Dependencies with expected ordered format for multiple required dependencies'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            dependencies {
                api 'org.jenkinsci.plugins:git:1.1.15'
                implementation 'org.jenkins-ci.plugins:ant:1.2'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Dependencies'] == 'git:1.1.15,ant:1.2'
    }

    def 'should populate Plugin-Dependencies with expected format for sole optional dependency'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            java {
                registerFeature('ant') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                antApi 'org.jenkins-ci.plugins:ant:1.2'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Dependencies'] == 'ant:1.2;resolution:=optional'
    }

    def 'should populate Plugin-Dependencies with expected ordered format for multiple optional dependencies'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            java {
                registerFeature('git') {
                    usingSourceSet(sourceSets.main)
                }
                registerFeature('ant') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                gitApi 'org.jenkinsci.plugins:git:1.1.15'
                antImplementation 'org.jenkins-ci.plugins:ant:1.2'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Dependencies'] == 'ant:1.2;resolution:=optional,git:1.1.15;resolution:=optional'
    }

    def 'should populate Plugin-Dependencies with expected ordered format for multiple dependencies'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            java {
                registerFeature('folder') {
                    usingSourceSet(sourceSets.main)
                }
                registerFeature('credentials') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                folderApi 'org.jenkins-ci.plugins:cloudbees-folder:4.2'
                api 'org.jenkinsci.plugins:git:1.1.15'
                implementation 'org.jenkins-ci.plugins:ant:1.2'
                credentialsImplementation 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Dependencies'] ==
                'credentials:1.9.4;resolution:=optional,' +
                'cloudbees-folder:4.2;resolution:=optional,' +
                'git:1.1.15,' +
                'ant:1.2'
    }

    def 'can use bom to manage plugin dependencies'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }

            dependencies {
                api platform("io.jenkins.tools.bom:bom-2.138.x:4")
                implementation 'org.jenkins-ci.plugins.workflow:workflow-api'
            }
        """

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Dependencies'] == 'workflow-api:2.37'
    }

    def 'should populate Plugin-Developers with sole developer'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                developers {
                    developer {
                        id = 'abayer'
                        name = 'Andrew Bayer'
                        email = 'andrew.bayer@gmail.com'
                    }
                }
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Developers'] == 'Andrew Bayer:abayer:andrew.bayer@gmail.com'
    }

    def 'should populate Plugin-Developers with multiple developers in expected ordered format'() {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                developers {
                    developer {
                        id = 'abayer'
                        email = 'andrew.bayer@gmail.com'
                    }
                    developer {
                        id = 'kohsuke'
                        name = 'Kohsuke Kawaguchi'
                    }
                }
            }
            """.stripIndent()

        when:
        def actual = generateManifestThroughGradle()

        then:
        actual['Plugin-Developers'] == ':abayer:andrew.bayer@gmail.com,Kohsuke Kawaguchi:kohsuke:'
    }

    @Unroll
    def 'should populate Support-Dynamic-Loading conditionally (case: #value)'(String value, String manifestValue) {
        given:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        projectDir.newFolder('src', 'main', 'java', 'my', 'example')
        projectDir.newFile('src/main/java/my/example/TestPlugin.java') << """\
            package my.example;

            @hudson.Extension(dynamicLoadable = jenkins.YesNoMaybe.$value)
            public class TestPlugin {
            }
            """.stripIndent()

        expect:
        def actual = generateManifestThroughGradle()
        actual['Support-Dynamic-Loading'] == manifestValue

        where:
        value   | manifestValue
        'YES'   | 'true'
        'MAYBE' | null
        'NO'    | 'false'
    }

    def 'should not rerun task if manifest has not changed'() {
        given:
        String taskPath = ':' + taskToRun()
        build << """\
            jenkinsPlugin {
                shortName = 'unchanged'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        def firstRun = runTask()
        firstRun.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        def secondRun = runTask()

        then:
        secondRun.task(taskPath).outcome == TaskOutcome.UP_TO_DATE
    }

    def 'should rerun task if manifest has changed'() {
        given:
        String taskPath = ':' + taskToRun()
        build << """\
            jenkinsPlugin {
                shortName = 'before'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        def firstRun = runTask()
        firstRun.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        build.text.replace("shortName = 'before'", "shortName = 'after'")
        def secondRun = runTask()

        then:
        secondRun.task(taskPath).outcome == TaskOutcome.UP_TO_DATE
    }

    @CompileStatic
    BuildResult runTask(String overrideVersion = projectVersion) {
        List<String> args = ['-s', taskToRun()]
        if (overrideVersion) {
            args.add('-Pversion=' + overrideVersion)
        }
        gradleRunner()
                .withArguments(args)
                .build()
    }

    @CompileStatic
    Map<String, String> generateManifestThroughGradle(String overrideVersion = projectVersion,
                                                      String overrideFileName = null) {
        runTask(overrideVersion)
        String fileName = overrideFileName ? generatedFileName(overrideFileName) : generatedFileName()
        if (overrideVersion != projectVersion) {
            if (overrideVersion == null) {
                fileName = fileName.replace('-' + projectVersion, '')
            } else {
                fileName = fileName.replace(projectVersion, overrideVersion)
            }
        }
        def producedJar = "build/libs/${fileName}"
        new JarInputStream(new File(projectDir.root, producedJar).newInputStream())
                .manifest
                .mainAttributes
                .collectEntries { [(it.key.toString()): it.value.toString()] } as Map<String, String>
    }
}
