package org.jenkinsci.gradle.plugins.testing

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import hudson.Extension
import hudson.model.Action
import hudson.model.Queue
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport

import javax.inject.Inject
import javax.lang.model.element.Modifier

class GeneratedJenkinsTestTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskPath = ':generatedJenkinsTest'
    private File build

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            """.stripIndent()
    }

    def 'should support eager task resolution'() {
        given:
        build << """
            tasks.named('generatedJenkinsTest').get()
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should not have source by default'() {
        given:
        build << """
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.NO_SOURCE
    }

    def 'should pass for api plugin'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should pass for plugin with classes'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            compileJava {
                options.compilerArgs = ['-Xlint:all', '-Xlint:-processing', '-Werror']
            }
            """.stripIndent()
        def decisionHandler = TypeSpec.classBuilder('NeverBuild')
                .addAnnotation(Extension)
                .addModifiers(Modifier.PUBLIC)
                .superclass(Queue.QueueDecisionHandler)
                .addMethod(MethodSpec.methodBuilder('shouldSchedule')
                        .addAnnotation(Override)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean)
                        .addParameter(Queue.Task, 'p')
                        .addParameter(ParameterizedTypeName.get(List, Action), 'actions')
                        .addStatement('return false')
                        .build())
                .build()
        JavaFile.builder('org.example', decisionHandler).build()
                .writeTo(inProjectDir('src/main/java'))

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should pass for plugin with plugin dependencies'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            compileJava {
                options.compilerArgs = ['-Xlint:all', '-Xlint:-processing', '-Werror']
            }
            dependencies {
                implementation 'org.jenkins-ci.plugins:jackson2-api:2.10.3'
            }
            """.stripIndent()
        def decisionHandler = TypeSpec.classBuilder('NeverBuild')
                .addAnnotation(Extension)
                .addModifiers(Modifier.PUBLIC)
                .superclass(Queue.QueueDecisionHandler)
                .addMethod(MethodSpec.methodBuilder('shouldSchedule')
                        .addAnnotation(Override)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean)
                        .addParameter(Queue.Task, 'p')
                        .addParameter(ParameterizedTypeName.get(List, Action), 'actions')
                        .addStatement('return false')
                        .build())
                .build()
        JavaFile.builder('org.example', decisionHandler).build()
                .writeTo(inProjectDir('src/main/java'))

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should fail for plugin injecting class without binding'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            compileJava {
                options.compilerArgs = ['-Xlint:all', '-Xlint:-processing', '-Werror']
            }
            """.stripIndent()
        def myInterface = TypeSpec.interfaceBuilder('MyService')
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.methodBuilder('findValue')
                        .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                        .returns(String)
                        .build())
                .build()
        def srcMainJava = inProjectDir('src/main/java')
        def interfaceFile = JavaFile.builder('org.example.components', myInterface).build()
        interfaceFile.writeTo(srcMainJava)
        def decisionHandler = TypeSpec.classBuilder('NeverBuild')
                .addAnnotation(Extension)
                .addModifiers(Modifier.PUBLIC)
                .superclass(Queue.QueueDecisionHandler)
                .addMethod(MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .build())
                .addMethod(MethodSpec.constructorBuilder()
                        .addAnnotation(Inject)
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(interfaceFile.packageName, myInterface.name), 'myService')
                        .build())
                .addMethod(MethodSpec.methodBuilder('shouldSchedule')
                        .addAnnotation(Override)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(boolean)
                        .addParameter(Queue.Task, 'p')
                        .addParameter(ParameterizedTypeName.get(List, Action), 'actions')
                        .addStatement('return false')
                        .build())
                .build()
        JavaFile.builder('org.example', decisionHandler).build()
                .writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-s')
                .buildAndFail()

        then:
        // this failure isn't very intuitive, but it is the first failure with a missing binding
        result.output.contains('org.jvnet.hudson.test.JellyTestSuiteBuilder$JellyTestSuite FAILED')
    }
}
