package org.jenkinsci.gradle.plugins.jpi.support

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path

class NeptuneSpec extends Specification {
    @TempDir
    protected Path projectDir

    def 'should create build and settings'() {
        given:
        def neptune = Neptune.newBuilder()
                .withRootProject(ProjectFile.newBuilder()
                        .withName('avacado')
                        .build())
                .build()

        when:
        neptune.writeTo(projectDir)

        then:
        Files.exists(projectDir.resolve('build.gradle'))
        Files.exists(projectDir.resolve('settings.gradle'))
    }

    def 'should create subprojects'() {
        given:
        def neptune = Neptune.newBuilder()
                .withRootProject(ProjectFile.newBuilder().build())
                .addSubproject(ProjectFile.newBuilder().withName('a').build())
                .addSubproject(ProjectFile.newBuilder().withName('b').build())
                .build()

        when:
        neptune.writeTo(projectDir)

        then:
        Files.exists(projectDir.resolve('build.gradle'))
        Files.exists(projectDir.resolve('a/build.gradle'))
        Files.exists(projectDir.resolve('b/build.gradle'))
        Files.readAllLines(projectDir.resolve('settings.gradle')) == [
                "include 'a'",
                "include 'b'",
        ]
    }
}
