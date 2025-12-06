package org.jenkinsci.gradle.plugins.jpi.internal

import groovy.transform.CompileStatic
import org.gradle.api.Project
import spock.lang.Specification

import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.function.Supplier

@CompileStatic
class VersionCalculatorTest extends Specification {
    def 'should use given version if present'() {
        given:
        def calculator = new VersionCalculator()
        def expected = '1.4.3'

        when:
        def actual = calculator.calculate(expected)

        then:
        actual == expected
    }

    def 'should append timestamp if ends in -SNAPSHOT'() {
        given:
        def now = LocalDateTime.of(2018, 12, 31, 20, 59)
                .atOffset(ZoneOffset.UTC)
                .toInstant()
        def calculator = new VersionCalculator(Clock.fixed(now, ZoneId.of('Z')), new FixedUsername('dev'))
        def expected = '2.2.1-SNAPSHOT (private-2018-12-31T20:59:00Z-dev)'

        when:
        def actual = calculator.calculate('2.2.1-SNAPSHOT')

        then:
        actual == expected
    }

    def 'should calculate given version if not set'() {
        given:
        def now = LocalDateTime.of(1997, 1, 31, 11, 35)
                .atOffset(ZoneOffset.UTC)
                .toInstant()
        def calculator = new VersionCalculator(Clock.fixed(now, ZoneId.of('Z')), new FixedUsername('auser'))
        def expected = '1.0-SNAPSHOT (private-1997-01-31T11:35:00Z-auser)'

        when:
        def actual = calculator.calculate(Project.DEFAULT_VERSION)

        then:
        actual == expected
    }

    private static class FixedUsername implements Supplier<String> {
        private final String username

        FixedUsername(String username) {
            this.username = username
        }

        @Override
        String get() {
            username
        }
    }
}
