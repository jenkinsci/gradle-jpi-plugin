package org.jenkinsci.gradle.plugins.jpi.support

import spock.lang.Specification

class CodeBlockSpec extends Specification {
    def 'should replace statement by template'() {
        given:
        CodeBlock block = CodeBlock.newBuilder('jenkinsPlugins')
                .addStatement('workDir = file($S)', 'work')
                .build()

        when:
        CodeBlock actual = block.toBuilder()
            .setStatement('workDir = file($S)', 'work2')
            .build()

        then:
        actual.statements == [Statement.create('workDir = file($S)', 'work2')]
    }
}
