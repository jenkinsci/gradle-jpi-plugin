package org.jenkinsci.gradle.plugins.jpi2

import org.gradle.api.Action

fun interface PluginDeveloperSpec {
    fun developer(action: Action<in PluginDeveloper>)
}
