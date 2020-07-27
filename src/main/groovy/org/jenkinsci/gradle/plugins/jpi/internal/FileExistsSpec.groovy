package org.jenkinsci.gradle.plugins.jpi.internal

import groovy.transform.CompileStatic
import org.gradle.api.specs.Spec

@CompileStatic
class FileExistsSpec implements Spec<File> {
    @Override
    boolean isSatisfiedBy(File file) {
        file.exists()
    }
}
