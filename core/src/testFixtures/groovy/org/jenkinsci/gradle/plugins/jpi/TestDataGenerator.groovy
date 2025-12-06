package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import org.apache.commons.text.RandomStringGenerator

@CompileStatic
class TestDataGenerator {
    static String generateName() {
        char[][] range = ['az'.toCharArray()]
        new RandomStringGenerator.Builder()
                .withinRange(range)
                .build()
                .generate(5, 20)
    }

    static String generateVersion() {
        char[][] range = ['09'.toCharArray()]
        def generator = new RandomStringGenerator.Builder()
                .withinRange(range)
                .build()
        "${generator.generate(1)}.${generator.generate(1, 5)}"
    }
}
