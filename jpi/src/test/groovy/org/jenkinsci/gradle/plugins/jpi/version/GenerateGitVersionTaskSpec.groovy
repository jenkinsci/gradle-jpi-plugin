package org.jenkinsci.gradle.plugins.jpi.version

import org.apache.commons.io.FilenameUtils
import org.eclipse.jgit.api.Git
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport

import java.nio.file.Files

import static org.jenkinsci.gradle.plugins.jpi.version.Util.isGitHash

class GenerateGitVersionTaskSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private static final String GROUP_NAME = 'jpitest'
    private static final String MIN_BUILD_FILE = """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            group = '${GROUP_NAME}'
            """.stripIndent()
    private File build

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << "rootProject.name = \"${projectName}\""
        build = touchInProjectDir('build.gradle')
        def props = new Properties()
        props.setProperty('version', '1.0.0')
        inProjectDir('gradle.properties').withOutputStream {
            props.store(it, 'generated')
        }
    }

    def customBuildFile(String content = '') {
        """\
            $MIN_BUILD_FILE
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
                ${content}
            }
            """.stripIndent()
    }

    def 'should generate on git repository'() {
        given:
        build.text = customBuildFile()
        initGitRepo()

        when:
        gradleRunner()
            .withArguments('generateGitVersion', '--stacktrace')
            .build()

        then:
        inProjectDir('build/generated/version/version.txt').readLines()[0] ==~ /1\.\w{12}/
        isGitHash(inProjectDir('build/generated/version/version.txt').readLines()[1])
    }

    def 'should fail generate on non git repository'() {
        given:
        build.text = customBuildFile()

        when:
        def result = gradleRunner()
            .withArguments('generateGitVersion')
            .buildAndFail()

        then:
        result.output.contains('repository not found')
    }

    def 'should fail generate with uncommitted changes'() {
        given:
        initGitRepo()
        Files.createFile(projectDir.toPath().resolve('foo')).text = 'bar'
        build.text = customBuildFile()

        when:
        def result = gradleRunner()
            .withArguments('generateGitVersion')
            .buildAndFail()

        then:
        result.output.contains('has some pending changes')
    }

    def 'should generate with uncommitted changes but with allowDirty = true'() {
        given:
        initGitRepo()
        Files.createFile(projectDir.toPath().resolve('foo')).text = 'bar'
        build.text = customBuildFile('''
            gitVersion {
                allowDirty = true
            }
        ''')

        when:
        gradleRunner()
            .withArguments('generateGitVersion')
            .build()

        then:
        inProjectDir('build/generated/version/version.txt').readLines()[0] ==~ /1\.\w{12}/
        isGitHash(inProjectDir('build/generated/version/version.txt').readLines()[1])
    }

    def 'should generate with custom version format'() {
        given:
        build.text = customBuildFile("""
            gitVersion {
                versionFormat = 'rc-%d.%s'
                abbrevLength = 10
            }
        """)
        initGitRepo()

        when:
        gradleRunner()
            .withArguments('generateGitVersion')
            .build()

        then:
        inProjectDir('build/generated/version/version.txt').readLines()[0] ==~ /rc-1\.\w{10}/
        isGitHash(inProjectDir('build/generated/version/version.txt').readLines()[1])
    }

    def 'should generate with custom version format from gradle property'() {
        given:
        build.text = customBuildFile()
        initGitRepo()

        when:
        gradleRunner()
            .withArguments('generateGitVersion', '-PgitVersionFormat=rc-%d.%s')
            .build()

        then:
        inProjectDir('build/generated/version/version.txt').readLines()[0] ==~ /rc-1\.\w{12}/
        isGitHash(inProjectDir('build/generated/version/version.txt').readLines()[1])
    }

    def 'should generate with custom prefix format'() {
        given:
        build.text = customBuildFile('''
            gitVersion {
                versionPrefix = version
            }
        ''')
        initGitRepo()

        when:
        gradleRunner()
                .withArguments('generateGitVersion')
                .build()

        then:
        inProjectDir('build/generated/version/version.txt').readLines()[0] ==~ /1\.0\.01\.\w{12}/
        isGitHash(inProjectDir('build/generated/version/version.txt').readLines()[1])
    }

    def 'should generate with custom git root'() {
        def customGitRoot = Files.createTempDirectory('custom-git-root')
        given:
        build.text = customBuildFile("""
            gitVersion {
                gitRoot = file("${FilenameUtils.separatorsToUnix(customGitRoot.toAbsolutePath().toString())}")
            }
        """)
        initGitRepo(customGitRoot.toFile())

        when:
        gradleRunner()
            .withArguments('generateGitVersion')
            .build()

        then:
        inProjectDir('build/generated/version/version.txt').readLines()[0] ==~ /1\.\w{12}/
        isGitHash(inProjectDir('build/generated/version/version.txt').readLines()[1])
    }

    def 'should set custom version file'() {
        def customVersionFile = Files.createTempDirectory('custom-version-dir').resolve('version.txt')
        given:
        build.text = customBuildFile("""
            gitVersion {
                outputFile = file("${FilenameUtils.separatorsToUnix(customVersionFile.toAbsolutePath().toString())}")
            }
        """)
        initGitRepo()

        when:
        gradleRunner()
                .withArguments('clean', 'generateGitVersion')
                .build()

        then:
        customVersionFile.readLines()[0] ==~ /1\.\w{12}/
        isGitHash(customVersionFile.readLines()[1])
    }

    def 'should set custom version file from gradle property'() {
        def customVersionFile = Files.createTempDirectory('custom-version-dir').resolve('version.txt')
        given:
        build.text = customBuildFile()
        initGitRepo()

        when:
        gradleRunner()
                .withArguments('clean', 'generateGitVersion', "-PgitVersionFile=${customVersionFile}")
                .build()

        then:
        customVersionFile.readLines()[0] ==~ /1\.\w{12}/
        isGitHash(customVersionFile.readLines()[1])
    }

    @SuppressWarnings('GStringExpressionWithinString')
    def 'should generate incrementals publication according to jenkins infra expectations'() {
        def customVersionFile = Files.createTempDirectory('custom-version-dir').resolve('version.txt')
        def customM2 = Files.createTempDirectory('custom-m2')
        given:
        build.text = customBuildFile('''
            gitHubUrl = 'https://github.com/foo'
            gitVersion {
                versionPrefix = "${version}-"
            }
        ''')
        initGitRepo()

        when:
        gradleRunner()
                .withArguments('clean', 'generateGitVersion', "-PgitVersionFile=${customVersionFile}", '-PgitVersionFormat=rc%d.%s')
                .build()

        def version = customVersionFile.readLines()[0]
        def tag = customVersionFile.readLines()[1]
        // Note that overriding the version with `-Pversion` works only if the version is not set in the build.gradle
        gradleRunner()
                .withArguments('build', 'publishToMavenLocal', "-Pversion=${version}", "-Dmaven.repo.local=${customM2}", "-PscmTag=${tag}")
                .build()

        then:
        customVersionFile.readLines()[0] ==~ /1.0.0-rc1\.\w{12}/
        customVersionFile.readLines()[1] ==~ /\w{40}/
        def prefix = "${projectName}-${version}"
        customM2.resolve("${GROUP_NAME}/${projectName}/${version}").toFile().list() as Set == [
            "${prefix}-javadoc.jar",
            "${prefix}-sources.jar",
            "${prefix}.jar",
            "${prefix}.hpi",
            "${prefix}.module",
            "${prefix}.pom",
        ] as Set
        customM2.resolve("${GROUP_NAME}/${projectName}/${version}/${prefix}.pom").text =~ /<tag>\w{40}<\/tag>/
    }

    def 'should be able to pass sanitize flag'() {
        given:
        build.text = customBuildFile('''
            gitVersion {
                sanitize = true
            }
        ''')
        initGitRepo()

        when:
        gradleRunner()
                .withArguments('clean', 'generateGitVersion')
                .build()

        then:
        !(inProjectDir('build/generated/version/version.txt').text ==~ /([ab][^_])/)
    }

    def initGitRepo(File gitRoot = projectDir) {
        def gitCmd = Git.init()
        gitCmd.directory = gitRoot
        Git git = gitCmd.call()
        Files.createFile(projectDir.toPath().resolve('.gitignore')).text = '.gradle\nbuild'
        git.add().addFilepattern('.').call()
        def commit = git.commit()
        commit.setCommitter('Anne', 'Onyme')
        commit.sign = false
        commit.message = 'Initial commit'
        commit.call()
    }
}
