package org.jenkinsci.gradle.plugins.jpi.support

import spock.lang.Specification
import spock.lang.Unroll

class StatementSpec extends Specification {
    @Unroll
    def 'should replace #token token'(String token, String format, Object arg, String expected) {
        expect:
        Statement.create(format, arg).toString() == expected

        where:
        token | format                     | arg                   | expected
        '$L'  | 'configurePublishing = $L' | true                  | 'configurePublishing = true'
        '$L'  | '$L = false'               | 'configurePublishing' | 'configurePublishing = false'
        '$L'  | 'someNumber($L)'           | 6                     | 'someNumber(6)'
        '$L'  | 'someNumber($L)'           | 'moon'                | 'someNumber(moon)'
        '$S'  | 'someMethod($S)'           | true                  | 'someMethod(\'true\')'
        '$S'  | 'someMethod($S)'           | 6                     | 'someMethod(\'6\')'
        '$S'  | 'someMethod($S)'           | 'moon'                | 'someMethod(\'moon\')'
    }

    def 'should replace multiple tokens'() {
        expect:
        def expected = "6 + 'true' + implementation('sun')"
        def statement = Statement.create('$L + $S + $L($S)', 6, true, 'implementation', 'sun')
        statement.toString() == expected
    }

    @Unroll
    def 'should emit with indent'(Indenter indenter, String expected) {
        expect:
        Statement.create('def a = 1').emit(indenter) == expected

        where:
        indenter                                         | expected
        FourSpaceIndenter.create()                       | 'def a = 1'
        FourSpaceIndenter.create().increase()            | '    def a = 1'
        FourSpaceIndenter.create().increase().increase() | '        def a = 1'
    }
}
