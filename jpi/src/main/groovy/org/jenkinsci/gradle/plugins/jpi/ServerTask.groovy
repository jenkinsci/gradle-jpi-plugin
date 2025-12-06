/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jenkinsci.gradle.plugins.jpi

import org.gradle.internal.deprecation.DeprecationLogger

import java.util.jar.JarFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction
import org.gradle.util.GFileUtils

import static JpiPlugin.SERVER_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME

/**
 * Task that starts Jenkins in place with the current plugin.
 *
 * This task is no longer wired to the 'server' task. It has been replaced
 * by three tasks for better caching.
 *
 * @see org.jenkinsci.gradle.plugins.jpi.server.GenerateHplTask
 * @see org.jenkinsci.gradle.plugins.jpi.server.InstallJenkinsServerPluginsTask
 * @see org.jenkinsci.gradle.plugins.jpi.server.JenkinsServerTask
 * @author Kohsuke Kawaguchi
 * @deprecated To be removed in 1.0.0
 */
@Deprecated
class ServerTask extends DefaultTask {
    public static final String TASK_NAME = 'server'

    private static final String HTTP_PORT = 'jenkins.httpPort'

    @TaskAction
    def start() {
        def jenkinsWar = project.extensions.getByType(JpiExtension).jenkinsWarCoordinates
        Set<File> files = []
        if (jenkinsWar) {
            def c = project.configurations.detachedConfiguration(project.dependencies.create(jenkinsWar))
            files = c.resolve()
        }
        if (files.isEmpty()) {
            throw new GradleException('No jenkins.war dependency is specified')
        }
        File war = files.first()

        generateHpl()
        copyPluginDependencies()

        def conv = project.extensions.getByType(JpiExtension)
        System.setProperty('JENKINS_HOME', conv.workDir.absolutePath)
        setSystemPropertyIfEmpty('stapler.trace', 'true')
        setSystemPropertyIfEmpty('stapler.jelly.noCache', 'true')
        setSystemPropertyIfEmpty('debug.YUI', 'true')
        setSystemPropertyIfEmpty('hudson.Main.development', 'true')

        List<String> args = []
        String port = project.properties[HTTP_PORT] ?: System.properties[HTTP_PORT]
        if (port) {
            args << "--httpPort=${port}"
        }

        def cl = new URLClassLoader([war.toURI().toURL()] as URL[])
        def mainClass = new JarFile(war).manifest.mainAttributes.getValue('Main-Class')
        cl.loadClass(mainClass).main(args as String[])

        // make the thread hang
        Thread.currentThread().join()
    }

    void generateHpl() {
        def m = new JpiHplManifest(project)
        def conv = project.extensions.getByType(JpiExtension)

        def hpl = new File(conv.workDir, "plugins/${conv.shortName}.hpl")
        hpl.parentFile.mkdirs()
        hpl.withOutputStream { m.write(it) }
    }

    private copyPluginDependencies() {
        def artifacts = project.configurations[SERVER_JENKINS_RUNTIME_CLASSPATH_CONFIGURATION_NAME].
                resolvedConfiguration.resolvedArtifacts

        // copy the resolved HPI/JPI files to the plugins directory
        def workDir = project.extensions.getByType(JpiExtension).workDir
        artifacts.findAll { it.extension in ['hpi', 'jpi'] }.each {
            // Disable the deprecation since this artifact is already deprecated anyway
            DeprecationLogger.whileDisabled {
                GFileUtils.copyFile(it.file, new File(workDir, "plugins/${it.name}.${it.extension}"))
            }
        }
    }

    private static void setSystemPropertyIfEmpty(String name, String value) {
        if (!System.getProperty(name)) {
            System.setProperty(name, value)
        }
    }
}
