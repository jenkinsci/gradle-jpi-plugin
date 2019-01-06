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

import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.util.GFileUtils

import java.util.jar.JarFile
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.TaskAction

import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.JENKINS_SERVER_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.PLUGINS_DEPENDENCY_CONFIGURATION_NAME

/**
 * Task that starts Jenkins in place with the current plugin.
 *
 * @author Kohsuke Kawaguchi
 */
class ServerTask extends DefaultTask {
    private static final String HTTP_PORT = 'jenkins.httpPort'

    @TaskAction
    def start() {
        def c = project.configurations.getByName(JpiPlugin.WAR_DEPENDENCY_CONFIGURATION_NAME)
        def files = c.resolve()
        if (files.isEmpty()) {
            throw new GradleException('No jenkins.war dependency is specified')
        }
        File war = files.toArray()[0]

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
        Set<ResolvedArtifact> artifacts = []
        [PLUGINS_DEPENDENCY_CONFIGURATION_NAME, JENKINS_SERVER_DEPENDENCY_CONFIGURATION_NAME].each { String name ->
            artifacts += project.configurations[name].resolvedConfiguration.resolvedArtifacts
        }

        // copy the resolved HPI/JPI files to the plugins directory
        def workDir = project.extensions.getByType(JpiExtension).workDir
        artifacts.findAll { it.extension in ['hpi', 'jpi'] }.each {
            GFileUtils.copyFile(it.file, new File(workDir, "plugins/${it.name}.${it.extension}"))
        }
    }

    private static void setSystemPropertyIfEmpty(String name, String value) {
        if (!System.getProperty(name)) {
            System.setProperty(name, value)
        }
    }

    public static final String TASK_NAME = 'server'
}
