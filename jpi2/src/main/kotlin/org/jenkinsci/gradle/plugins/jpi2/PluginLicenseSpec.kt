package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.Action

fun interface PluginLicenseSpec {
    fun license(action: Action<in PluginLicense>)
}
