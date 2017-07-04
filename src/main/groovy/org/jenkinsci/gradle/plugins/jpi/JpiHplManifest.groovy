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

import org.gradle.api.Project
import org.gradle.api.java.archives.Attributes
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.WarPluginConvention
import org.gradle.api.tasks.bundling.War

/**
 * @author Kohsuke Kawaguchi
 */
class JpiHplManifest extends JpiManifest {
    JpiHplManifest(Project project) {
        super(project)
    }

    @Override
    Attributes getAttributes() {
        Attributes mainAttributes = super.attributes

        def conv = project.extensions.getByType(JpiExtension)
        War war = project.tasks.getByName(WarPlugin.WAR_TASK_NAME) as War

        // src/main/webApp
        def warconv = project.convention.getPlugin(WarPluginConvention)
        mainAttributes['Resource-Path'] = warconv.webAppDir.absolutePath

        // add resource directories directly so that we can pick up the source, then add all the jars and class path
        Set<File> libraries = conv.mainSourceTree().resources.srcDirs + war.classpath.files
        mainAttributes['Libraries'] = libraries.findAll { it.exists() }.join(',')

        mainAttributes
    }
}
