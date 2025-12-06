package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.TaskOutcome
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import spock.lang.PendingFeature
import spock.lang.Unroll

class LicenseTaskSpec extends IntegrationSpec {

    @Unroll
    def 'compute license information - #buildFile'(String buildFile, String expectedLicensesFile) {
        given:
        File projectFolder = mkDirInProjectDir('bar')
        new File(projectFolder, 'build.gradle') << getClass().getResource(buildFile).text
        def embeddedRepo = EmbeddedRepoBuilder.makeEmbeddedRepo()

        when:
        def result = gradleRunner()
                .withProjectDir(projectFolder)
                .withArguments('generateLicenseInfo', "-PembeddedIvyUrl=${embeddedRepo}")
                .build()

        then:
        result.task(':generateLicenseInfo').outcome == TaskOutcome.SUCCESS
        def licenses = 'build/licenses/licenses.xml'
        File licensesFile = new File(projectFolder, licenses)
        existsRelativeToProjectDir('bar/' + licenses)
        compareXml(licensesFile.text, getClass().getResource(expectedLicensesFile).text)

        where:
        buildFile                      | expectedLicensesFile
        'licenseInfo.gradle'           | 'licenses.xml'
        'licenseInfoWithKotlin.gradle' | 'licensesWithKotlin.xml'
    }

    @PendingFeature
    def 'support configuration cache'() {
        given:
        File projectFolder = mkDirInProjectDir('bar')
        new File(projectFolder, 'build.gradle') << getClass().getResource('licenseInfo.gradle').text

        when:
        def result = gradleRunner()
                .withProjectDir(projectFolder)
                .withArguments('generateLicenseInfo', "-PembeddedIvyUrl=${TestSupport.EMBEDDED_IVY_URL}", '--configuration-cache')
                .build()

        then:
        result.task(':generateLicenseInfo').outcome == TaskOutcome.SUCCESS
    }

    private static boolean compareXml(String actual, String expected) {
        !DiffBuilder.compare(Input.fromString(actual))
                .withTest(Input.fromString(expected))
                .checkForSimilar()
                .ignoreWhitespace()
                .build()
                .hasDifferences()
    }
}
