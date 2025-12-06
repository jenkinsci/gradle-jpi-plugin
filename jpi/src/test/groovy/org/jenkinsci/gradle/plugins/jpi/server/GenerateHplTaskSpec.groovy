package org.jenkinsci.gradle.plugins.jpi.server

import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.Manifest

abstract class GenerateHplTaskSpec extends IntegrationSpec {
    protected final String projectName = TestDataGenerator.generateName()
    protected File settings
    protected File build
    protected Map<String, String> minimalAttributes
    private Path realProjectDir

    abstract String taskName()
    abstract String expectedRelativeHplLocation()

    def setup() {
        settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            '''.stripIndent()
        realProjectDir = projectDir.toPath().toRealPath()
        minimalAttributes = [
                'Long-Name'              : 'strawberry',
                'Minimum-Java-Version'   : '1.8',
                'Support-Dynamic-Loading': 'true',
                'Resource-Path'          : realProjectDir.resolve('src/main/webapp').toString(),
                'Libraries'              : '',
                'Plugin-Version'         : '6.0.13',
                'Jenkins-Version'        : '2.401.3',
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
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
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
                .withArguments(taskName())
                .build()

        then:
        def hplLocation = expectedRelativeHplLocation()
        def file = inProjectDir(hplLocation)
        existsRelativeToProjectDir(hplLocation)
        new Manifest(file.newInputStream()) == toManifest(minimalAttributes)

        where:
        inner                                        | outer                              | modifications
        ''                                           | ''                                 | [:]
        '''displayName = 'The Strawberry Plugin' ''' | ''                                 | ['Long-Name': 'The Strawberry Plugin']
        '''url = 'https://example.org/123' '''       | ''                                 | ['Url': 'https://example.org/123']
        '''compatibleSinceVersion = '2.64' '''       | ''                                 | ['Compatible-Since-Version': '2.64']
        '''sandboxStatus = true '''                  | ''                                 | ['Sandbox-Status': 'true']
        '''sandboxStatus = false '''                 | ''                                 | [:]
        '''maskClasses = 'com.google.guava.' '''     | ''                                 | ['Mask-Classes': 'com.google.guava.']
        '''maskClasses = 'a.b.c. a.b.c. y.z.' '''    | ''                                 | ['Mask-Classes': 'a.b.c. y.z.']
        '''maskClasses = null '''                    | ''                                 | [:]
        '''maskClasses = '' '''                      | ''                                 | [:]
        '''pluginFirstClassLoader = true '''         | ''                                 | ['PluginFirstClassLoader': 'true']
        '''pluginFirstClassLoader = false '''        | ''                                 | [:]
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
        Path srcMainJava = inProjectDir('src/main/java').toPath()
        TestSupport.CALCULATOR.writeTo(srcMainJava)
        Path srcMainResources = inProjectDir('src/main/resources').toPath()
        Files.createDirectories(srcMainResources)
        Files.createFile(srcMainResources.resolve('some.properties'))
        build << """
            jenkinsPlugin {
                shortName = 'strawberry'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                workDir = file('embedded-jenkins')
            }
            version = '6.0.13'

            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
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
        minimalAttributes['Libraries'] = [
                realProjectDir.resolve('src/main/resources').toString(),
                realProjectDir.resolve('build/classes/java/main').toString(),
                realProjectDir.resolve('build/resources/main').toString(),
                depFilesResult.output,
        ].join(',')

        when:
        gradleRunner()
                .withArguments(taskName())
                .build()

        then:
        def hplLocation = expectedRelativeHplLocation()
        def file = inProjectDir(hplLocation)
        existsRelativeToProjectDir(hplLocation)
        new Manifest(file.newInputStream()) == toManifest(minimalAttributes)
    }

    def 'should load classes in Libraries if present'() {
        given:
        Path srcMainJava = inProjectDir('src/main/java').toPath()
        TestSupport.CALCULATOR.writeTo(srcMainJava)
        build << """
            jenkinsPlugin {
                shortName = 'strawberry'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                workDir = file('embedded-jenkins')
            }
            version = '6.0.13'

            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
            }

            dependencies {
                implementation 'org.jenkins-ci.plugins:git:4.0.0'
            }
            """.stripIndent()
        minimalAttributes['Plugin-Dependencies'] = 'git:4.0.0'
        minimalAttributes['Libraries'] = realProjectDir.resolve('build/classes/java/main').toString()

        when:
        gradleRunner()
                .withArguments(taskName())
                .build()

        then:
        def hplLocation = expectedRelativeHplLocation()
        def file = inProjectDir(hplLocation)
        existsRelativeToProjectDir(hplLocation)
        new Manifest(file.newInputStream()) == toManifest(minimalAttributes)
    }

    def 'should load resources in Libraries if present'() {
        given:
        Path srcMainResources = inProjectDir('src/main/resources').toPath()
        Files.createDirectories(srcMainResources)
        Files.createFile(srcMainResources.resolve('some.properties'))
        build << """
            jenkinsPlugin {
                shortName = 'strawberry'
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
                workDir = file('embedded-jenkins')
            }
            version = '6.0.13'

            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
            }

            dependencies {
                implementation 'org.jenkins-ci.plugins:git:4.0.0'
            }
            """.stripIndent()
        minimalAttributes['Plugin-Dependencies'] = 'git:4.0.0'
        minimalAttributes['Libraries'] = [
                // TODO: unclear why these are both present. possibly so one can edit without rebuilding?
                realProjectDir.resolve('src/main/resources').toString(),
                realProjectDir.resolve('build/resources/main').toString(),
        ].join(',')

        when:
        gradleRunner()
                .withArguments(taskName())
                .build()

        then:
        def hplLocation = expectedRelativeHplLocation()
        def file = inProjectDir(hplLocation)
        existsRelativeToProjectDir(hplLocation)
        new Manifest(file.newInputStream()) == toManifest(minimalAttributes)
    }

    private static Manifest toManifest(Map<String, String> attributes) {
        def m = new Manifest()
        attributes.each { k, v -> m.mainAttributes.putValue(k, v) }
        m
    }
}
