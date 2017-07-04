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

import hudson.Extension
import jenkins.YesNoMaybe
import net.java.sezpoz.Index
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.java.archives.Attributes
import org.gradle.api.java.archives.Manifest
import org.gradle.api.java.archives.ManifestException
import org.gradle.api.plugins.JavaPluginConvention

import java.text.SimpleDateFormat

import static java.util.jar.Attributes.Name.MANIFEST_VERSION
import static org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME
import static org.jenkinsci.gradle.plugins.jpi.JpiPlugin.PLUGINS_DEPENDENCY_CONFIGURATION_NAME

/**
 * Encapsulates the Jenkins plugin manifest and its generation.
 *
 * @author Kohsuke Kawaguchi
 */
class JpiManifest implements Manifest {
    protected final Project project

    JpiManifest(Project project) {
        this.project = project
    }

    @Override
    Attributes getAttributes() {
        def conv = project.extensions.getByType(JpiExtension)
        def javaPluginConvention = project.convention.getPlugin(JavaPluginConvention)
        def classDir = javaPluginConvention.sourceSets.getByName(MAIN_SOURCE_SET_NAME).output.classesDir

        Attributes mainAttributes = new JpiAttributes()

        mainAttributes[MANIFEST_VERSION.toString()] = '1.0'

        File pluginImpl = new File(classDir, 'META-INF/services/hudson.Plugin')
        if (pluginImpl.exists()) {
            mainAttributes['Plugin-Class'] = pluginImpl.readLines('UTF-8')[0]
        }

        mainAttributes['Group-Id'] = project.group.toString()
        mainAttributes['Short-Name'] = conv.shortName
        mainAttributes['Long-Name'] = conv.displayName
        mainAttributes['Url'] = conv.url
        mainAttributes['Compatible-Since-Version'] = conv.compatibleSinceVersion
        if (conv.sandboxStatus) {
            mainAttributes['Sandbox-Status'] = conv.sandboxStatus.toString()
        }
        mainAttributes['Extension-Name'] = conv.shortName

        def version = project.version
        if (version == Project.DEFAULT_VERSION) {
            version = '1.0-SNAPSHOT'
        }
        if (version.toString().endsWith('-SNAPSHOT')) {
            String dt = new SimpleDateFormat('MM/dd/yyyy HH:mm', Locale.default).format(new Date())
            version += " (private-$dt-${System.getProperty('user.name')})"
        }
        mainAttributes['Plugin-Version'] = version.toString()

        mainAttributes['Jenkins-Version'] = conv.coreVersion

        mainAttributes['Mask-Classes'] = conv.maskClasses

        def dep = findDependencyProjects(project)
        if (dep.length() > 0) {
            mainAttributes['Plugin-Dependencies'] = dep
        }

        if (conv.pluginFirstClassLoader) {
            mainAttributes['PluginFirstClassLoader'] = 'true'
        }

        if (conv.developers) {
            mainAttributes['Plugin-Developers'] = conv.developers.collect {
                "${it.name ?: ''}:${it.id ?: ''}:${it.email ?: ''}"
            }.join(',')
        }

        YesNoMaybe supportDynamicLoading = isSupportDynamicLoading(classDir)
        if (supportDynamicLoading != YesNoMaybe.MAYBE) {
            mainAttributes['Support-Dynamic-Loading'] = (supportDynamicLoading == YesNoMaybe.YES).toString()
        }

        // remove empty values
        mainAttributes.entrySet().removeAll { it.value == null || it.value.toString().empty }

        mainAttributes
    }

    @Override
    Map<String, Attributes> getSections() {
        [:]
    }

    @Override
    Manifest attributes(Map<String, ?> map) throws ManifestException {
        throw new UnsupportedOperationException()
    }

    @Override
    Manifest attributes(Map<String, ?> map, String s) throws ManifestException {
        throw new UnsupportedOperationException()
    }

    @Override
    Manifest getEffectiveManifest() {
        this
    }

    @Override
    Manifest writeTo(Writer writer) {
        throw new UnsupportedOperationException()
    }

    @Override
    @SuppressWarnings('Instanceof')
    Manifest writeTo(Object o) {
        if (!(o instanceof OutputStream)) {
            throw new IllegalArgumentException()
        }
        toJavaManifest().write(o)
        this
    }

    @Override
    Manifest from(Object... objects) {
        throw new UnsupportedOperationException()
    }

    @Override
    Manifest from(Object o, Closure<?> closure) {
        throw new UnsupportedOperationException()
    }

    java.util.jar.Manifest toJavaManifest() {
        java.util.jar.Manifest manifest = new java.util.jar.Manifest()
        attributes.each {
            manifest.mainAttributes.putValue(it.key, it.value.toString())
        }
        manifest
    }

    private static String findDependencyProjects(Project project) {
        def buf = new StringBuilder()

        listUpDependencies(project.configurations.getByName(PLUGINS_DEPENDENCY_CONFIGURATION_NAME), false, buf)
        listUpDependencies(project.configurations.getByName(OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME), true, buf)

        buf.toString()
    }

    private static listUpDependencies(Configuration c, boolean optional, StringBuilder buf) {
        for (Dependency d : c.dependencies) {
            if (buf.length() > 0) {
                buf.append(',')
            }
            buf.append(d.name)
            buf.append(':')
            buf.append(d.version)
            if (optional) {
                buf.append(';resolution:=optional')
            }
        }
    }

    private static YesNoMaybe isSupportDynamicLoading(File classDir) throws IOException {
        ClassLoader classLoader = new URLClassLoader(
                [classDir.toURI().toURL()] as URL[],
                JpiManifest.classLoader as ClassLoader
        )
        def enums = Index.load(Extension, Object, classLoader).collect { it.annotation().dynamicLoadable() }
        if (enums.contains(YesNoMaybe.NO)) {
            return YesNoMaybe.NO
        }
        if (enums.contains(YesNoMaybe.MAYBE)) {
            return YesNoMaybe.MAYBE
        }
        YesNoMaybe.YES
    }

    static class JpiAttributes extends HashMap<String, Object> implements Attributes {
    }
}
