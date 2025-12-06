package org.jenkinsci.gradle.plugins.jpi

import java.util.jar.JarEntry
import java.util.jar.JarInputStream

class JpiPublishingAndConsumptionTest extends IntegrationSpec {
    private File producerBuild
    private File consumerBuild

    def setup() {
        mkDirInProjectDir('producer')
        mkDirInProjectDir('consumer')
        def repo = mkDirInProjectDir('repo')
        touchInProjectDir('producer/settings.gradle') << 'rootProject.name = "producer"'
        touchInProjectDir('consumer/settings.gradle') << 'rootProject.name = "consumer"'
        producerBuild = touchInProjectDir('producer/build.gradle')
        consumerBuild = touchInProjectDir('consumer/build.gradle')
        producerBuild << """\
            plugins {
                id 'org.jenkins-ci.jpi'
                id 'maven-publish'
            }
            group = 'org'
            version = '1.0'
            publishing {
                repositories {
                    maven {
                        name 'test'
                        url '${path(repo)}'
                    }
                }
            }
            """.stripIndent()
        consumerBuild << """\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            repositories {
                maven {
                    url '${path(repo)}'
                }
            }
            tasks.create('runtime') {
                doLast {
                    configurations.runtimeClasspath.files.each { print "\${it.name}," }
                }
            }
            tasks.create('compile') {
                doLast {
                    configurations.compileClasspath.files.each { print "\${it.name},"  }
                }
            }
            tasks.create('jenkinsRuntime') {
                doLast {
                    configurations.runtimeClasspathJenkins.files.findAll {
                        it.name.endsWith('.jpi') || it.name.endsWith('.hpi') || it.name.endsWith('.war')
                    }.each { print "\${it.name}," }
                }
            }
            tasks.create('jenkinsTestRuntime') {
                doLast {
                    configurations.testRuntimeClasspathJenkins.files.findAll {
                        it.name.endsWith('.jpi') || it.name.endsWith('.hpi') || it.name.startsWith('jenkins-war-')
                    }.each { print "\${it.name}," }
                }
            }
            """.stripIndent()
    }

    def 'publishes compile, runtime and jenkins runtime variant'() {
        given:
        producerBuild << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            dependencies {
                api 'org.jenkins-ci.plugins:credentials:1.9.4'
                implementation 'org.jenkins-ci.plugins:git:3.12.1'
                api 'org.apache.commons:commons-lang3:3.9'
                implementation 'org.apache.commons:commons-collections4:4.4'
            }
            """.stripIndent()
        publishProducer()

        when:
        consumerBuild << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            dependencies {
                implementation 'org:producer:1.0'
            }
        """

        then:
        resolveConsumer('compile') == JENKINS_CORE_DEPS + [
                'producer-1.0.jar',
                'credentials-1.9.4.jar',
                'commons-lang3-3.9.jar',
                'jcip-annotations-1.0.jar',
                'findbugs-annotations-1.3.9-1.jar',
        ] as Set

        resolveConsumer('runtime') == [
                'producer-1.0.jar',
                'git-3.12.1.jar',
                'commons-collections4-4.4.jar',
                'git-client-2.7.7.jar',
                'jsch-0.1.54.1.jar',
                'ssh-credentials-1.13.jar',
                'credentials-2.1.18.jar',
                'commons-lang3-3.9.jar',
                'joda-time-2.9.5.jar',
                'scm-api-2.6.3.jar',
                'workflow-scm-step-2.7.jar',
                'workflow-step-api-2.20.jar',
                'structs-1.19.jar',
                'matrix-project-1.7.1.jar',
                'mailer-1.18.jar',
                'antlr4-runtime-4.5.jar',
                'symbol-annotation-1.19.jar',
                'org.eclipse.jgit.http.server-4.5.5.201812240535-r.jar',
                'org.eclipse.jgit.http.apache-4.5.5.201812240535-r.jar',
                'org.eclipse.jgit-4.5.5.201812240535-r.jar',
                'apache-httpcomponents-client-4-api-4.5.3-2.0.jar',
                'display-url-api-0.2.jar',
                'junit-1.3.jar',
                'script-security-1.13.jar',
                'org.abego.treelayout.core-1.0.1.jar',
                'JavaEWAH-0.7.9.jar',
                'slf4j-api-1.7.2.jar',
                'jsch-0.1.54.jar',
                'httpmime-4.5.3.jar',
                'fluent-hc-4.5.3.jar',
                'httpclient-cache-4.5.3.jar',
                'httpclient-4.5.3.jar',
                'groovy-sandbox-1.8.jar',
                'httpcore-4.4.6.jar',
                'commons-logging-1.2.jar',
                'commons-codec-1.9.jar',
        ] as Set

        resolveConsumer('jenkinsRuntime') == [
                'producer-1.0.hpi',
                'git-3.12.1.hpi',
                'git-client-2.7.7.hpi',
                'jsch-0.1.54.1.hpi',
                'ssh-credentials-1.13.hpi',
                'credentials-2.1.18.hpi',
                'scm-api-2.6.3.hpi',
                'workflow-scm-step-2.7.hpi',
                'workflow-step-api-2.20.hpi',
                'structs-1.19.hpi',
                'matrix-project-1.7.1.hpi',
                'mailer-1.18.hpi',
                'apache-httpcomponents-client-4-api-4.5.3-2.0.hpi',
                'display-url-api-0.2.hpi',
                'junit-1.3.hpi',
                'script-security-1.13.hpi',
        ] as Set

        resolveConsumer('jenkinsTestRuntime') == [
                'producer-1.0.hpi',
                'git-3.12.1.hpi',
                'git-client-2.7.7.hpi',
                'jsch-0.1.54.1.hpi',
                'ssh-credentials-1.13.hpi',
                'credentials-2.1.18.hpi',
                'scm-api-2.6.3.hpi',
                'workflow-scm-step-2.7.hpi',
                'workflow-step-api-2.20.hpi',
                'structs-1.19.hpi',
                'matrix-project-1.7.1.hpi',
                'mailer-1.18.hpi',
                'apache-httpcomponents-client-4-api-4.5.3-2.0.hpi',
                'display-url-api-0.2.hpi',
                'junit-1.3.hpi',
                'script-security-1.13.hpi',
                'jenkins-war-2.401.3.war',
        ] as Set
    }

    def 'publishes feature variant with compile, runtime and jenkins runtime variant'() {
        given:
        producerBuild << """\
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            java {
                registerFeature('credentials') {
                    usingSourceSet(sourceSets.main)
                }
            }
            dependencies {
                implementation 'org.jenkins-ci.plugins:ant:1.2'
                implementation 'org.apache.commons:commons-lang3:3.9'
                credentialsImplementation 'org.jenkins-ci.plugins:credentials:1.9.4'
                credentialsImplementation 'org.apache.commons:commons-collections4:4.4'
            }
            """.stripIndent()
        publishProducer()

        when:
        consumerBuild << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            dependencies {
                implementation 'org:producer:1.0'
            }
        """

        then:
        resolveConsumer('compile') == JENKINS_CORE_DEPS + [ 'producer-1.0.jar' ] as Set
        resolveConsumer('runtime') == [
                'producer-1.0.jar',
                'ant-1.2.jar',
                'commons-lang3-3.9.jar' ] as Set
        resolveConsumer('jenkinsRuntime') == [ 'producer-1.0.hpi', 'ant-1.2.hpi' ] as Set
        resolveConsumer('jenkinsTestRuntime') == [
                'producer-1.0.hpi',
                'ant-1.2.hpi',
                'jenkins-war-2.401.3.war',
        ] as Set

        when:
        consumerBuild << """
            dependencies {
                implementation('org:producer:1.0') {
                    capabilities { requireCapability('org:producer-credentials') }
                }
            }
        """

        then:
        resolveConsumer('compile') == JENKINS_CORE_DEPS + [ 'producer-1.0.jar' ] as Set
        resolveConsumer('runtime') == [
                'producer-1.0.jar',
                'ant-1.2.jar',
                'commons-lang3-3.9.jar',
                'commons-collections4-4.4.jar',
                'credentials-1.9.4.jar',
                'jcip-annotations-1.0.jar',
                'findbugs-annotations-1.3.9-1.jar',
                'jsr305-1.3.9.jar' ] as Set
        resolveConsumer('jenkinsRuntime') ==
                [ 'producer-1.0.hpi', 'ant-1.2.hpi', 'credentials-1.9.4.hpi' ] as Set
        resolveConsumer('jenkinsTestRuntime') ==
                [ 'producer-1.0.hpi',
                  'ant-1.2.hpi',
                  'credentials-1.9.4.hpi',
                  'jenkins-war-2.401.3.war',
                ] as Set

        manifestEntry('consumer', 'Plugin-Dependencies') ==
                'producer:1.0,credentials:1.9.4'
        manifestEntry('producer', 'Plugin-Dependencies') ==
                'credentials:1.9.4;resolution:=optional,ant:1.2'
        packagedJars('consumer') ==
                [ 'consumer.jar' ] as Set
        packagedJars('producer') ==
                [ 'producer-1.0.jar', 'commons-lang3-3.9.jar', 'commons-collections4-4.4.jar' ] as Set
    }

    def 'has Jenkins core dependencies if a Jenkins version is configured'() {
        given:
        producerBuild << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            """.stripIndent()
        publishProducer()

        when:
        consumerBuild << """
            jenkinsPlugin {
                jenkinsVersion = '${TestSupport.RECENT_JENKINS_VERSION}'
            }
            dependencies {
                implementation 'org:producer:1.0'
            }
        """

        then:
        resolveConsumer('compile') == JENKINS_CORE_DEPS + [ 'producer-1.0.jar' ] as Set
        resolveConsumer('runtime') == [ 'producer-1.0.jar' ] as Set
        resolveConsumer('jenkinsRuntime') == [ 'producer-1.0.hpi' ] as Set
        resolveConsumer('jenkinsTestRuntime') == [ 'producer-1.0.hpi', 'jenkins-war-2.401.3.war' ] as Set
    }

    private void publishProducer() {
        gradleRunner().withProjectDir(producerBuild.parentFile).forwardOutput().
                withArguments('publishMavenJpiPublicationToTestRepository', '-s').build()
    }

    private Set<String> resolveConsumer(String resolveTask) {
        def result = gradleRunner().withProjectDir(consumerBuild.parentFile).forwardOutput().
                withArguments(resolveTask, '-q').build()
        result.output.split(',').findAll { !it.isBlank() }
    }

    private String manifestEntry(String jpiName, String key) {
        new JarInputStream(jpi(jpiName).newInputStream()).manifest
                .mainAttributes.find { it.key.toString() == key }.value.toString()
    }

    private Set<String> packagedJars(String jpiName) {
        def jpi = new JarInputStream(jpi(jpiName).newInputStream())
        Set<String> jars = []
        JarEntry entry
        while ((entry = jpi.nextJarEntry) != null) {
            def path = entry.name
            if (path.endsWith('.jar')) {
                jars.add(path.split('/')[2])
            }
        }
        jars
    }

    private File jpi(String name) {
        def buildRoot = inProjectDir(name)
        def jpi = new File(buildRoot, "build/libs/${name}.hpi")
        if (!existsRelativeToProjectDir("${name}/build/libs/${name}.hpi")) {
            gradleRunner().withProjectDir(buildRoot).forwardOutput().
                    withArguments('jpi').build()
        }
        jpi
    }

    private static String path(File file) {
        file.absolutePath.replaceAll('\\\\', '/')
    }

    private static final JENKINS_CORE_DEPS = [
        'access-modifier-annotation-1.31.jar',
        'annotation-indexer-1.17.jar',
        'ant-1.10.13.jar',
        'ant-launcher-1.10.13.jar',
        'antlr4-runtime-4.12.0.jar',
        'args4j-2.33.jar',
        'asm-9.5.jar',
        'asm-analysis-9.5.jar',
        'asm-commons-9.5.jar',
        'asm-tree-9.5.jar',
        'asm-util-9.5.jar',
        'bridge-method-annotation-1.26.jar',
        'checker-qual-3.12.0.jar',
        'cli-2.401.3.jar',
        'commons-beanutils-1.9.4.jar',
        'commons-codec-1.15.jar',
        'commons-collections-3.2.2.jar',
        'commons-compress-1.23.0.jar',
        'commons-discovery-0.5.jar',
        'commons-fileupload-1.5.jar',
        'commons-io-2.11.0.jar',
        'commons-jelly-1.1-jenkins-20230124.jar',
        'commons-jelly-tags-define-1.1-jenkins-20230124.jar',
        'commons-jelly-tags-fmt-1.0.jar',
        'commons-jelly-tags-xml-1.1.jar',
        'commons-jexl-1.1-jenkins-20111212.jar',
        'commons-lang-2.6.jar',
        'commons-logging-1.2.jar',
        'crypto-util-1.8.jar',
        'dom4j-2.1.4.jar',
        'embedded_su4j-1.1.jar',
        'error_prone_annotations-2.18.0.jar',
        'ezmorph-1.0.6.jar',
        'failureaccess-1.0.1.jar',
        'groovy-all-2.4.21.jar',
        'guava-31.1-jre.jar',
        'guice-6.0.0.jar',
        'j-interop-2.0.8-kohsuke-1.jar',
        'j-interopdeps-2.0.8-kohsuke-1.jar',
        'j2objc-annotations-1.3.jar',
        'jakarta.annotation-api-2.1.1.jar',
        'jakarta.inject-api-2.0.1.jar',
        'jakarta.servlet.jsp.jstl-api-1.2.7.jar',
        'jansi-1.11.jar',
        'javax.annotation-api-1.3.2.jar',
        'javax.inject-1.jar',
        'javax.servlet-api-3.1.0.jar',
        'jaxen-2.0.0.jar',
        'jbcrypt-1.0.0.jar',
        'jcifs-1.3.18-kohsuke-1.jar',
        'jcip-annotations-1.0.jar',
        'jcl-over-slf4j-2.0.7.jar',
        'jcommon-1.0.23.jar',
        'jenkins-core-2.401.3.jar',
        'jenkins-stapler-support-1.1.jar',
        'jfreechart-1.0.19.jar',
        'jline-2.14.6.jar',
        'jna-5.13.0.jar',
        'json-lib-2.4-jenkins-3.jar',
        'jsr305-3.0.2.jar',
        'jzlib-1.1.3-kohsuke-1.jar',
        'kxml2-2.3.0.jar',
        'listenablefuture-9999.0-empty-to-avoid-conflict-with-guava.jar',
        'localizer-1.31.jar',
        'log4j-over-slf4j-2.0.7.jar',
        'memory-monitor-1.12.jar',
        'mxparser-1.2.2.jar',
        'producer-1.0.jar',
        'relaxngDatatype-20020414.jar',
        'remoting-3107.v665000b_51092.jar',
        'robust-http-client-1.2.jar',
        'sezpoz-1.13.jar',
        'slf4j-api-2.0.7.jar',
        'spotbugs-annotations-4.7.3.jar',
        'spring-aop-5.3.27.jar',
        'spring-beans-5.3.27.jar',
        'spring-context-5.3.27.jar',
        'spring-core-5.3.27.jar',
        'spring-expression-5.3.27.jar',
        'spring-security-core-5.8.2.jar',
        'spring-security-crypto-5.8.2.jar',
        'spring-security-web-5.8.2.jar',
        'spring-web-5.3.27.jar',
        'stapler-1777.v7c6fe6d54a_0c.jar',
        'stapler-adjunct-codemirror-1.3.jar',
        'stapler-adjunct-timeline-1.5.jar',
        'stapler-groovy-1777.v7c6fe6d54a_0c.jar',
        'stapler-jelly-1777.v7c6fe6d54a_0c.jar',
        'symbol-annotation-1.24.jar',
        'task-reactor-1.8.jar',
        'tiger-types-2.2.jar',
        'txw2-20110809.jar',
        'version-number-1.11.jar',
        'websocket-spi-2.401.3.jar',
        'windows-package-checker-1.2.jar',
        'winp-1.30.jar',
        'xpp3-1.1.4c.jar',
        'xstream-1.4.20.jar',
    ] as Set
}
