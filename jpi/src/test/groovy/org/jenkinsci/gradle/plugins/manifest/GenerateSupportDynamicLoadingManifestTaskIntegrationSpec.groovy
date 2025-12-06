package org.jenkinsci.gradle.plugins.manifest

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import hudson.Extension
import jenkins.YesNoMaybe
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Unroll

import javax.lang.model.element.Modifier
import java.nio.file.Path
import java.util.jar.Manifest

import static java.util.jar.Attributes.Name.MANIFEST_VERSION

class GenerateSupportDynamicLoadingManifestTaskIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private final String taskPath = ':' + GenerateSupportDynamicLoadingManifestTask.NAME
    private File build
    private Path srcMainJava
    private Path srcMainGroovy

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
            """.stripIndent()
        srcMainJava = inProjectDir('src/main/java').toPath()
        srcMainGroovy = inProjectDir('src/main/groovy').toPath()
    }

    @Unroll
    def 'should populate Support-Dynamic-Loading conditionally (#value)'(YesNoMaybe value, String manifestValue) {
        given:
        def expected = new Manifest()
        expected.mainAttributes[MANIFEST_VERSION] = '1.0'
        if (manifestValue) {
            expected.mainAttributes.putValue('Support-Dynamic-Loading', manifestValue)
        }

        def plugin = TypeSpec.classBuilder('TestPlugin')
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Extension)
                        .addMember('dynamicLoadable', '$T.$L', value.class, value.name())
                        .build())
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(GenerateSupportDynamicLoadingManifestTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        actualManifest() == expected

        when:
        def rerunResult = gradleRunner()
                .withArguments(GenerateSupportDynamicLoadingManifestTask.NAME)
                .build()

        then:
        rerunResult.task(taskPath).outcome == TaskOutcome.UP_TO_DATE

        where:
        value            | manifestValue
        YesNoMaybe.YES   | 'true'
        YesNoMaybe.MAYBE | null
        YesNoMaybe.NO    | 'false'
    }

    def 'should use NO if any Extension is NO'() {
        given:
        def expected = new Manifest()
        expected.mainAttributes[MANIFEST_VERSION] = '1.0'
        expected.mainAttributes.putValue('Support-Dynamic-Loading', 'false')

        [YesNoMaybe.YES, YesNoMaybe.MAYBE, YesNoMaybe.NO].each { value ->
            def plugin = TypeSpec.classBuilder('TestPlugin' + value.name().capitalize())
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(Extension)
                            .addMember('dynamicLoadable', '$T.$L', value.class, value.name())
                            .build())
                    .build()
            def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
            myExamplePlugin.writeTo(srcMainJava)
        }
        writeDefaultValuePlugin()

        when:
        def result = gradleRunner()
                .withArguments(GenerateSupportDynamicLoadingManifestTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        actualManifest() == expected
    }

    def 'should use MAYBE if no Extension is NO and MAYBE or default exists'() {
        given:
        def expected = new Manifest()
        expected.mainAttributes[MANIFEST_VERSION] = '1.0'

        [YesNoMaybe.YES, YesNoMaybe.MAYBE].each { value ->
            def plugin = TypeSpec.classBuilder('TestPlugin' + value.name().capitalize())
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(Extension)
                            .addMember('dynamicLoadable', '$T.$L', value.class, value.name())
                            .build())
                    .build()
            def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
            myExamplePlugin.writeTo(srcMainJava)
        }
        writeDefaultValuePlugin()

        when:
        def result = gradleRunner()
                .withArguments(GenerateSupportDynamicLoadingManifestTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        actualManifest() == expected
    }

    def 'should use YES if all Extensions are YES'() {
        given:
        def expected = new Manifest()
        expected.mainAttributes[MANIFEST_VERSION] = '1.0'
        expected.mainAttributes.putValue('Support-Dynamic-Loading', 'true')

        [YesNoMaybe.YES, YesNoMaybe.YES, YesNoMaybe.YES].eachWithIndex { value, i ->
            def plugin = TypeSpec.classBuilder('TestPlugin' + value.name().capitalize() + i)
                    .addModifiers(Modifier.PUBLIC)
                    .addAnnotation(AnnotationSpec.builder(Extension)
                            .addMember('dynamicLoadable', '$T.$L', value.class, value.name())
                            .build())
                    .build()
            def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
            myExamplePlugin.writeTo(srcMainJava)
        }

        when:
        def result = gradleRunner()
                .withArguments(GenerateSupportDynamicLoadingManifestTask.NAME)
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
        actualManifest() == expected
    }

    def 'should support configuration cache'() {
        given:
        def plugin = TypeSpec.classBuilder('TestPlugin')
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Extension)
                        .addMember('dynamicLoadable', '$T.$L', YesNoMaybe, YesNoMaybe.YES.name())
                        .build())
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)

        when:
        def result = gradleRunner()
                .withArguments(GenerateSupportDynamicLoadingManifestTask.NAME, '--configuration-cache')
                .build()

        then:
        result.task(taskPath).outcome == TaskOutcome.SUCCESS
    }

    def writeDefaultValuePlugin() {
        def plugin = TypeSpec.classBuilder('TestPluginDefaultValue')
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(AnnotationSpec.builder(Extension).build())
                .build()
        def myExamplePlugin = JavaFile.builder('my.example', plugin).build()
        myExamplePlugin.writeTo(srcMainJava)
    }

    def actualManifest() {
        new Manifest(inProjectDir('build/jenkins-manifests/support-dynamic-loading.mf').newInputStream())
    }
}
