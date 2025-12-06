package org.jenkinsci.gradle.plugins.testing

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import hudson.Extension
import hudson.PluginManager
import hudson.PluginWrapper
import hudson.model.Action
import hudson.model.FreeStyleProject
import hudson.model.Queue
import hudson.model.queue.ScheduleResult
import jenkins.model.Jenkins
import org.assertj.core.api.Assertions
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import org.junit.Rule
import org.junit.Test

import javax.lang.model.element.Modifier

class TestTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskPath = ':test'
    private File build

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            dependencies {
                testImplementation 'org.assertj:assertj-core:3.19.0'
            }
            """.stripIndent()
    }

    def 'should support eager task resolution'() {
        given:
        build << """
            tasks.named('test').get()
            jenkinsPlugin {
                generateTests.set(false)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            """.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-is')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.NO_SOURCE
        !result.output.contains('java.lang.ClassNotFoundException')
        !result.output.contains('No SLF4J providers were found.')
    }

    def 'should work out of the box with JenkinsRule'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            compileTestJava {
                options.compilerArgs = ['-Xlint:all', '-Xlint:-processing', '-Werror']
            }
            """.stripIndent()
        def srcMainJava = inProjectDir('src/main/java')
        def srcTestJava = inProjectDir('src/test/java')
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
                .writeTo(srcMainJava)

        def jenkinsRule = ClassName.get('org.jvnet.hudson.test', 'JenkinsRule')
        def test = TypeSpec.classBuilder('DecisionHandlerTest')
                .addModifiers(Modifier.PUBLIC)
                .addField(FieldSpec.builder(jenkinsRule, 'j', Modifier.PUBLIC)
                        .addAnnotation(Rule)
                        .initializer('new $T()', jenkinsRule)
                        .build())
                .addMethod(MethodSpec.methodBuilder('shouldNotScheduleBuild')
                        .addAnnotation(Test)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void)
                        .addException(IOException)
                        .addStatement('$T job = j.createFreeStyleProject()', FreeStyleProject)
                        .addStatement('$T q = j.jenkins.getQueue()', Queue)
                        .addStatement('$T result = q.schedule2(job, 0)', ScheduleResult)
                        .addStatement('assertThat(result.isRefused()).describedAs("build was not scheduled").isTrue()')
                        .build())
                .build()
        JavaFile.builder('org.example', test)
                .indent('    ')
                .addStaticImport(Assertions, 'assertThat')
                .build()
                .writeTo(srcTestJava)

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-is')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        !result.output.contains('java.lang.ClassNotFoundException')
        !result.output.contains('No SLF4J providers were found.')
    }

    def 'should be installed by default'() {
        given:
        build << """
            jenkinsPlugin {
                generateTests.set(true)
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            compileTestJava {
                options.compilerArgs = ['-Xlint:all', '-Xlint:-processing', '-Werror']
            }
            dependencies {
                implementation 'org.jenkins-ci.plugins:gradle:1.35'
                implementation 'org.jenkins-ci.plugins:junit:1.20'
                runtimeOnly 'io.jenkins.plugins:javax-mail-api:1.6.2-10'
                runtimeOnly 'org.jenkins-ci.plugins:script-security:1172.v35f6a_0b_8207e'
            }
            """.stripIndent()
        def srcTestJava = inProjectDir('src/test/java')
        def jenkinsRule = ClassName.get('org.jvnet.hudson.test', 'JenkinsRule')
        def test = TypeSpec.classBuilder('HplPresentTest')
                .addModifiers(Modifier.PUBLIC)
                .addField(FieldSpec.builder(jenkinsRule, 'j', Modifier.PUBLIC)
                        .addAnnotation(Rule)
                        .initializer('new $T()', jenkinsRule)
                        .build())
                .addMethod(MethodSpec.methodBuilder('shouldNotScheduleBuild')
                        .addAnnotation(Test)
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void)
                        .addException(IOException)
                        .addStatement('$T jenkins = j.jenkins', Jenkins)
                        .addStatement('$T pluginManager = jenkins.getPluginManager()', PluginManager)
                        .addStatement('$T failedPlugins = pluginManager.getFailedPlugins()', ParameterizedTypeName.get(List, PluginManager.FailedPlugin))
                        .beginControlFlow('for ($T plugin : failedPlugins)', PluginManager.FailedPlugin)
                        .addStatement('fail("%s failed to start: %s", plugin.name, plugin.cause)')
                        .endControlFlow()
                        .addStatement('$T plugin = pluginManager.getPlugin($S)', PluginWrapper, projectName)
                        .addStatement('assertThat(plugin).describedAs("$L started").isNotNull()', projectName)
                        .addStatement('assertThat(plugin.isActive()).describedAs("$L failed to start").isNotNull()', projectName)
                        .build())
                .build()
        JavaFile.builder('org.example', test)
                .indent('    ')
                .addStaticImport(Assertions, 'assertThat', 'fail')
                .build()
                .writeTo(srcTestJava)

        when:
        def result = gradleRunner()
                .withArguments(taskPath, '-is')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        !result.output.contains('java.lang.ClassNotFoundException')
        !result.output.contains('No SLF4J providers were found.')
    }
}
