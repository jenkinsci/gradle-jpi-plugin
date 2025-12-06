package org.jenkinsci.gradle.plugins.jpi.internal

import spock.lang.Specification

class LicenseDataExtractorTest extends Specification {
    private final extractor = new LicenseDataExtractor()

    def 'should extract from unqualified pom'() {
        given:
        def name = 'Artifact Name'
        def url = 'http://example.org'
        def description = 'Artifact Description.'
        def licenseName = 'BSD 2'
        def licenseUrl = 'http://example.org/license'
        def licenseName2 = 'Apache 2'
        def licenseUrl2 = 'http://example.org/license/2'
        // inspired by xmlunit:xmlunit:1.4
        def pom = """\
            <?xml version="1.0"?>
            <project>
              <name>$name</name>
              <url>$url</url>
              <description>$description</description>
              <licenses>
                <license>
                  <name>$licenseName</name>
                  <url>$licenseUrl</url>
                </license>
                <license>
                  <name>$licenseName2</name>
                  <url>$licenseUrl2</url>
                </license>
              </licenses>
            </project>
            """.stripIndent()
        def expected = new LicenseData(name, description, url,
                [new License(licenseName, licenseUrl), new License(licenseName2, licenseUrl2)] as Set
        )

        when:
        def actual = extractor.extractFrom(new StringReader(pom))

        then:
        actual == expected
    }

    def 'should extract from qualified pom'() {
        given:
        def name = 'Artifact Name'
        def url = 'http://example.org'
        def description = 'Artifact Description.'
        def licenseName = 'BSD 2'
        def licenseUrl = 'http://example.org/license'
        def licenseName2 = 'Apache 2'
        def licenseUrl2 = 'http://example.org/license/2'
        // inspired by POM published with Gradle
        def pom = """\
            <?xml version="1.0" encoding="UTF-8"?>
            <project xmlns="http://maven.apache.org/POM/4.0.0" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <description>$description</description>
              <name>$name</name>
              <url>$url</url>
              <licenses>
                <license>
                  <name>$licenseName2</name>
                  <url>$licenseUrl2</url>
                  <distribution>repo</distribution>
                </license>
                <license>
                  <name>$licenseName</name>
                  <url>$licenseUrl</url>
                  <distribution>repo</distribution>
                </license>
              </licenses>
            </project>
            """.stripIndent()
        def expected = new LicenseData(name, description, url,
                [new License(licenseName, licenseUrl), new License(licenseName2, licenseUrl2)] as Set
        )

        when:
        def actual = extractor.extractFrom(new StringReader(pom))

        then:
        actual == expected
    }
}
