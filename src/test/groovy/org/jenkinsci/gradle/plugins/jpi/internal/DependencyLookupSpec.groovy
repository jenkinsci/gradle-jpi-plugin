package org.jenkinsci.gradle.plugins.jpi.internal

import org.jenkinsci.gradle.plugins.jpi.TestSupport
import spock.lang.Specification
import spock.lang.Unroll

class DependencyLookupSpec extends Specification {
    private static final MavenDependency JCIP_ANNOTATIONS = new MavenDependency('net.jcip:jcip-annotations:1.0')
    private static final MavenDependency SERVLET_5_0 = new MavenDependency('jakarta.servlet:jakarta.servlet-api:5.0.0')
    private static final MavenDependency SERVLET_3_1 = new MavenDependency('javax.servlet:javax.servlet-api:3.1.0')
    private static final MavenDependency SERVLET_2_4 = new MavenDependency('javax.servlet:servlet-api:2.4')
    private static final MavenDependency GOOGLE_FINDBUGS = new MavenDependency('com.google.code.findbugs:annotations:3.0.0')
    private static final MavenDependency FINDBUGS_1 = new MavenDependency('findbugs:annotations:1.0.0')
    private static final MavenDependency SPOTBUGS = new MavenDependency('com.github.spotbugs:spotbugs-annotations')
    private static final MavenDependency JENKINS_TEST_HARNESS = jenkinsTestHarness('2112.ve584e0edc63b_')
    private final DependencyLookup lookup = new DependencyLookup()

    @Unroll
    def 'should get annotationProcessor dependencies for #version'(String version, Set<DependencyFactory> expected) {
        when:
        def actual = this.lookup.find('annotationProcessor', version)

        then:
        actual == expected

        where:
        version                         | expected
        '2.0'                           | [jenkinsCore('2.0'), SERVLET_3_1] as Set
        '2.222.3'                       | [jenkinsBom('2.222.3'), jenkinsCore('2.222.3'), SERVLET_3_1] as Set
        '2.361.2-rc32710.c1a_5e8c179f6' | [jenkinsBom('2.361.2-rc32710.c1a_5e8c179f6'), jenkinsCore('2.361.2-rc32710.c1a_5e8c179f6'), SERVLET_3_1] as Set
        '2.369-rc32854.076293e36922'    | [jenkinsBom('2.369-rc32854.076293e36922'), jenkinsCore('2.369-rc32854.076293e36922'), SERVLET_3_1] as Set
        '2.475'                         | [jenkinsBom('2.475'), jenkinsCore('2.475'), SERVLET_5_0] as Set
        '2.479.2'                       | [jenkinsBom('2.479.2'), jenkinsCore('2.479.2'), SERVLET_5_0] as Set
    }

    @Unroll
    def 'should get compileOnly dependencies for #version'(String version, Set<DependencyFactory> expected) {
        when:
        def actual = lookup.find('compileOnly', version)

        then:
        actual == expected

        where:
        version                         | expected
        '1.617'                         | [jenkinsCore('1.617'), FINDBUGS_1, SERVLET_2_4] as Set
        '1.618'                         | [jenkinsCore('1.618'), GOOGLE_FINDBUGS, SERVLET_2_4] as Set
        '2.0'                           | [jenkinsCore('2.0'), GOOGLE_FINDBUGS, SERVLET_3_1] as Set
        '2.222.3'                       | [jenkinsBom('2.222.3'), jenkinsCore('2.222.3'), SPOTBUGS, SERVLET_3_1] as Set
        '2.361.2-rc32710.c1a_5e8c179f6' | [jenkinsBom('2.361.2-rc32710.c1a_5e8c179f6'), jenkinsCore('2.361.2-rc32710.c1a_5e8c179f6'), SPOTBUGS, SERVLET_3_1] as Set
        '2.369-rc32854.076293e36922'    | [jenkinsBom('2.369-rc32854.076293e36922'), jenkinsCore('2.369-rc32854.076293e36922'), SPOTBUGS, SERVLET_3_1] as Set
    }

    @Unroll
    def 'should get testImplementation dependencies for #version'(String version, Set<DependencyFactory> expected) {
        when:
        def actual = lookup.find('testImplementation', version)

        then:
        actual == expected

        where:
        version                         | expected
        '1.504'                         | [jenkinsCore('1.504'), jenkinsTestHarness('1.504'), new MavenDependency('junit:junit-dep:4.10')] as Set
        '1.532'                         | [jenkinsCore('1.532'), jenkinsTestHarness('1.532')] as Set
        '1.644'                         | [jenkinsCore('1.644'), jenkinsTestHarness('1.644')] as Set
        '1.645'                         | [jenkinsCore('1.645'), jenkinsTestHarness('2.0')] as Set
        '2.64'                          | [jenkinsCore('2.64'), JENKINS_TEST_HARNESS] as Set
        '2.222.3'                       | [jenkinsBom('2.222.3'), jenkinsCore('2.222.3'), JENKINS_TEST_HARNESS] as Set
        '2.361.2-rc32710.c1a_5e8c179f6' | [jenkinsBom('2.361.2-rc32710.c1a_5e8c179f6'), jenkinsCore('2.361.2-rc32710.c1a_5e8c179f6'), JENKINS_TEST_HARNESS] as Set
        '2.369-rc32854.076293e36922'    | [jenkinsBom('2.369-rc32854.076293e36922'), jenkinsCore('2.369-rc32854.076293e36922'), JENKINS_TEST_HARNESS] as Set
    }

    @Unroll
    def 'should get testCompileOnly dependencies for #version'(String version, Set<DependencyFactory> expected) {
        when:
        def actual = lookup.find('testCompileOnly', version)

        then:
        actual == expected

        where:
        version                         | expected
        '1.617'                         | [FINDBUGS_1, JCIP_ANNOTATIONS, SERVLET_2_4] as Set
        '2.150.3'                       | [GOOGLE_FINDBUGS, JCIP_ANNOTATIONS, SERVLET_3_1] as Set
        '2.222.3'                       | [SPOTBUGS, JCIP_ANNOTATIONS, SERVLET_3_1] as Set
        '2.361.2-rc32710.c1a_5e8c179f6' | [SPOTBUGS, JCIP_ANNOTATIONS, SERVLET_3_1] as Set
        '2.369-rc32854.076293e36922'    | [SPOTBUGS, JCIP_ANNOTATIONS, SERVLET_3_1] as Set
        '2.475'                         | [SPOTBUGS, JCIP_ANNOTATIONS, SERVLET_5_0] as Set
    }

    @Unroll
    def 'should get declaredJenkinsWar dependencies for #version'(String version) {
        when:
        def actual = lookup.find('declaredJenkinsWar', version)

        then:
        actual == ([
                new MavenDependency("org.jenkins-ci.main:jenkins-war:${version}"),
        ] as Set)

        where:
        version << [TestSupport.RECENT_JENKINS_VERSION, '2.361.2-rc32710.c1a_5e8c179f6', '2.369-rc32854.076293e36922']
    }

    @Unroll
    def 'should get generatedJenkinsTestImplementation dependencies for #version'(String version, Set<DependencyFactory> expected) {
        when:
        def actual = lookup.find('generatedJenkinsTestImplementation', version)

        then:
        actual == expected

        where:
        version                         | expected
        '2.150.3'                       | [jenkinsCore('2.150.3'), JENKINS_TEST_HARNESS] as Set
        '2.222.3'                       | [jenkinsCore('2.222.3'), jenkinsBom('2.222.3'), JENKINS_TEST_HARNESS] as Set
        '2.361.2-rc32710.c1a_5e8c179f6' | [jenkinsCore('2.361.2-rc32710.c1a_5e8c179f6'), jenkinsBom('2.361.2-rc32710.c1a_5e8c179f6'), JENKINS_TEST_HARNESS] as Set
        '2.369-rc32854.076293e36922'    | [jenkinsCore('2.369-rc32854.076293e36922'), jenkinsBom('2.369-rc32854.076293e36922'), JENKINS_TEST_HARNESS] as Set
    }

    private static BomDependency jenkinsBom(String version) {
        new BomDependency('org.jenkins-ci.main:jenkins-bom:' + version)
    }

    private static MavenDependency jenkinsCore(String version) {
        new MavenDependency('org.jenkins-ci.main:jenkins-core:' + version)
    }

    private static MavenDependency jenkinsTestHarness(String version) {
        new MavenDependency('org.jenkins-ci.main:jenkins-test-harness:' + version)
    }
}
