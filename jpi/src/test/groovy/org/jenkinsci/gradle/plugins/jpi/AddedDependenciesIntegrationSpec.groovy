package org.jenkinsci.gradle.plugins.jpi

import groovy.json.JsonSlurper
import org.gradle.testkit.runner.TaskOutcome

import java.nio.file.Files

class AddedDependenciesIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build

    def setup() {
        settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            '''.stripIndent()
    }

    def 'resolves test dependencies'() {
        given:
        build << """\
           jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
           }
           java {
                registerFeature('configFile') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                implementation 'org.jenkins-ci.plugins:structs:1.1'
                configFileApi 'org.jenkins-ci.plugins:config-file-provider:2.8.1'
                testImplementation 'org.jenkins-ci.plugins:cloudbees-folder:4.4'
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments('test', '--stacktrace')
                .build()

        then:
        result.task(':copyTestPluginDependencies').outcome == TaskOutcome.SUCCESS
        def dependenciesPath = 'build/jpi-plugin/test/test-dependencies'
        File dir = inProjectDir(dependenciesPath)
        def lines = Files.readAllLines(new File(dir, 'index').toPath())
        lines.toSet() == [
                'config-file-provider', 'structs', 'cloudbees-folder',
                'token-macro', 'credentials',
        ].toSet()
        existsRelativeToProjectDir("${dependenciesPath}/structs.jpi")
        existsRelativeToProjectDir("${dependenciesPath}/config-file-provider.jpi")
        existsRelativeToProjectDir("${dependenciesPath}/cloudbees-folder.jpi")
        existsRelativeToProjectDir("${dependenciesPath}/token-macro.jpi")
        existsRelativeToProjectDir("${dependenciesPath}/credentials.jpi")
    }

    def 'testCompileClasspath configuration contains plugin JAR dependencies'() {
        given:
        mkDirInProjectDir('build')
        build << '''\
            jenkinsPlugin {
                jenkinsVersion = '1.554.2'
            }
            tasks.register('writeAllResolvedDependencies') {
                def output = new File(project.buildDir, 'resolved-dependencies.json')
                outputs.file(output)
                doLast {
                    output.createNewFile()
                    def deprecatedConfigs = [
                        'archives',
                        'compile',
                        'compileOnly',
                        'default',
                        'generatedJenkinsTestCompile',
                        'generatedJenkinsTestCompileOnly',
                        'generatedJenkinsTestRuntime',
                        'runtime',
                        'testCompile',
                        'testCompileOnly',
                        'testRuntime',
                    ]
                    def artifactsByConfiguration = configurations.findAll { it.canBeResolved && !deprecatedConfigs.contains(it.name) }.collectEntries { c ->
                        def artifacts = c.incoming.artifactView { it.lenient(true) }.artifacts.collect {
                            it.id.componentIdentifier.toString() + '@' + it.file.name.substring(it.file.name.lastIndexOf('.') + 1)
                        }
                        [(c.name): artifacts]
                    }
                    output.text = groovy.json.JsonOutput.toJson(artifactsByConfiguration)
                }
            }
            '''.stripIndent()

        when:
        gradleRunner()
                .withArguments('writeAllResolvedDependencies', '-s')
                .build()
        def resolutionJson = inProjectDir('build/resolved-dependencies.json')
        def resolvedDependencies = new JsonSlurper().parse(resolutionJson)

        then:
        def testCompileClasspath = resolvedDependencies['testCompileClasspath']
        'org.jenkins-ci.plugins:ant:1.2@jar' in testCompileClasspath
        'org.jenkins-ci.main:maven-plugin:2.1@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:antisamy-markup-formatter:1.0@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:javadoc:1.0@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:mailer:1.8@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:matrix-auth:1.0.2@jar' in testCompileClasspath
        'org.jenkins-ci.plugins:subversion:1.45@jar' in testCompileClasspath
    }
}
