package org.jenkinsci.gradle.plugins.accmod

import org.gradle.api.Project
import spock.lang.Specification

class PrefixedPropertiesProviderSpec extends Specification {
    def 'should return empty map when no matching properties'() {
        given:
        def project = Mock(Project)
        project.properties >> ['some.prop': 'one']
        def provider = new PrefixedPropertiesProvider(project, 'a.')
        def expected = [:]

        when:
        def actual = provider.call()

        then:
        actual == expected
    }

    def 'should return only matching properties without prefix'() {
        given:
        def project = Mock(Project)
        project.properties >> [
                'some.prop'      : 'one',
                'some.other.prop': 'two',
                'a.b'            : 'three',
        ]
        def provider = new PrefixedPropertiesProvider(project, 'some.')
        def expected = ['prop': 'one', 'other.prop': 'two']

        when:
        def actual = provider.call()

        then:
        actual == expected
    }
}
