package org.jenkinsci.gradle.plugins.accmod

import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport

import javax.lang.model.element.Modifier
import java.nio.file.Path

class CheckAccessModifierIgnoreFailuresIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File build
    private Path srcMainJava

    def setup() {
        File settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            jenkinsPlugin {
                jenkinsVersion.set('${TestSupport.RECENT_JENKINS_VERSION}')
            }
            dependencies {
                implementation 'org.jenkins-ci.plugins:mercurial:2.10'
            }
            """.stripIndent()
        srcMainJava = inProjectDir('src/main/java').toPath()
        JavaFile.builder('org.example', TypeSpec.classBuilder('Consumer')
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.methodBuilder('callDoNotUse')
                        .addStatement('$1T o = new $1T()', ClassName.get('hudson.plugins.mercurial', 'MercurialChangeSet'))
                        .addStatement('o.setMsg($S)', 'some message')
                        .build())
                .addMethod(MethodSpec.methodBuilder('callNoExternalUse')
                        .addStatement('$1T o = new $1T()', ClassName.get('hudson.plugins.mercurial', 'MercurialStatus'))
                        .addStatement('o.doNotifyCommit($S, $S, $S)', '', '', '')
                        .addException(ClassName.get('javax.servlet', 'ServletException'))
                        .addException(IOException)
                        .build())
                .build())
                .build()
                .writeToPath(srcMainJava)
    }

    def 'should not fail when using @Restricted method by default'() {
        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-s')
                .build()

        then:
        result.task(':' + CheckAccessModifierTask.NAME).outcome == TaskOutcome.SUCCESS
        result.output.contains('hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')
        result.output.contains('hudson/plugins/mercurial/MercurialStatus.doNotifyCommit(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lorg/kohsuke/stapler/HttpResponse; must not be used')
    }

    def 'should allow overriding by configuring task property'() {
        given:
        build << '''
            tasks.named('checkAccessModifier').configure {
                ignoreFailures.set(false)
            }
            '''.stripIndent()

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME)
                .buildAndFail()

        then:
        result.task(':' + CheckAccessModifierTask.NAME).outcome == TaskOutcome.FAILED
        result.output.contains('hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')
        result.output.contains('hudson/plugins/mercurial/MercurialStatus.doNotifyCommit(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lorg/kohsuke/stapler/HttpResponse; must not be used')
    }
}
