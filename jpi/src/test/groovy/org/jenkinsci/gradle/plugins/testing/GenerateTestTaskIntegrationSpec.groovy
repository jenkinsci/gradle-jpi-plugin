package org.jenkinsci.gradle.plugins.testing

import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.IgnoreIf
import spock.lang.PendingFeature
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path

class GenerateTestTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskPath = ':generateJenkinsTests'
    private File build

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            """.stripIndent()
    }

    def 'should be disabled by default'() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SKIPPED
    }

    @Unroll
    def 'should be enabled with #declaration'(String declaration) {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
                $declaration
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        where:
        declaration << [
                'disabledTestInjection = false',
                'generateTests = true',
                'generateTests.set(true)',
                'disabledTestInjection = true; generateTests.set(true); disabledTestInjection = true',
        ]
    }

    @IgnoreIf({ isWindows() })
    def 'should generate source with defaults'() {
        given:
        // ensure this is present so we can call realpath for Windows compatiblity
        inProjectDir('build/resources/main').mkdirs()
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()
        def expected = expectedGeneratedTest()
        def mainOutputResources = inProjectDir('build/resources/main').toPath()

        when:
        gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        Files.exists(expected.toRealPath())
        expected.text.contains('public class InjectedTest extends TestCase {')
        expected.text.contains('    public static Test suite() throws Exception {')
        expected.text.contains("""parameters.put("basedir", "${projectDir.toPath().toRealPath().toString()}");""")
        expected.text.contains("""parameters.put("artifactId", "$projectName");""")
        expected.text.contains("""parameters.put("outputDirectory", "${mainOutputResources.toRealPath().toString()}");""")
        expected.text.contains('parameters.put("requirePI", "true");')
        expected.text.contains('return PluginAutomaticTestBuilder.build(parameters);')
    }

    @Unroll
    def 'should populate requirePI from #declaration'(String declaration, boolean expectedPI) {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
                $declaration
            }
            """.stripIndent()
        def expected = expectedGeneratedTest()

        when:
        gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        Files.exists(expected.toRealPath())
        expected.text.contains("""parameters.put("requirePI", "$expectedPI");""")

        where:
        declaration                                                  | expectedPI
        'requirePI = true'                                           | true
        'requirePI = false'                                          | false
        'requireEscapeByDefaultInJelly = true'                       | true
        'requireEscapeByDefaultInJelly.set(true)'                    | true
        'requireEscapeByDefaultInJelly = false'                      | false
        'requireEscapeByDefaultInJelly.set(false)'                   | false
        'requirePI = true; requireEscapeByDefaultInJelly.set(false)' | false
    }

    @Unroll
    def 'should populate artifactId from #declaration'(String declaration, String expectedTestPackageName, String expectedArtifactId) {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
                $declaration
            }
            """.stripIndent()
        def expected = expectedGeneratedTest(expectedTestPackageName)

        when:
        gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        Files.exists(expected.toRealPath())
        expected.text.contains("""parameters.put("artifactId", "$expectedArtifactId");""")

        where:
        declaration                               | expectedTestPackageName | expectedArtifactId
        """pluginId = 'abc-def'"""                | 'abc_def'               | 'abc-def'
        """pluginId = 'abc'"""                    | 'abc'                   | 'abc'
        """pluginId.set('abc')"""                 | 'abc'                   | 'abc'
        """shortName = 'xyz'"""                   | 'xyz'                   | 'xyz'
        """pluginId = 'abc'; shortName = 'xyz'""" | 'abc'                   | 'abc'
    }

    @Unroll
    def 'should populate test name from #declaration'(String declaration, String expectedPath) {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
                $declaration
            }
            """.stripIndent()
        def expected = inProjectDir("build/inject-tests/${expectedPath.replace('%REPLACE%', projectName)}.java").toPath()

        when:
        gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        Files.exists(expected.toRealPath())

        where:
        declaration                                                                    | expectedPath
        """injectedTestName = 'GenTest'"""                                             | 'org/jenkinsci/plugins/generated/%REPLACE%/GenTest'
        """injectedTestName = 'One'; generatedTestClassName = 'Two'"""                 | 'org/jenkinsci/plugins/generated/%REPLACE%/Two'
        """injectedTestName = 'my.package.GenTest'"""                                  | 'my/package/GenTest'
        """generatedTestClassName = 'one.GenTest'"""                                   | 'one/GenTest'
        """generatedTestClassName.set('one.GenTest')"""                                | 'one/GenTest'
        """generatedTestClassName = 'one.GenTest'; injectedTestName = 'two.GenTest'""" | 'one/GenTest'
    }

    def 'should fail if given invalid test class name'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
                generatedTestClassName.set('A-Bad Value')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .buildAndFail()

        then:
        result.output.contains('not a valid name: A-Bad Value')
    }

    def 'should generate code compiled without warnings'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }

            compileGeneratedJenkinsTestJava {
                options.compilerArgs = ['-Xlint:all', '-Werror']
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('compileGeneratedJenkinsTestJava', '-s')
                .build()

        then:
        result.task(':compileGeneratedJenkinsTestJava').outcome == TaskOutcome.SUCCESS
    }

    @PendingFeature
    def 'should support configuration cache'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()

        expect:
        gradleRunner()
                .withArguments(taskPath, '--configuration-cache')
                .build()
    }

    private Path expectedGeneratedTest(String pluginId = projectName) {
        inProjectDir("build/inject-tests/org/jenkinsci/plugins/generated/$pluginId/InjectedTest.java").toPath()
    }
}
