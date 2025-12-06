package org.jenkinsci.gradle.plugins.jpi.support

import spock.lang.Specification

import static org.jenkinsci.gradle.plugins.jpi.support.FourSpaceIndenter.create

class DependenciesBlockSpec extends Specification {
    def 'should emit with indent'(Indenter indenter, String expected) {
        given:
        def block = DependenciesBlock.newBuilder()
                .add('api', 'org.slf4j:slf4j-api:1.7.25')
                .build()

        when:
        def actual = block.emit(indenter)

        then:
        actual == expected

        where:
        indenter            | expected
        create()            | '''dependencies {\n    api 'org.slf4j:slf4j-api:1.7.25'\n}\n'''
        create().increase() | '''    dependencies {\n        api 'org.slf4j:slf4j-api:1.7.25'\n    }\n'''
    }

    def 'should reset'() {
        given:
        def starting = DependenciesBlock.newBuilder()
                .addImplementation('junit:junit:4.12')
                .build()
        def rebuilt = starting.toBuilder()
                .reset()
                .addImplementation('com.google.guava:guava:20.0')
                .build()

        when:
        def actual = rebuilt.emit(create())

        then:
        actual == '''dependencies {\n    implementation 'com.google.guava:guava:20.0'\n}\n'''
    }
}
