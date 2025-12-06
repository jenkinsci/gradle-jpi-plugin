package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import spock.lang.Specification

class JpiDeveloperSpec extends Specification {
    Logger logger = Mock(Logger)

    def 'set and get properties'() {
        setup:
        JpiDeveloper developer = new JpiDeveloper(logger)

        when:
        developer.foo = 'bar'
        developer.name = 'me'

        then:
        developer.foo == 'bar'
        developer.name == 'me'
    }

    def 'supported methods'() {
        setup:
        JpiDeveloper developer = new JpiDeveloper(logger)

        when:
        developer.with {
            id('test')
            name('me')
            email('me@example.org')
            url('https://example.org/me')
            organization('Example Inc.')
            organizationUrl('https://example.org')
            timezone('UTC')
        }

        then:
        with(developer) {
            id == 'test'
            name == 'me'
            email == 'me@example.org'
            url == 'https://example.org/me'
            organization == 'Example Inc.'
            organizationUrl == 'https://example.org'
            timezone == 'UTC'
        }
    }

    def 'unsupported methods'() {
        setup:
        JpiDeveloper developer = new JpiDeveloper(logger)

        when:
        developer.foo('bar')

        then:
        developer.foo == null
        1 * logger.log(LogLevel.WARN, 'JPI POM developer field foo not implemented.')
    }

    def 'configure'() {
        setup:
        JpiDeveloper developer = new JpiDeveloper(logger)

        when:
        developer.configure {
            id = 'test'
            name('me')
        }

        then:
        developer.id == 'test'
        developer.name == 'me'
    }
}
