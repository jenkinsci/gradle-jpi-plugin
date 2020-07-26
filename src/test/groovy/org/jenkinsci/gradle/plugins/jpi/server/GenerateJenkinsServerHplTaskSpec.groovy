package org.jenkinsci.gradle.plugins.jpi.server

import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import spock.lang.Unroll

import java.util.jar.Manifest

import static org.jenkinsci.gradle.plugins.jpi.server.GenerateJenkinsServerHplTask.TASK_NAME

class GenerateJenkinsServerHplTaskSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build
    private Map<String, String> minimalAttributes

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = projectDir.newFile('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            '''.stripIndent()
        def realProjectDir = projectDir.root.toPath().toRealPath()
        minimalAttributes = [
                'Long-Name'              : 'strawberry',
                'Minimum-Java-Version'   : '1.8',
                'Support-Dynamic-Loading': 'true',
                'Resource-Path'          : realProjectDir.resolve('src/main/webapp').toString(),
                'Libraries'              : '',
                'Plugin-Version'         : '6.0.13',
                'Jenkins-Version'        : '2.222.3',
                'Extension-Name'         : 'strawberry',
                'Manifest-Version'       : '1.0',
                'Short-Name'             : 'strawberry',
        ]
    }

    @Unroll
    def 'should build up manifest with expected attributes'(String inner, String outer, Map<String, String> modifications) {
        given:
        build << """
            jenkinsPlugin {
                shortName = 'strawberry'
                jenkinsVersion = '2.222.3'
                $inner
            }
            $outer
            version = '6.0.13'

            java {
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            """.stripIndent()
        modifications.each { k, v ->
            minimalAttributes[k] = v
        }

        when:
        gradleRunner()
                .withArguments(TASK_NAME)
                .build()

        then:
        def file = new File(projectDir.root, 'build/hpl/strawberry.hpl')
        file.exists()
        new Manifest(file.newInputStream()) == toManifest(minimalAttributes)

        where:
        inner                                          | outer                              | modifications
        ''                                             | ''                                 | [:]
        '''displayName = 'The Strawberry Plugin' '''   | ''                                 | ['Long-Name': 'The Strawberry Plugin']
        '''url = 'https://example.org/123' '''         | ''                                 | ['Url': 'https://example.org/123']
        '''compatibleSinceVersion = '2.64' '''         | ''                                 | ['Compatible-Since-Version': '2.64']
        '''sandboxStatus = true '''                    | ''                                 | ['Sandbox-Status': 'true']
        '''sandboxStatus = false '''                   | ''                                 | [:]
        '''maskClasses = 'org.example,com.example' ''' | ''                                 | ['Mask-Classes': 'org.example,com.example']
        '''maskClasses = '' '''                        | ''                                 | [:]
        '''pluginFirstClassLoader = true '''           | ''                                 | ['PluginFirstClassLoader': 'true']
        '''pluginFirstClassLoader = false '''          | ''                                 | [:]
        '''\
            developers {
                developer {
                    name = 'Avatar'
                    id = 'a'
                    email = 'a@example.org'
                }
                developer {
                    name = 'Bear'
                    id = 'b'
                }
                developer {
                    name = 'Charlie'
                    email = 'charlie@example.org'
                }
                developer {
                    id = 'd'
                    email = 'd@example.org'
                }
            }'''.stripIndent()            | ''                                 | ['Plugin-Developers': 'Avatar:a:a@example.org,Bear:b:,Charlie::charlie@example.org,:d:d@example.org']
        '''developers {}'''                          | ''                                 | [:]
        ''                                           | '''group = 'org.example.fancy' ''' | ['Group-Id': 'org.example.fancy']
        // TODO dynamic load
        // TODO plugin class
    }

    def 'should load libraries and plugin-dependencies'() {
        given:
        build << """
            jenkinsPlugin {
                shortName = 'strawberry'
                jenkinsVersion = '2.222.3'
                workDir = file('embedded-jenkins')
            }
            version = '6.0.13'

            java {
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            configurations {
                wanted
            }

            dependencies {
                implementation 'com.google.guava:guava:19.0'
                implementation 'org.jetbrains:annotations:13.0'
                implementation 'org.jenkins-ci.plugins:git:4.0.0'

                wanted 'com.google.guava:guava:19.0'
                wanted 'org.jetbrains:annotations:13.0'
            }

            tasks.register('depFiles') {
                doLast {
                    print configurations.wanted.resolvedConfiguration.resolvedArtifacts
                        .collect { it.file }.join(',')
                }
            }
            """.stripIndent()
        def depFilesResult = gradleRunner().withArguments('depFiles', '-q').build()
        minimalAttributes['Plugin-Dependencies'] = 'git:4.0.0'
        minimalAttributes['Libraries'] = depFilesResult.output

        when:
        gradleRunner()
                .withArguments(TASK_NAME)
                .build()

        then:
        def file = new File(projectDir.root, 'build/hpl/strawberry.hpl')
        file.exists()
        new Manifest(file.newInputStream()) == toManifest(minimalAttributes)
    }

    private static Manifest toManifest(Map<String, String> attributes) {
        def m = new Manifest()
        attributes.each { k, v -> m.mainAttributes.putValue(k, v) }
        m
    }
}
