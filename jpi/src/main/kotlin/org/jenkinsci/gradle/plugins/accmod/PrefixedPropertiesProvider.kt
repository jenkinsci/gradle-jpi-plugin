package org.jenkinsci.gradle.plugins.accmod

import org.gradle.api.Project
import java.util.concurrent.Callable

open class PrefixedPropertiesProvider(private val project: Project, private val prefix: String) : Callable<Map<String, Any>> {
    override fun call(): Map<String, Any> {
        val filtered = mutableMapOf<String, Any>()
        for ((key, value) in project.properties) {
            if (key.startsWith(prefix) && value != null) {
                val tidy = key.replace(prefix, "")
                filtered[tidy] = value
            }
        }
        return filtered
    }
}
