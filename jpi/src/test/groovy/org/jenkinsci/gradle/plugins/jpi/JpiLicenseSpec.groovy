package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import spock.lang.Specification

class JpiLicenseSpec extends Specification {
    Logger logger = Mock(Logger)

    def 'set and get properties'() {
        setup:
        JpiLicense license = new JpiLicense(logger)

        when:
        license.foo = 'bar'
        license.name = 'me'

        then:
        license.foo == 'bar'
        license.name == 'me'
    }

    def 'supported methods'() {
        setup:
        JpiLicense license = new JpiLicense(logger)

        when:
        license.with {
            name('Apache License, Version 2.0')
            url('https://www.apache.org/licenses/LICENSE-2.0.txt')
            distribution('repo')
            comments('A business-friendly OSS license')
        }

        then:
        with(license) {
            name == 'Apache License, Version 2.0'
            url == 'https://www.apache.org/licenses/LICENSE-2.0.txt'
            distribution == 'repo'
            comments == 'A business-friendly OSS license'
        }
    }

    def 'unsupported methods'() {
        setup:
        JpiLicense license = new JpiLicense(logger)

        when:
        license.foo('bar')

        then:
        license.foo == null
        1 * logger.log(LogLevel.WARN, 'JPI POM license field foo not implemented.')
    }

    def 'configure'() {
        setup:
        JpiLicense license = new JpiLicense(logger)

        when:
        license.configure {
            name('Apache License, Version 2.0')
            url('https://www.apache.org/licenses/LICENSE-2.0.txt')
        }

        then:
        license.name == 'Apache License, Version 2.0'
        license.url == 'https://www.apache.org/licenses/LICENSE-2.0.txt'
    }
}
