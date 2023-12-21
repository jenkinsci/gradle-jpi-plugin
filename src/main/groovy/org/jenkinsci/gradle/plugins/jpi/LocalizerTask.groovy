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

import groovy.transform.CompileDynamic
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.OutputDirectory
import org.jvnet.localizer.GeneratorTask

/**
 * Generates Java source based on localization properties files.
 *
 * @author Andrew Bayer
 * @deprecated To be removed in 1.0.0
 * @see org.jenkinsci.gradle.plugins.jpi.localization.LocalizationTask
 */
@Deprecated
@CompileDynamic
class LocalizerTask extends ConventionTask {
    public static final String TASK_NAME = 'localizer'

    @OutputDirectory
    File destinationDir

    @InputFiles
    Set<File> sourceDirs

    @TaskAction
    def generateLocalized() {
        sourceDirs.findAll { it.exists() }.each { File sourceDirectory ->
            GeneratorTask generator = new GeneratorTask()
            generator.project = ant.project
            generator.dir = sourceDirectory
            generator.todir = destinationDir
            generator.includes = '**/Messages.properties'
            generator.execute()
        }
    }
}
