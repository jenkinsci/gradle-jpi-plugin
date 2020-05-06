package org.jenkinsci.gradle.plugins.jpi.server

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.support.CodeBlock
import org.jenkinsci.gradle.plugins.jpi.support.DependenciesBlock
import org.jenkinsci.gradle.plugins.jpi.support.Neptune
import org.jenkinsci.gradle.plugins.jpi.support.PluginsBlock
import org.jenkinsci.gradle.plugins.jpi.support.ProjectFile
import spock.lang.Unroll

import java.nio.file.Files

import static InstallJenkinsServerPluginsTask.TASK_NAME

class InstallJenkinsServerPluginsTaskSpec extends IntegrationSpec {
    private static final String TASK_PATH = ':' + TASK_NAME
    private static final Set<String> DEFAULT = ['ui-samples-plugin.hpi'] as Set
    private static final Set<String> SOLO = DEFAULT + ['apache-httpcomponents-client-4-api.hpi'] as Set
    private static final Set<String> TRANSITIVES = DEFAULT + ['apache-httpcomponents-client-4-api.hpi',
                                                              'credentials.hpi',
                                                              'display-url-api.hpi',
                                                              'git.hpi',
                                                              'git-client.hpi',
                                                              'jsch.hpi',
                                                              'junit.hpi',
                                                              'mailer.hpi',
                                                              'scm-api.hpi',
                                                              'ssh-credentials.hpi',
                                                              'structs.hpi',
                                                              'ui-samples-plugin.hpi',
                                                              'workflow-scm-step.hpi',
                                                              'workflow-step-api.hpi'] as Set
    private final String projectName = TestDataGenerator.generateName()
    private ProjectFile.Builder projectBuilder

    def setup() {
        projectBuilder = ProjectFile.newBuilder()
                .withName(projectName)
                .withPlugins(PluginsBlock.newBuilder()
                        .withPlugin('org.jenkins-ci.jpi')
                        .build())
                .withBlock(CodeBlock.newBuilder('jenkinsPlugin')
                        .addStatement('coreVersion = $S', '2.222.3')
                        .build())
    }

    def 'should sync plugins without version'(Set<String> dependencies) {
        given:
        projectBuilder.withDependencies(DependenciesBlock.newBuilder()
                .addAllToImplementation(dependencies)
                .build())
        Neptune.newBuilder(projectBuilder.build())
                .build()
                .writeTo(projectDir)

        when:
        def result = runInstallJenkinsServerPlugins()

        then:
        result.task(TASK_PATH).outcome == TaskOutcome.SUCCESS
        actualPluginsDir() == expectedPluginsDir(dependencies)

        and:
        def rerun = runInstallJenkinsServerPlugins()
        rerun.task(TASK_PATH).outcome == TaskOutcome.UP_TO_DATE

        where:
        dependencies                                                             | _
        []                                                                       | _
        ['com.google.guava:guava:19.0']                                          | _
        ['org.jenkins-ci.plugins:apache-httpcomponents-client-4-api:4.5.10-1.0'] | _
        ['org.jenkins-ci.plugins:git:4.0.0']                                     | _
    }

    @Unroll
    def 'should rerun if #config dependencies #description'(String config,
                                                            String description,
                                                            List<String> starting,
                                                            List<String> updated) {
        given:
        projectBuilder.withDependencies(DependenciesBlock.newBuilder()
                .addAllTo(config, starting)
                .build())
        Neptune.newBuilder(projectBuilder.build())
                .build()
                .writeTo(projectDir)

        when:
        def firstResult = runInstallJenkinsServerPlugins()

        then:
        firstResult.task(TASK_PATH).outcome == TaskOutcome.SUCCESS
        actualPluginsDir() == expectedPluginsDir(starting)

        when:
        projectBuilder
                .clearDependencies()
                .withDependencies(DependenciesBlock.newBuilder()
                        .addAllTo(config, updated)
                        .build())
        Neptune.newBuilder(projectBuilder.build())
                .build()
                .writeTo(projectDir)
        def secondResult = runInstallJenkinsServerPlugins()

        then:
        secondResult.task(TASK_PATH).outcome == TaskOutcome.SUCCESS
        actualPluginsDir() == expectedPluginsDir(updated)

        where:
        config               | description   | starting                             | updated
        'api'                | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'api'                | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.0']
        'api'                | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'api'                | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'api'                | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'api'                | 'removed jar' | ['com.google.guava:guava:20.0']      | []

        'implementation'     | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'implementation'     | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.1']
        'implementation'     | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'implementation'     | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'implementation'     | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'implementation'     | 'removed jar' | ['com.google.guava:guava:20.0']      | []

        'runtimeOnly'        | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'runtimeOnly'        | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.1']
        'runtimeOnly'        | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'runtimeOnly'        | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'runtimeOnly'        | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'runtimeOnly'        | 'removed jar' | ['com.google.guava:guava:20.0']      | []

        'testImplementation' | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'testImplementation' | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.1']
        'testImplementation' | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'testImplementation' | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'testImplementation' | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'testImplementation' | 'removed jar' | ['com.google.guava:guava:20.0']      | []

        'testRuntimeOnly'    | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'testRuntimeOnly'    | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.1']
        'testRuntimeOnly'    | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'testRuntimeOnly'    | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'testRuntimeOnly'    | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'testRuntimeOnly'    | 'removed jar' | ['com.google.guava:guava:20.0']      | []
    }

    @Unroll
    def 'should be UP-TO-DATE if #config dependencies #description'(String config,
                                                                    String description,
                                                                    List<String> starting,
                                                                    List<String> updated) {
        given:
        projectBuilder.withDependencies(DependenciesBlock.newBuilder()
                .addAllTo(config, starting)
                .build())
        Neptune.newBuilder(projectBuilder.build())
                .build()
                .writeTo(projectDir)

        when:
        def firstResult = runInstallJenkinsServerPlugins()

        then:
        firstResult.task(TASK_PATH).outcome == TaskOutcome.SUCCESS
        actualPluginsDir() == expectedPluginsDir(starting, config)

        when:
        projectBuilder
                .clearDependencies()
                .withDependencies(DependenciesBlock.newBuilder()
                        .addAllTo(config, updated)
                        .build())
        Neptune.newBuilder(projectBuilder.build())
                .build()
                .writeTo(projectDir)
        def secondResult = runInstallJenkinsServerPlugins()

        then:
        secondResult.task(TASK_PATH).outcome == TaskOutcome.UP_TO_DATE

        where:
        config               | description   | starting                             | updated
        'api'                | 'stay jpi'    | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.0']
        'api'                | 'stay jar'    | ['com.google.guava:guava:20.0']      | ['com.google.guava:guava:20.0']
        'implementation'     | 'stay jpi'    | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.0']
        'implementation'     | 'stay jar'    | ['com.google.guava:guava:20.0']      | ['com.google.guava:guava:20.0']
        'runtimeOnly'        | 'stay jpi'    | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.0']
        'runtimeOnly'        | 'stay jar'    | ['com.google.guava:guava:20.0']      | ['com.google.guava:guava:20.0']
        'testImplementation' | 'stay jpi'    | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.0']
        'testImplementation' | 'stay jar'    | ['com.google.guava:guava:20.0']      | ['com.google.guava:guava:20.0']
        'testRuntimeOnly'    | 'stay jpi'    | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.0']
        'testRuntimeOnly'    | 'stay jar'    | ['com.google.guava:guava:20.0']      | ['com.google.guava:guava:20.0']

        'compileOnly'        | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'compileOnly'        | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.0']
        'compileOnly'        | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'compileOnly'        | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'compileOnly'        | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'compileOnly'        | 'removed jar' | ['com.google.guava:guava:20.0']      | []

        'testCompileOnly'    | 'changed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | ['org.jenkins-ci.plugins:git:4.0.1']
        'testCompileOnly'    | 'added jpi'   | []                                   | ['org.jenkins-ci.plugins:git:4.0.0']
        'testCompileOnly'    | 'removed jpi' | ['org.jenkins-ci.plugins:git:4.0.0'] | []
        'testCompileOnly'    | 'changed jar' | ['com.google.guava:guava:19.0']      | ['com.google.guava:guava:20.0']
        'testCompileOnly'    | 'added jar'   | []                                   | ['com.google.guava:guava:20.0']
        'testCompileOnly'    | 'removed jar' | ['com.google.guava:guava:20.0']      | []
    }

    def 'should rerun if workDir changes'() {
        given:
        def deps = ['org.jenkins-ci.plugins:git:4.0.0']
        projectBuilder.withDependencies(DependenciesBlock.newBuilder()
                .addAllToImplementation(deps)
                .build())
                .withBlock(CodeBlock.newBuilder('jenkinsPlugin')
                        .setStatement('workDir = file($S)', 'work')
                        .build())
        Neptune.newBuilder(projectBuilder.build())
                .build()
                .writeTo(projectDir)

        when:
        def firstResult = runInstallJenkinsServerPlugins()

        then:
        firstResult.task(TASK_PATH).outcome == TaskOutcome.SUCCESS
        actualPluginsDir() == expectedPluginsDir(deps)

        when:
        projectBuilder.withBlock(CodeBlock.newBuilder('jenkinsPlugin')
                .setStatement('workDir = file($S)', 'jenkins-home')
                .build())
        Neptune.newBuilder(projectBuilder.build()).build().writeTo(projectDir)
        def secondResult = runInstallJenkinsServerPlugins()

        then:
        secondResult.task(TASK_PATH).outcome == TaskOutcome.SUCCESS
        actualPluginsDir('jenkins-home') == expectedPluginsDir(deps)
    }

    private Set<String> actualPluginsDir(String dir = 'work') {
        Files.list(projectDir.root.toPath().resolve(dir).resolve('plugins'))
                .collect { it.fileName.toString() }
                .toSet()
    }

    private Set<String> expectedPluginsDir(Collection<String> dependencies, String configuration = 'implementation') {
        def hpl = projectName + '.hpl'
        def result = [hpl] as Set
        result.addAll(DEFAULT)
        if (!['api', 'implementation', 'runtimeOnly', 'testImplementation', 'testRuntimeOnly'].contains(configuration)) {
            return result
        }
        if (dependencies.contains('org.jenkins-ci.plugins:git:4.0.0')) {
            result.addAll(TRANSITIVES)
        }
        if (dependencies.contains('org.jenkins-ci.plugins:git:4.0.1')) {
            result.addAll(TRANSITIVES)
        }
        if (dependencies.contains('org.jenkins-ci.plugins:apache-httpcomponents-client-4-api:4.5.10-1.0')) {
            result.addAll(SOLO)
        }
        result
    }

    private BuildResult runInstallJenkinsServerPlugins() {
        gradleRunner().withArguments(TASK_NAME).build()
    }
}
