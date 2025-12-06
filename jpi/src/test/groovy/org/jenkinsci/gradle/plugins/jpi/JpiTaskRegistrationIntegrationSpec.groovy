package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testkit.runner.BuildResult

class JpiTaskRegistrationIntegrationSpec extends IntegrationSpec {
    private final String projectName = TestDataGenerator.generateName()
    private File settings
    private File build
    def setup() {
        settings = touchInProjectDir('settings.gradle')
        settings << """rootProject.name = \"$projectName\""""
        build = touchInProjectDir('build.gradle')
        build << '''\
            plugins {
                id 'org.jenkins-ci.jpi'
            }
            '''.stripIndent()
    }

    def 'JpiPlugin is applied when some other plugin modifies War tasks'() {
        given:
        build.text = '''
            import jenkinsci.ConflictPlugin

            allprojects {
                apply plugin: ConflictPlugin
                apply plugin: 'java'
            }
            '''.stripIndent()

        inProjectDir('submodule').mkdirs()

        File submoduleBuildFile = inProjectDir('submodule/build.gradle')
        submoduleBuildFile.text = '''
            plugins {
                id 'org.jenkins-ci.jpi'
            }
        '''

        settings.text = '''
            include 'submodule'
        '''

        inProjectDir('buildSrc/src/main/groovy/jenkinsci').mkdirs()
        File conflictPlugin = inProjectDir('buildSrc/src/main/groovy/jenkinsci/ConflictPlugin.groovy')
        conflictPlugin.text = '''
            package jenkinsci

            import org.gradle.api.Plugin
            import org.gradle.api.Project
            import org.gradle.api.tasks.bundling.War

            class ConflictPlugin implements Plugin<Project> {
                @Override
                void apply(Project project) {
                    project.tasks.withType(War) { War war ->
                        war.archiveBaseName.set('do-something')
                    }
                }
            }
        '''

        File buildSrcFile = inProjectDir('buildSrc/build.groovy')
        buildSrcFile.text = '''
            plugins {
                id 'groovy'
            }
        '''

        when:
        BuildResult result = gradleRunner()
                .withArguments('buildEnvironment')
                .build()

        then:
        !result.output.contains('Plugin with type class org.jenkinsci.gradle.plugins.jpi.JpiPlugin has not been used')
    }

}
