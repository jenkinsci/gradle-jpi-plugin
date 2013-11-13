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

import java.util.jar.JarFile
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction

/**
 * Task that starts Jenkins in place with the current plugin.
 *
 * @author Kohsuke Kawaguchi
 */
class ServerTask extends DefaultTask {

    @TaskAction
    def start() {
        def c = project.configurations.getByName(JpiPlugin.WAR_DEPENDENCY_CONFIGURATION_NAME)
        def files = c.resolve();
        if (files.isEmpty())
            throw new Error("No jenkins.war dependency is specified");
        File war = files.toArray()[0]

        generateHpl();

        def conv = project.extensions.getByType(JpiExtension)
        System.setProperty("JENKINS_HOME",conv.workDir.getAbsolutePath())
        System.setProperty("stapler.trace","true")
        System.setProperty("debug.YUI","true")
        System.setProperty("stapler.jelly.noCache","true")

        def cl = new URLClassLoader([war.toURI().toURL()] as URL[])
        def mainClass = new JarFile(war).getManifest().mainAttributes.getValue("Main-Class")
        cl.loadClass(mainClass).main();
        
        // make the thread hang
        synchronized (this) { wait(); }
    }

    void generateHpl() {
        def m = new JpiHplManifest(project)
        def conv = project.extensions.getByType(JpiExtension)

        def hpl = new File(conv.workDir, "plugins/${conv.shortName}.hpl")
        hpl.parentFile.mkdirs()
        m.writeTo(hpl)
    }

    public static final String TASK_NAME = "server";
}
