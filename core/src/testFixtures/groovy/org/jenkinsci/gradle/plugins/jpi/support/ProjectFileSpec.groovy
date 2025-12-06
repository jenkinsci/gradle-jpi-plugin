package org.jenkinsci.gradle.plugins.jpi.support

import spock.lang.Specification

class ProjectFileSpec extends Specification {
    Indenter indenter = FourSpaceIndenter.create()

    def 'should write empty'() {
        expect:
        ProjectFile.newBuilder().build().emit(indenter) == ''
    }

    def 'should write file with plugin'() {
        expect:
        ProjectFile.newBuilder()
                .withPlugins(PluginsBlock.newBuilder()
                        .withPlugin('org.jenkins-ci.jpi')
                        .build())
                .build()
                .emit(indenter) == '''\
                    plugins {
                        id 'org.jenkins-ci.jpi'
                    }
                    '''.stripIndent()
    }

    def 'should handle plugins with version'() {
        expect:
        ProjectFile.newBuilder()
                .withPlugins(PluginsBlock.newBuilder()
                        .withPlugin('org.jenkins-ci.jpi')
                        .withPlugin('nebula.maven-publish', '17.0.5')
                        .build())
                .build()
                .emit(indenter) == '''\
                    plugins {
                        id 'org.jenkins-ci.jpi'
                        id 'nebula.maven-publish' version '17.0.5'
                    }
                    '''.stripIndent()
    }

    def 'should configure extension with replacements'() {
        expect:
        ProjectFile.newBuilder()
                .withBlock(CodeBlock.newBuilder('jenkinsPlugin')
                        .addStatement('configurePublishing = $L', true)
                        .addStatement('fileExtension = $S', 'jpi')
                        .build())
                .build()
                .emit(indenter) == '''\
                    jenkinsPlugin {
                        configurePublishing = true
                        fileExtension = 'jpi'
                    }
                    '''.stripIndent()
    }

    def 'should handle dependencies'() {
        expect:
        ProjectFile.newBuilder()
                .withDependencies(DependenciesBlock.newBuilder()
                        .addImplementation('com.google.guava:guava:19.0')
                        .add('testImplementation', 'junit:junit:4.12')
                        .build())
                .build()
                .emit(indenter) == '''\
                    dependencies {
                        implementation 'com.google.guava:guava:19.0'
                        testImplementation 'junit:junit:4.12'
                    }
                    '''.stripIndent()
    }

    def 'should clear dependencies'() {
        given:
        def builder = ProjectFile.newBuilder()
                .withDependencies(DependenciesBlock.newBuilder()
                        .addImplementation('com.google.guava:guava:19.0')
                        .add('testImplementation', 'junit:junit:4.12')
                        .build())
        def one = builder.build()
        def two = builder
                .clearDependencies()
                .withDependencies(DependenciesBlock.newBuilder()
                        .addImplementation('com.squareup.okio:okio:2.6.0')
                        .build())
                .build()

        when:
        def first = one.emit(indenter)
        def second = two.emit(indenter)

        then:
        first == '''\
                    dependencies {
                        implementation 'com.google.guava:guava:19.0'
                        testImplementation 'junit:junit:4.12'
                    }
                    '''.stripIndent()
        second == '''\
                    dependencies {
                        implementation 'com.squareup.okio:okio:2.6.0'
                    }
                    '''.stripIndent()
    }

    def 'should handle top-level statement'() {
        expect:
        ProjectFile.newBuilder()
                .setStatement('group = $S', 'com.example')
                .setStatement('version = $S', '1.2.3')
                .build()
                .emit(indenter) == '''\
                    group = 'com.example'
                    version = '1.2.3'
                    '''.stripIndent()
    }

    def 'should do it all'() {
        expect:
        ProjectFile.newBuilder()
                .withPlugins(PluginsBlock.newBuilder()
                        .withPlugin('org.jenkins-ci.jpi')
                        .build())
                .setStatement('group = $S', 'org.example')
                .setStatement('version = $S', '3.3.1')
                .setStatement('group = $S', 'org.example.something')
                .withBlock(CodeBlock.newBuilder('jenkinsPlugin')
                        .addStatement('jenkinsVersion = $S', '2.222.3')
                        .build())
                .withDependencies(DependenciesBlock.newBuilder()
                        .addImplementation('com.google.guava:guava:19.0')
                        .build())
                .build()
                .emit(indenter) == '''\
                    plugins {
                        id 'org.jenkins-ci.jpi'
                    }

                    version = '3.3.1'
                    group = 'org.example.something'

                    jenkinsPlugin {
                        jenkinsVersion = '2.222.3'
                    }

                    dependencies {
                        implementation 'com.google.guava:guava:19.0'
                    }
                    '''.stripIndent()
    }
}
