package org.jenkinsci.gradle.plugins.jpi

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

/**
 * @author Kiyotaka Oku
 */
class JpiManifestSpec extends Specification {

    def project

    def setup() {
        project = ProjectBuilder.builder().build()
    }

    def "Plugin-Developers"() {
        given:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                developers closure
            }
        }

        when:
        def manifest = new JpiManifest(project)

        then:
        manifest['Plugin-Developers'] == expected

        where:
        [closure, expected] << [
            [
                {
                    developer {
                        id 'foo'
                        name 'Foo'
                        email 'foo@example.com'
                    }
                },
                'Foo:foo:foo@example.com'
            ],
            [
                {
                    developer {
                        id 'foo'
                        name 'Foo'
                    }
                },
                'Foo:foo:'
            ],
            [
                {
                    developer {
                        id 'foo'
                        name 'Foo'
                        email 'foo@example.com'
                    }
                    developer {
                        id 'bar'
                        name 'Bar'
                        email 'bar@example.com'
                    }
                },
                'Foo:foo:foo@example.com,Bar:bar:bar@example.com'
            ]
        ]
    }

    def "PluginFirstClassLoader"() {
        given:
        project.with {
            apply plugin: 'jpi'
            jenkinsPlugin {
                pluginFirstClassLoader = true
            }
        }

        when:
        def manifest = new JpiManifest(project)

        then:
        manifest['PluginFirstClassLoader'] == 'true'
    }
}
