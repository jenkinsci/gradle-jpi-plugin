package org.jenkinsci.gradle.plugins.accmod

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import groovy.text.SimpleTemplateEngine
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

import javax.lang.model.element.Modifier
import java.nio.file.Files
import java.nio.file.Path

import static org.jenkinsci.gradle.plugins.jpi.TestSupport.LOG4J_API_2_13_0
import static org.jenkinsci.gradle.plugins.jpi.TestSupport.LOG4J_API_2_14_0

class CheckAccessModifierCachingIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File build
    private Path srcMainJava
    private TypeSpec ok
    private JavaFile okFile
    private final taskPath = ':' + CheckAccessModifierTask.NAME
    private final engine = new SimpleTemplateEngine()
    @SuppressWarnings('GStringExpressionWithinString')
    private final buildTemplate = '''\
        plugins {
            id 'org.jenkins-ci.jpi'
        }

        jenkinsPlugin {
            jenkinsVersion = '$jenkinsVersion'
        }

        tasks.named('checkAccessModifier').configure {
            ignoreFailures.set($ignoreFailures)
        }

        dependencies {
        <% for (dep in dependencies) { %>
            ${dep.configuration} '${dep.coordinate}'
        <% } %>
        }
        '''.stripIndent()
    private final template = engine.createTemplate(buildTemplate)

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        def made = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [],
                'ignoreFailures': false,
        ])
        build.withWriter { made.writeTo(it) }
        srcMainJava = inProjectDir('src/main/java').toPath()
        ok = TypeSpec.classBuilder('Ok')
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.methodBuilder('run')
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int)
                        .addStatement('return 1 + 1')
                        .build())
                .build()
        okFile = JavaFile.builder('org.example.ok', ok).build()
    }

    @Unroll
    def 'should rerun #rerunOutcome when #configuration dependencies #change'(String configuration,
                                                                              List<String> before,
                                                                              List<String> after,
                                                                              String change,
                                                                              TaskOutcome rerunOutcome) {
        given:
        okFile.writeTo(srcMainJava)
        def made = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : before.collect { ['configuration': configuration, 'coordinate': it] },
                'ignoreFailures': false,
        ])
        build.withWriter { made.writeTo(it) }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        existsRelativeToProjectDir('build/access-modifier/main-java.txt')
        existsRelativeToProjectDir('build/access-modifier/main-groovy.txt')

        when:
        def remade = this.template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : after.collect { ['configuration': configuration, 'coordinate': it] },
                'ignoreFailures': false,
        ])
        build.withWriter { remade.writeTo(it) }
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == rerunOutcome

        where:
        configuration    | before             | after              | change      | rerunOutcome
        'compileOnly'    | []                 | [LOG4J_API_2_13_0] | 'added'     | TaskOutcome.SUCCESS
        'compileOnly'    | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed'   | TaskOutcome.SUCCESS
        'compileOnly'    | [LOG4J_API_2_13_0] | []                 | 'removed'   | TaskOutcome.SUCCESS
        'compileOnly'    | [LOG4J_API_2_13_0] | [LOG4J_API_2_13_0] | 'unchanged' | TaskOutcome.UP_TO_DATE

        'implementation' | []                 | [LOG4J_API_2_13_0] | 'added'     | TaskOutcome.SUCCESS
        'implementation' | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed'   | TaskOutcome.SUCCESS
        'implementation' | [LOG4J_API_2_13_0] | []                 | 'removed'   | TaskOutcome.SUCCESS
        'implementation' | [LOG4J_API_2_13_0] | [LOG4J_API_2_13_0] | 'unchanged' | TaskOutcome.UP_TO_DATE

        'runtimeOnly'    | []                 | [LOG4J_API_2_13_0] | 'added'     | TaskOutcome.UP_TO_DATE
        'runtimeOnly'    | [LOG4J_API_2_13_0] | [LOG4J_API_2_14_0] | 'changed'   | TaskOutcome.UP_TO_DATE
        'runtimeOnly'    | [LOG4J_API_2_13_0] | []                 | 'removed'   | TaskOutcome.UP_TO_DATE
        'runtimeOnly'    | [LOG4J_API_2_13_0] | [LOG4J_API_2_13_0] | 'unchanged' | TaskOutcome.UP_TO_DATE
    }

    def 'should not cache failure even without changes'() {
        given:
        JavaFile.builder('org.example.restricted', ok.toBuilder()
                .addMethod(MethodSpec.methodBuilder('callDoNotUse')
                        .addStatement('$1T o = new $1T()', ClassName.get('hudson.plugins.mercurial', 'MercurialChangeSet'))
                        .addStatement('o.setMsg($S)', 'some message')
                        .build())
                .build())
                .build()
                .writeTo(srcMainJava)
        def made = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [
                        ['configuration': 'implementation', 'coordinate': 'org.jenkins-ci.plugins:mercurial:2.10'],
                ],
                'ignoreFailures': false,
        ])
        build.withWriter { made.writeTo(it) }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .buildAndFail()

        then:
        result.task(taskPath).outcome == TaskOutcome.FAILED

        when:
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .buildAndFail()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.FAILED
    }

    def 'should not cache success if ignoreFailures'() {
        given:
        JavaFile.builder('org.example.restricted', ok.toBuilder()
                .addMethod(MethodSpec.methodBuilder('callDoNotUse')
                        .addStatement('$1T o = new $1T()', ClassName.get('hudson.plugins.mercurial', 'MercurialChangeSet'))
                        .addStatement('o.setMsg($S)', 'some message')
                        .build())
                .build())
                .build()
                .writeTo(srcMainJava)
        def made = template.make([
                'jenkinsVersion': TestSupport.RECENT_JENKINS_VERSION,
                'dependencies'  : [
                        ['configuration': 'implementation', 'coordinate': 'org.jenkins-ci.plugins:mercurial:2.10'],
                ],
                'ignoreFailures': true,
        ])
        build.withWriter { made.writeTo(it) }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        result.output.contains('hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')

        when:
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.SUCCESS
        rerunResult.output.contains('hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')
    }

    def 'should rerun when source added'() {
        given:
        okFile.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        JavaFile.builder('org.example.another', ok).build().writeTo(srcMainJava)
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should rerun when source changed'() {
        given:
        okFile.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        JavaFile.builder(okFile.packageName, ok.toBuilder()
                .addMethod(MethodSpec.methodBuilder('additional')
                        .build())
                .build()).build().writeTo(srcMainJava)
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should rerun when source removed'() {
        given:
        okFile.writeTo(srcMainJava)
        def anotherFile = JavaFile.builder('org.example.another', ok).build()
        anotherFile.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        Files.delete(srcMainJava.resolve(anotherFile.toJavaFileObject().toUri().toString()))
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def 'should be up-to-date when source unchanged'() {
        given:
        okFile.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS

        when:
        def rerunResult = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.UP_TO_DATE
    }
}
