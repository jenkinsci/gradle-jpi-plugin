package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.Project
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.DefaultVersionSelectorScheme
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionComparator
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionSelectorScheme
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.publication.maven.internal.MavenVersionRangeMapper
import org.gradle.api.publication.maven.internal.VersionRangeMapper
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.publish.maven.internal.publication.MavenPomInternal
import org.gradle.api.publish.maven.internal.tasks.MavenPomFileGenerator
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import spock.lang.Specification

class JpiPomCustomizerSpec extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    Project project = ProjectBuilder.builder().build()

    def 'minimal POM'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('minimal-pom.xml', pom)
    }

    def 'POM with all metadata'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            description = 'lorem ipsum'
            jenkinsPlugin {
                coreVersion = '1.580.1'
                url = 'https://lorem-ipsum.org'
                gitHubUrl = 'https://github.com/lorem/ipsum'
                developers {
                    developer {
                        id 'abayer'
                        name 'Andrew Bayer'
                        email 'andrew.bayer@gmail.com'
                    }
                }
                licenses {
                    license {
                        name 'Apache License, Version 2.0'
                        url 'https://www.apache.org/licenses/LICENSE-2.0.txt'
                        distribution 'repo'
                        comments 'A business-friendly OSS license'
                    }
                }
            }
            repositories {
                maven {
                    name 'lorem-ipsum'
                    url 'https://repo.lorem-ipsum.org/'
                }
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('complex-pom.xml', pom)
    }

    def 'gitHubUrl not pointing to GitHub'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
                gitHubUrl = 'https://bitbucket.org/lorem/ipsum'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('bitbucket-pom.xml', pom)
    }

    def 'mavenLocal is ignored'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
            repositories {
                mavenLocal()
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('minimal-pom.xml', pom)
    }

    def 'mavenCentral is ignored'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
            repositories {
                mavenCentral()
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('minimal-pom.xml', pom)
    }

    def 'plugin dependencies'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
            dependencies {
                jenkinsPlugins 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('plugin-dependencies-pom.xml', pom)
    }

    def 'optional plugin dependencies'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
            dependencies {
                optionalJenkinsPlugins 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('optional-plugin-dependencies-pom.xml', pom)
    }

    def 'compile dependencies'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
            dependencies {
                compile 'javax.ejb:ejb:2.1'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('compile-dependencies-pom.xml', pom)
    }

    def 'compile dependencies with excludes'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.580.1'
            }
            dependencies {
                compile('org.bitbucket.b_c:jose4j:0.5.5') {
                    exclude group: 'org.slf4j', module: 'slf4j-api'
                }
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('compile-dependencies-with-excludes-pom.xml', pom)
    }

    def 'updated FindBugs version in 1.618'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '1.618'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('updated-findbugs-pom.xml', pom)
    }

    def 'updated Servlet API version in 2.0'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                coreVersion = '2.0'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Node pom = generatePom()

        then:
        compareXml('updated-servlet-api-pom.xml', pom)
    }

    private Node generatePom() {
        PublishingExtension publishingExtension = project.extensions.getByType(PublishingExtension)
        MavenPublication publication = publishingExtension.publications.getByName('mavenJpi') as MavenPublication
        MavenPomInternal pomInternal = (MavenPomInternal) publication.pom

        VersionComparator versionComparator = new DefaultVersionComparator()
        VersionSelectorScheme versionSelectorScheme = new DefaultVersionSelectorScheme(versionComparator)
        VersionRangeMapper versionRangeMapper = new MavenVersionRangeMapper(versionSelectorScheme)
        MavenPomFileGenerator pomGenerator = new MavenPomFileGenerator(pomInternal.projectIdentity, versionRangeMapper)
        pomGenerator.packaging = pomInternal.packaging
        pomInternal.runtimeDependencies.each { pomGenerator.addRuntimeDependency(it) }
        pomGenerator.withXml(pomInternal.xmlAction)

        File pomFile = temporaryFolder.newFile()
        pomGenerator.writeTo(pomFile)
        new XmlParser().parse(pomFile)
    }

    private static boolean compareXml(String fileName, Node node) {
        !DiffBuilder.compare(Input.fromString(readXml(fileName)))
                .withTest(Input.fromString(toXml(node)))
                .checkForSimilar()
                .ignoreWhitespace()
                .build()
                .hasDifferences()
    }

    private static String readXml(String fileName) {
        JpiPomCustomizerSpec.getResourceAsStream(fileName).text
    }

    private static String toXml(Node node) {
        Writer buffer = new StringWriter()
        new XmlNodePrinter(new PrintWriter(buffer)).print(node)
        buffer.toString()
    }
}
