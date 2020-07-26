package org.jenkinsci.gradle.plugins.jpi

import groovy.json.JsonSlurper
import org.junit.rules.TemporaryFolder
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input

class JpiPomCustomizerIntegrationSpec extends IntegrationSpec {
    private File settings
    private File build

    def setup() {
        settings = projectDir.newFile('settings.gradle')
        settings << 'rootProject.name = "test"'
        build = projectDir.newFile('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            group = 'org'
            version '1.0'
            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }
            """.stripIndent()
    }

    def 'minimal POM'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('minimal-pom.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'minimal POM with other publication logic setting the name'() {
        setup:
        build << """\
            apply plugin: 'maven-publish'
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            publishing.publications.withType(org.gradle.api.publish.maven.MavenPublication) {
                it.pom {
                    name = project.name
                }
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('minimal-pom.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'POM with other publication logic setting the description'() {
        setup:
        build << """\
            apply plugin: 'maven-publish'
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            description = 'this is my description'
            publishing.publications.withType(org.gradle.api.publish.maven.MavenPublication) {
                it.pom {
                    description = project.description
                }
            }
            """.stripIndent()

        when:
        generatePom()
        then:
        compareXml('minimal-pom-with-description.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'POM with all metadata'() {
        setup:
        build << """\
            description = 'lorem ipsum'
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
                url = 'https://lorem-ipsum.org'
                gitHubUrl = 'https://github.com/lorem/ipsum'
                developers {
                    developer {
                        id = 'abayer'
                        name = 'Andrew Bayer'
                        email = 'andrew.bayer@gmail.com'
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
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('complex-pom.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'gitHubUrl not pointing to GitHub'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
                gitHubUrl = 'https://bitbucket.org/lorem/ipsum'
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('bitbucket-pom.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'mavenLocal is ignored'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            repositories {
                mavenLocal()
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('minimal-pom.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'mavenCentral is ignored'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            repositories {
                mavenCentral()
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('minimal-pom.xml', actualPomIn(projectDir))
        compareJson('minimal-module.json', actualModuleIn(projectDir))
    }

    def 'plugin dependencies'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            dependencies {
                api 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('plugin-dependencies-pom.xml', actualPomIn(projectDir))
        compareJson('plugin-dependencies-module.json', actualModuleIn(projectDir))
    }

    def 'plugin with dynamic dependency - 1.9.+'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            dependencies {
                api 'org.jenkins-ci.plugins:credentials:1.9.+'
            }

            apply plugin: 'maven-publish'
            publishing {
                publications {
                    mavenJpi(MavenPublication) {
                        versionMapping {
                            usage('java-api') {
                                fromResolutionResult()
                            }
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('plugin-dependencies-pom.xml', actualPomIn(projectDir))
        compareJson('plugin-dependencies-module.json', actualModuleIn(projectDir))
    }

    def 'plugin with dynamic dependency - latest.release'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            dependencies {
                api 'org.jenkins-ci.plugins:credentials:latest.release'
            }
            apply plugin: 'maven-publish'
            publishing {
                publications {
                    mavenJpi(MavenPublication) {
                        versionMapping {
                            usage('java-api') {
                                fromResolutionResult()
                            }
                            usage('java-runtime') {
                                fromResolutionResult()
                            }
                        }
                    }
                }
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        !actualPomIn(projectDir).text.contains('<version>RELEASE</version>')
        !actualModuleIn(projectDir).text.contains('latest.release')
    }

    def 'optional plugin dependencies'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            java {
                registerFeature('credentials') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                credentialsApi 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('optional-plugin-dependencies-pom.xml', actualPomIn(projectDir))
        compareJson('optional-plugin-dependencies-module.json', actualModuleIn(projectDir))
    }

    def 'compile dependencies'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            dependencies {
                api 'org.apache.commons:commons-lang3:3.9'
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('compile-dependencies-pom.xml', actualPomIn(projectDir))
        compareJson('compile-dependencies-module.json', actualModuleIn(projectDir))
    }

    def 'compile dependencies with excludes'() {
        setup:
        build << """\
            jenkinsPlugin {
                jenkinsVersion = '1.580.1'
            }
            dependencies {
                api('org.bitbucket.b_c:jose4j:0.5.5') {
                    exclude group: 'org.slf4j', module: 'slf4j-api'
                }
            }
            """.stripIndent()

        when:
        generatePom()

        then:
        compareXml('compile-dependencies-with-excludes-pom.xml', actualPomIn(projectDir))
        compareJson('compile-dependencies-with-excludes-module.json', actualModuleIn(projectDir))
    }

    private static boolean compareXml(String fileName, File actual) {
        !DiffBuilder.compare(Input.fromString(readResource(fileName)))
                .withTest(Input.fromString(toXml(new XmlParser().parse(actual))))
                .checkForSimilar()
                .ignoreWhitespace()
                .build()
                .hasDifferences()
    }

    private static boolean compareJson(String fileName, File actual) {
        def actualJson = removeChangingDetails(new JsonSlurper().parseText(actual.text))
        def expectedJson = new JsonSlurper().parseText(readResource(fileName))
        assert actualJson == expectedJson
        actualJson == expectedJson
    }

    static removeChangingDetails(moduleRoot) {
        moduleRoot.createdBy.gradle.version = ''
        moduleRoot.createdBy.gradle.buildId = ''
        moduleRoot.variants.each { it.files.each { it.size = '' } }
        moduleRoot.variants.each { it.files.each { it.sha512 = '' } }
        moduleRoot.variants.each { it.files.each { it.sha256 = '' } }
        moduleRoot.variants.each { it.files.each { it.sha1 = '' } }
        moduleRoot.variants.each { it.files.each { it.md5 = '' } }
        moduleRoot
    }

    private static String readResource(String fileName) {
        JpiPomCustomizerIntegrationSpec.getResourceAsStream(fileName).text
    }

    private static String toXml(Node node) {
        Writer buffer = new StringWriter()
        new XmlNodePrinter(new PrintWriter(buffer)).print(node)
        buffer.toString()
    }

    void generatePom() {
        gradleRunner()
        .withArguments('generatePomFileForMavenJpiPublication', 'generateMetadataFileForMavenJpiPublication', '-s')
                .build()
    }

    static File actualPomIn(TemporaryFolder projectDir) {
        new File(projectDir.root, 'build/publications/mavenJpi/pom-default.xml')
    }

    static File actualModuleIn(TemporaryFolder projectDir) {
        new File(projectDir.root, 'build/publications/mavenJpi/module.json')
    }
}
