package org.jenkinsci.gradle.plugins.accmod

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

open class AccessModifierPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply<JavaLibraryPlugin>()
            val library = dependencies.create("org.kohsuke:access-modifier-checker:1.33")
            val mavenLog = dependencies.create("org.apache.maven:maven-plugin-api:2.0.1").apply {
                because("Requires org.apache.maven.plugin.logging.Log but missing dependency")
            }
            val jenkinsAccessModifier = configurations.create("jenkinsAccessModifier") {
                attributes {
                    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
                }
                isVisible = false
                isCanBeConsumed = false
                isCanBeResolved = true
                withDependencies {
                    add(library)
                    add(mavenLog)
                }
            }

            val propertyProvider = provider(PrefixedPropertiesProvider(this, CheckAccessModifierTask.PREFIX))
            val checkAccessModifier = tasks.register<CheckAccessModifierTask>(CheckAccessModifierTask.NAME) {
                group = "Verification"
                description = "Checks if Jenkins restricted apis are used (https://tiny.cc/jenkins-restricted)."
                val dirs = project.extensions.getByType<SourceSetContainer>()["main"].output.classesDirs
                accessModifierClasspath.from(jenkinsAccessModifier)
                accessModifierProperties.set(propertyProvider)
                compileClasspath.from(target.configurations.getByName("compileClasspath"))
                compilationDirs.from(dirs)
                ignoreFailures.convention(true)
                outputDirectory.set(project.layout.buildDirectory.dir("access-modifier"))
                outputs.upToDateWhen { !ignoreFailures.get() }
            }
            tasks.named("check").configure {
                dependsOn(checkAccessModifier)
            }
        }
    }
}
