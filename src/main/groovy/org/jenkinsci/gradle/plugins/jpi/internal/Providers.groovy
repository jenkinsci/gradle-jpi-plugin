package org.jenkinsci.gradle.plugins.jpi.internal

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetOutput
import org.jenkinsci.gradle.plugins.jpi.DependencyAnalysis

import java.util.concurrent.Callable

import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME

@CompileStatic
class Providers {
    static Provider<String> groupIdFrom(Project p) {
        p.provider(new Callable<String>() {
            @Override
            String call() throws Exception {
                p.group
            }
        })
    }

    static Provider<String> versionFrom(Project p) {
        p.provider(new Callable<String>() {
            @Override
            String call() throws Exception {
                p.version
            }
        })
    }

    static Provider<String> minimumJavaVersionFrom(Project p) {
        p.provider(new Callable<String>() {
            @Override
            String call() throws Exception {
                p.convention.getPlugin(JavaPluginConvention).targetCompatibility.toString()
            }
        })
    }

    static Provider<String> pluginDependenciesFrom(DependencyAnalysis dependencyAnalysis, Project p) {
        p.provider(new Callable<String>() {
            @Override
            String call() throws Exception {
                dependencyAnalysis.analyse().manifestPluginDependencies
            }
        })
    }

    static Provider<Configuration> libraryDependenciesFrom(DependencyAnalysis dependencyAnalysis, Project p) {
        p.provider(new Callable<Configuration>() {
            @Override
            Configuration call() throws Exception {
                dependencyAnalysis.allLibraryDependencies
            }
        })
    }

    static Provider<SourceSetOutput> mainSourceSetOutputFrom(Project p) {
        p.provider(new Callable<SourceSetOutput>() {
            @Override
            SourceSetOutput call() throws Exception {
                p.convention.getPlugin(JavaPluginConvention).sourceSets.getByName(MAIN_SOURCE_SET_NAME).output
            }
        })
    }
}
