package org.jenkinsci.gradle.plugins.accmod

import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import groovy.transform.CompileStatic
import org.gradle.testkit.runner.TaskOutcome
import org.jenkinsci.gradle.plugins.jpi.IntegrationSpec
import org.jenkinsci.gradle.plugins.jpi.TestDataGenerator
import org.jenkinsci.gradle.plugins.jpi.TestSupport
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.Beta
import org.kohsuke.accmod.restrictions.DoNotUse
import org.kohsuke.accmod.restrictions.NoExternalUse
import org.kohsuke.accmod.restrictions.None
import org.kohsuke.accmod.restrictions.ProtectedExternally
import spock.lang.PendingFeature
import spock.lang.Unroll

import javax.lang.model.element.Modifier
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class CheckAccessModifierIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File build
    private Path srcMainJava
    private Path srcMainGroovy
    private TypeSpec ohNo
    private JavaFile ohNoFile
    private TypeSpec consumer
    private JavaFile consumerFile
    private final Map<Class, String> expectedMessages = [
            (DoNotUse)     : 'must not be used',
            (Beta)         : 'is still in beta',
            (NoExternalUse): 'must not be used',
    ]

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
            tasks.named('checkAccessModifier').configure {
                ignoreFailures.set(false)
            }
            """.stripIndent()
        srcMainJava = inProjectDir('src/main/java').toPath()
        srcMainGroovy = inProjectDir('src/main/groovy').toPath()
        ohNo = TypeSpec.classBuilder('OhNo')
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.methodBuilder('add')
                        .addModifiers(Modifier.PUBLIC)
                        .returns(int)
                        .addParameter(int, 'a')
                        .addParameter(int, 'b')
                        .addStatement('return a + b')
                        .build())
                .build()
        ohNoFile = JavaFile.builder('org.example.restricted', ohNo).build()
        consumer = TypeSpec.classBuilder('Consumer')
                .addModifiers(Modifier.PUBLIC)
                .addMethod(MethodSpec.methodBuilder('consume')
                        .addModifiers(Modifier.PUBLIC)
                        .returns(void)
                        .addStatement('$1T o = new $1T()', ClassName.get(ohNoFile.packageName, ohNo.name))
                        .addStatement('o.add(3, 3)')
                        .build())
                .build()
        consumerFile = JavaFile.builder('org.example.blessed', consumer).build()
    }

    private static void renameFileToGroovy(Path path) {
        Files.move(path, Paths.get(path.toString().replace('.java', '.groovy')))
    }

    enum Language {
        GROOVY, JAVA
    }

    @Unroll
    def 'should fail when using @Restricted method from #srcDir (ext: #ext)'(Language srcDir, Language ext, Class<?> annotation) {
        given:
        build << '''
            dependencies {
                implementation 'org.jenkins-ci.plugins:mercurial:2.10'
            }
            '''.stripIndent()
        def builder = TypeSpec.classBuilder('Consumer')
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
        if (annotation) {
            builder.addAnnotation(annotation)
        }
        Path dir = srcDir == Language.JAVA ? srcMainJava : srcMainGroovy
        def written = JavaFile.builder('org.example.blessed', builder.build())
                .build()
                .writeToPath(dir)
        if (ext == Language.GROOVY) {
            renameFileToGroovy(written)
        }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-s')
                .buildAndFail()

        then:
        result.output.contains('hudson/plugins/mercurial/MercurialChangeSet.setMsg(Ljava/lang/String;)V must not be used')
        result.output.contains('hudson/plugins/mercurial/MercurialStatus.doNotifyCommit(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Lorg/kohsuke/stapler/HttpResponse; must not be used')

        where:
        srcDir          | ext             | annotation
        Language.JAVA   | Language.JAVA   | null
        Language.GROOVY | Language.JAVA   | null
        Language.GROOVY | Language.GROOVY | CompileStatic
//        these unexpectedly pass
//        Language.groovy | Language.groovy | CompileDynamic
//        Language.groovy | Language.groovy | null
    }

    @Unroll
    def 'should pass #type.simpleName internally #upstreamExt -> #downstreamExt'(Language upstreamExt, Language downstreamExt, Class<?> type, boolean useGroovyDir) {
        given:
        Path dir = useGroovyDir ? srcMainGroovy : srcMainJava
        def annotated = ohNo.toBuilder()
                .addAnnotation(AnnotationSpec.builder(Restricted)
                        .addMember('value', '$T.class', type)
                        .build())
                .build()

        def annotatedFile = JavaFile.builder('org.example.restricted', annotated).build()
        def upstream = annotatedFile.writeToPath(dir)
        if (upstreamExt == Language.GROOVY) {
            renameFileToGroovy(upstream)
        }
        def downstream = consumerFile.writeToPath(dir)
        if (downstreamExt == Language.GROOVY) {
            renameFileToGroovy(downstream)
        }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-s')
                .build()

        then:
        result.task(':' + CheckAccessModifierTask.NAME).outcome == TaskOutcome.SUCCESS

        where:
        upstreamExt     | downstreamExt   | type                | useGroovyDir
        Language.GROOVY | Language.GROOVY | None                | true
        Language.GROOVY | Language.GROOVY | ProtectedExternally | true

        Language.GROOVY | Language.JAVA   | None                | true
        Language.GROOVY | Language.JAVA   | ProtectedExternally | true

        Language.JAVA   | Language.JAVA   | None                | true
        Language.JAVA   | Language.JAVA   | ProtectedExternally | true
        Language.JAVA   | Language.JAVA   | None                | false
        Language.JAVA   | Language.JAVA   | ProtectedExternally | false

        Language.JAVA   | Language.JAVA   | None                | true
        Language.JAVA   | Language.JAVA   | ProtectedExternally | true
    }

    @Unroll
    def 'should fail #type.simpleName class internally #upstreamExt -> #downstreamExt'(Language upstreamExt, Language downstreamExt, Class<?> type, boolean useGroovyDir) {
        given:
        Path dir = useGroovyDir ? srcMainGroovy : srcMainJava
        def annotated = ohNo.toBuilder()
                .addAnnotation(AnnotationSpec.builder(Restricted)
                        .addMember('value', '$T.class', type)
                        .build())
                .build()

        def annotatedFile = JavaFile.builder('org.example.restricted', annotated).build()
        def upstream = annotatedFile.writeToPath(dir)
        if (upstreamExt == Language.GROOVY) {
            renameFileToGroovy(upstream)
        }
        def downstream = consumerFile.writeToPath(dir)
        if (downstreamExt == Language.GROOVY) {
            renameFileToGroovy(downstream)
        }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-s')
                .buildAndFail()

        then:
        result.output.contains('org/example/restricted/OhNo ' + expectedMessages.get(type))

        where:
        upstreamExt     | downstreamExt   | type          | useGroovyDir
//        these unexpectedly pass
//        Language.GROOVY | Language.GROOVY | DoNotUse      | true
//        Language.GROOVY | Language.GROOVY | Beta          | true
//        Language.GROOVY | Language.GROOVY | NoExternalUse | true

        Language.GROOVY | Language.JAVA   | DoNotUse      | true
        Language.GROOVY | Language.JAVA   | Beta          | true
        Language.GROOVY | Language.JAVA   | NoExternalUse | true

        Language.JAVA   | Language.JAVA   | DoNotUse      | true
        Language.JAVA   | Language.JAVA   | Beta          | true
        Language.JAVA   | Language.JAVA   | NoExternalUse | true
        Language.JAVA   | Language.JAVA   | DoNotUse      | false
        Language.JAVA   | Language.JAVA   | Beta          | false
        Language.JAVA   | Language.JAVA   | NoExternalUse | false

        Language.JAVA   | Language.JAVA   | DoNotUse      | true
        Language.JAVA   | Language.JAVA   | Beta          | true
        Language.JAVA   | Language.JAVA   | NoExternalUse | true
    }

    @Unroll
    def 'should pass Beta with property #upstreamExt -> #downstreamExt'(Language upstreamExt, Language downstreamExt, boolean useGroovyDir) {
        given:
        Path dir = useGroovyDir ? srcMainGroovy : srcMainJava
        def annotated = ohNo.toBuilder()
                .addAnnotation(AnnotationSpec.builder(Restricted)
                        .addMember('value', '$T.class', Beta)
                        .build())
                .build()

        def annotatedFile = JavaFile.builder('org.example.restricted', annotated).build()
        def upstream = annotatedFile.writeToPath(dir)
        if (upstreamExt == Language.GROOVY) {
            renameFileToGroovy(upstream)
        }
        def downstream = consumerFile.writeToPath(dir)
        if (downstreamExt == Language.GROOVY) {
            renameFileToGroovy(downstream)
        }

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-PcheckAccessModifier.useBeta=true', '-s')
                .build()

        then:
        result.task(':' + CheckAccessModifierTask.NAME).outcome == TaskOutcome.SUCCESS

        where:
        upstreamExt     | downstreamExt   | useGroovyDir
        Language.GROOVY | Language.GROOVY | true
        Language.GROOVY | Language.JAVA   | true
        Language.JAVA   | Language.JAVA   | true
        Language.JAVA   | Language.JAVA   | false
    }

    @Unroll
    @PendingFeature
    def 'should fail #restrictionType.simpleName when dynamic groovy references java'(Class<?> restrictionType, String expected) {
        given:
        def annotated = ohNo.toBuilder()
                .addAnnotation(AnnotationSpec.builder(Restricted)
                        .addMember('value', '$T.class', restrictionType)
                        .build())
                .build()

        def annotatedFile = JavaFile.builder('org.example.restricted', annotated).build()
        annotatedFile.writeToPath(srcMainGroovy)
        renameFileToGroovy(consumerFile.writeToPath(srcMainGroovy))

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-s')
                .buildAndFail()

        then:
        result.output.contains('org/example/restricted/OhNo ' + expected)

        where:
        restrictionType | expected
        DoNotUse        | 'must not be used'
        Beta            | 'is still in beta'
    }

    @Unroll
    @PendingFeature
    def 'should fail if package #restrictionType internally'(String restrictionType, String expected) {
        given:
        ohNoFile.writeTo(srcMainJava)
        consumerFile.writeTo(srcMainJava)
        touchInProjectDir('src/main/java/org/example/restricted/package-info.java') << """\
            @Restricted(${restrictionType}.class)
            package org.example.restricted;

            import org.kohsuke.accmod.Restricted;
            import org.kohsuke.accmod.restrictions.*;

            """.stripIndent().normalize()

        when:
        def result = gradleRunner()
                .withArguments(CheckAccessModifierTask.NAME, '-s')
                .buildAndFail()

        then:
        result.output.contains("org/example/restricted/OhNo ${expected}")

        where:
        restrictionType | expected
        'DoNotUse'      | 'must not be used'
        'Beta'          | 'is still in beta'
    }
}
