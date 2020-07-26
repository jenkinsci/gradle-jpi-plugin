package org.jenkinsci.gradle.plugins.jpi.internal

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.provider.Provider
import org.jenkinsci.gradle.plugins.jpi.DependencyAnalysis

import java.util.concurrent.Callable

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
}
