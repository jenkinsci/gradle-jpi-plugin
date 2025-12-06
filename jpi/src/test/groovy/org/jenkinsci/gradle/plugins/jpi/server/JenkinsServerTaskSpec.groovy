package org.jenkinsci.gradle.plugins.jpi.server

import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import spock.lang.IgnoreIf
import spock.lang.Unroll

@IgnoreIf({ isWindows() })
class JenkinsServerTaskSpec extends IntegrationSpec {

    @Unroll
    def 'server task is working - Jenkins #jenkinsVersion'() {
        given:
        touchInProjectDir('settings.gradle') << """\
            rootProject.name = "test-project"
        """
        def build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                $additionalPlugin
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                jenkinsVersion = '$jenkinsVersion'
            }
            dependencies {
                jenkinsServer 'org.jenkins-ci.plugins:git:5.4.0'
                implementation 'com.squareup.okio:okio:2.4.3'
            }
            """.stripIndent()

        when:
        Thread.start {
            int response = 0
            Thread.sleep(5000)
            while (response != 200) {
                Thread.sleep(1000)
                try {
                    def shutdown = new URL('http://localhost:8456/safeExit').openConnection()
                    shutdown.requestMethod = 'POST'
                    response = shutdown.responseCode
                } catch (ConnectException e) {
                    e.message
                }
            }
        }

        def result = gradleRunner()
                .withArguments('server', '--port=8456')
                .build()
        def output = result.output

        then:
        output.contains("/jenkins-war-${jenkinsVersion}.war")
        output.contains('webroot: ')
        output.contains('Jenkins is fully up and running')

        where:
        jenkinsVersion | additionalPlugin
        '2.462.3'      | ''
        '2.462.3'      | "id 'org.jetbrains.kotlin.jvm' version '1.9.25'"
    }
}
