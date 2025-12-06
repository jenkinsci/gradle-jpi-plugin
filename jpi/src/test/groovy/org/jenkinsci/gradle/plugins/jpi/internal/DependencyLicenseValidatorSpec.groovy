package org.jenkinsci.gradle.plugins.jpi.internal

import spock.lang.Specification

import java.nio.file.Paths

class DependencyLicenseValidatorSpec extends Specification {
    def 'everything resolved'() {
        given:
        def path = Paths.get('.')
        def requested = ['a:b:1', 'y:z:2'] as Set
        def resolved = ['a:b:1', 'y:z:2'] as Set

        when:
        def result = DependencyLicenseValidator.validate(requested, resolved, path)

        then:
        !result.unresolved
        result.message == ''
    }

    def 'missing one dependency'() {
        given:
        def path = Paths.get('.')
        def requested = ['a:b:1', 'y:z:2'] as Set
        def resolved = ['a:b:1'] as Set

        when:
        def result = DependencyLicenseValidator.validate(requested, resolved, path)

        then:
        result.unresolved
        result.message == """\
            Could not resolve license(s) via POM for 1 dependency:
            \t- y:z:2
            The above will be missing from $path
            """.stripIndent().denormalize()
    }

    def 'missing multiple dependencies'() {
        given:
        def path = Paths.get('.')
        def requested = ['y:z:2', 'a:b:1'] as Set
        def resolved = [] as Set

        when:
        def result = DependencyLicenseValidator.validate(requested, resolved, path)

        then:
        result.unresolved
        result.message == """\
            Could not resolve license(s) via POM for 2 dependencies:
            \t- a:b:1
            \t- y:z:2
            The above will be missing from $path
            """.stripIndent().denormalize()
    }
}
