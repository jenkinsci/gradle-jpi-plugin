package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

import java.util.jar.Manifest

class JpiHplManifestSpec extends Specification {
    Project project = ProjectBuilder.builder().build()

    def 'basics'() {
        setup:
        project.with {
            apply plugin: 'jpi'
        }
        def libraries = [
                new File(project.projectDir, 'src/main/resources'),
                new File(project.buildDir, 'classes/main'),
                new File(project.buildDir, 'resources/main'),
        ]
        libraries*.mkdirs()

        when:
        Manifest manifest = new JpiHplManifest(project).toJavaManifest()

        then:
        manifest.mainAttributes.getValue('Resource-Path') == new File(project.projectDir, 'src/main/webapp').path
        manifest.mainAttributes.getValue('Libraries') == libraries*.path.join(',')
    }

    def 'non-existing libraries are ignored'() {
        setup:
        project.with {
            apply plugin: 'jpi'
        }

        when:
        Manifest manifest = new JpiHplManifest(project).toJavaManifest()

        then:
        manifest.mainAttributes.getValue('Resource-Path') == new File(project.projectDir, 'src/main/webapp').path
        manifest.mainAttributes.getValue('Libraries') == ''
    }
}
