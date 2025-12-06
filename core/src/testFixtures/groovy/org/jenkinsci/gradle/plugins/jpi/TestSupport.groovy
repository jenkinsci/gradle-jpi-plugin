package org.jenkinsci.gradle.plugins.jpi

import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import groovy.transform.CompileStatic
import org.junit.Assert
import org.junit.Test

import javax.lang.model.element.Modifier
import java.util.function.Function

@CompileStatic
class TestSupport {
    static final EMBEDDED_IVY_URL = "${System.getProperty('user.dir')}/src/test/repo"
            .replace('\\', '/')

    static final RECENT_JENKINS_VERSION = '2.401.3'

    static final TypeSpec CALCULATOR_CLASS = TypeSpec.classBuilder('Calculator')
            .addModifiers(Modifier.PUBLIC)
            .addMethod(MethodSpec.methodBuilder('add')
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(int.class, 'a')
                    .addParameter(int.class, 'b')
                    .addStatement('return a + b')
                    .returns(int.class)
                    .build())
            .build()

    static final JavaFile CALCULATOR = JavaFile.builder('org.example', CALCULATOR_CLASS).build()

    static final JavaFile PASSING_TEST = JavaFile.builder('org.example', TypeSpec.classBuilder('AdditionTest')
            .addModifiers(Modifier.PUBLIC)
            .addMethod(MethodSpec.methodBuilder('shouldAdd')
                    .addAnnotation(Test)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(void)
                    .addStatement('$T.assertEquals(3, 1 + 2)', Assert)
                    .build())
            .build())
            .build()

    static final Function<File, JavaFile> TEST_THAT_WRITES_SYSTEM_PROPERTIES_TO = new Function<File, JavaFile>() {
        @Override
        JavaFile apply(File file) {
            JavaFile.builder('org.example', TypeSpec.classBuilder('ExampleTest')
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(MethodSpec.methodBuilder('shouldHaveSystemProperties')
                            .addAnnotation(Test)
                            .addModifiers(Modifier.PUBLIC)
                            .returns(void)
                            .addException(Exception)
                            .addStatement('$1T writer = new $1T($2S)', FileWriter, file)
                            .addStatement('$T.getProperties().store(writer, null)', System)
                            .build())
                    .build()).build()
        }
    }

    static final String LOG4J_API_2_13_0 = 'org.apache.logging.log4j:log4j-api:2.13.0'
    static final String LOG4J_API_2_14_0 = 'org.apache.logging.log4j:log4j-api:2.14.0'
    static final String ANT_1_10 = 'org.jenkins-ci.plugins:ant:1.10'
    static final String ANT_1_11 = 'org.jenkins-ci.plugins:ant:1.11'

    /**
     * Adds quotes to given string
     */
    static String q(String s) {
        "'$s'"
    }

    static String ant(String version) {
        q("org.jenkins-ci.plugins:ant:$version")
    }

    static String git(String version) {
        q("org.jenkins-ci.plugins:git:$version")
    }

    static String log4jApi(String version) {
        q("org.apache.logging.log4j:log4j-api:$version")
    }
}
