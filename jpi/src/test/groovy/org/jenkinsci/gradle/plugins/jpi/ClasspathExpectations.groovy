package org.jenkinsci.gradle.plugins.jpi

import groovy.transform.CompileStatic
import groovy.transform.Immutable

@Immutable
@CompileStatic
class ClasspathExpectations {
    static final ClasspathExpectations EVERYWHERE_BUT_RUNTIME = new ClasspathExpectations(['annotationProcessor', 'compileClasspath', 'testCompileClasspath', 'testRuntimeClasspath'], ['runtimeClasspath'])
    static final ClasspathExpectations COMPILE_ONLY = new ClasspathExpectations(['compileClasspath'], ['runtimeClasspath'])
    static final ClasspathExpectations TEST_IMPLEMENTATION_ONLY = new ClasspathExpectations(['testCompileClasspath', 'testRuntimeClasspath'], ['compileClasspath', 'runtimeClasspath'])
    static final ClasspathExpectations NOWHERE = new ClasspathExpectations([], ['annotationProcessor', 'compileClasspath', 'runtimeClasspath', 'testCompileClasspath', 'testRuntimeClasspath'])

    List<String> on
    List<String> off
}
