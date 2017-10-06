package org.jenkinsci.gradle.plugins.jpi

import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification
import spock.lang.Unroll

import java.util.jar.JarFile
import java.util.jar.Manifest

class JpiManifestSpec extends Specification {
    public static final String PROJECT = """
plugins {
    id 'org.jenkins-ci.jpi'
}

jenkinsPlugin {
    coreVersion = '1.642'
}

version = '1.2'"""

    Project project = ProjectBuilder.builder().build()

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    def 'basics'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('basics.mf')
    }

    def 'JAR contains manifest'() {
        given:
        temporaryFolder.newFolder('test', 'src', 'main', 'java')
        temporaryFolder.newFile('test/build.gradle') << PROJECT
        temporaryFolder.newFile('test/src/main/java/TestPlugin.java') << 'class TestPlugin extends hudson.Plugin {}'

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File(temporaryFolder.root, 'test'))
                .withPluginClasspath()
                .withArguments('jar')
                .build()

        then:
        result.task(':jar').outcome == TaskOutcome.SUCCESS
        File generatedJarFile = new File(temporaryFolder.root, 'test/build/libs/test-1.2.jar')
        generatedJarFile.exists()
        new JarFile(generatedJarFile).manifest.mainAttributes == readManifest('test.mf').mainAttributes
    }

    def 'JPI contains manifest'() {
        given:
        temporaryFolder.newFolder('test', 'src', 'main', 'java')
        temporaryFolder.newFile('test/build.gradle') << PROJECT
        temporaryFolder.newFile('test/src/main/java/TestPlugin.java') << 'class TestPlugin extends hudson.Plugin {}'

        when:
        BuildResult result = GradleRunner.create()
                .withProjectDir(new File(temporaryFolder.root, 'test'))
                .withPluginClasspath()
                .withArguments('jpi')
                .build()

        then:
        result.task(':jpi').outcome == TaskOutcome.SUCCESS
        File generatedJarFile = new File(temporaryFolder.root, 'test/build/libs/test.hpi')
        generatedJarFile.exists()
        new JarFile(generatedJarFile).manifest.mainAttributes == readManifest('test.mf').mainAttributes
    }

    def 'plugin class'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
        }
        File directory = new File(project.tasks.compileJava.destinationDir as File, 'META-INF/services')
        directory.mkdirs()
        new File(directory, 'hudson.Plugin').write('org.example.PluginImpl')

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('plugin-class.mf')
    }

    def 'no version'() {
        setup:
        project.with {
            apply plugin: 'jpi'
        }

        when:
        JpiManifest manifest = new JpiManifest(project)

        then:
        manifest.mainAttributes.getValue('Plugin-Version') =~
                /1.0-SNAPSHOT \(private-\d{2}\/\d{2}\/\d{4} \d{2}:\d{2}-.+\)/
    }

    def 'dependency'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.0'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
            dependencies {
                jenkinsPlugins 'org.jenkins-ci.plugins:ant:1.2'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('dependency.mf')
    }

    def 'dependencies'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.0'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
            dependencies {
                jenkinsPlugins 'org.jenkinsci.plugins:git:1.1.15'
                jenkinsPlugins 'org.jenkins-ci.plugins:ant:1.2'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('dependencies.mf')
    }

    def 'optional dependency'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.0'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
            dependencies {
                optionalJenkinsPlugins 'org.jenkins-ci.plugins:ant:1.2'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('optional-dependency.mf')
    }

    def 'optional dependencies'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.0'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
            dependencies {
                optionalJenkinsPlugins 'org.jenkinsci.plugins:git:1.1.15'
                optionalJenkinsPlugins 'org.jenkins-ci.plugins:ant:1.2'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('optional-dependencies.mf')
    }

    def 'complex dependencies'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.0'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
            dependencies {
                jenkinsPlugins 'org.jenkinsci.plugins:git:1.1.15'
                jenkinsPlugins 'org.jenkins-ci.plugins:ant:1.2'
                optionalJenkinsPlugins 'org.jenkins-ci.plugins:cloudbees-folder:4.2'
                optionalJenkinsPlugins 'org.jenkins-ci.plugins:credentials:1.9.4'
            }
        }
        (project as ProjectInternal).evaluate()

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('complex-dependencies.mf')
    }

    def 'compatible since version'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
                compatibleSinceVersion = '1.1'
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('compatible-since-version.mf')
    }

    def 'mask classes'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
                maskClasses = 'org.example.test'
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('mask-classes.mf')
    }

    def 'plugin first class loader'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
                pluginFirstClassLoader = true
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('plugin-first-class-loader.mf')
    }

    def 'sandbox status'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
                sandboxStatus = true
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('sandbox-status.mf')
    }

    def 'plugin developer'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
                developers {
                    developer {
                        id 'abayer'
                        name 'Andrew Bayer'
                        email 'andrew.bayer@gmail.com'
                    }
                }
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('plugin-developer.mf')
    }

    def 'plugin developers'() {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
                developers {
                    developer {
                        id 'abayer'
                        email 'andrew.bayer@gmail.com'
                    }
                    developer {
                        id 'kohsuke'
                        name 'Kohsuke Kawaguchi'
                    }
                }
            }
        }

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest('plugin-developers.mf')
    }

    @Unroll
    def 'support dynamic loading #value'(String value) {
        setup:
        project.with {
            apply plugin: 'jpi'
            group = 'org.example'
            version = '1.2'
            jenkinsPlugin {
                coreVersion = '1.509.3'
            }
        }
        byte[] index = JpiManifestSpec.getResourceAsStream("support-dynamic-loading/${value}/hudson.Extension").bytes
        File directory = new File(project.tasks.compileJava.destinationDir as File, 'META-INF/annotations')
        directory.mkdirs()
        new File(directory, 'hudson.Extension').bytes = index

        when:
        Manifest manifest = new JpiManifest(project)

        then:
        manifest == readManifest("support-dynamic-loading-${value}.mf")

        where:
        value << ['yes', 'maybe', 'no']
    }

    private static Manifest readManifest(String fileName) {
        new Manifest(JpiManifestSpec.getResourceAsStream(fileName))
    }
}
