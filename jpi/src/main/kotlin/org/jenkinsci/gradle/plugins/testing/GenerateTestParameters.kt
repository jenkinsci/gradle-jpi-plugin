package org.jenkinsci.gradle.plugins.testing

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkParameters

interface GenerateTestParameters : WorkParameters {
    val outputDir: DirectoryProperty
    val testParameters: MapProperty<String, String>
    val testName: Property<String>
    val pluginId: Property<String>
}
