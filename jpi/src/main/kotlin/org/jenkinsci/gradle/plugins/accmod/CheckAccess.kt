package org.jenkinsci.gradle.plugins.accmod

import org.gradle.workers.WorkAction
import org.kohsuke.accmod.impl.Checker
import org.slf4j.LoggerFactory
import java.net.URLClassLoader
import java.util.*

abstract class CheckAccess : WorkAction<CheckAccessParameters> {
    private companion object {
        private val LOGGER = LoggerFactory.getLogger(CheckAccess::class.java)
    }

    override fun execute() {
        val toScan = parameters.classpathToScan.files.map { it.toURI().toURL() }
        val array = toScan.toTypedArray()
        val loader = URLClassLoader(array, javaClass.classLoader)
        val listener = InternalErrorListener()
        val props = Properties()
        for ((key, value) in parameters.propertiesForAccessModifier.get()) {
            props[key] = value
        }
        val checker = Checker(loader, listener, props, MavenLoggingBridge)
        checker.check(parameters.dirToCheck.asFile.get())
        parameters.outputFile.asFile.get().writeText(listener.errorMessage())
        if (listener.hasErrors()) {
            if (parameters.ignoreFailures.get()) {
                LOGGER.warn(listener.errorMessage())
            } else {
                LOGGER.error(listener.errorMessage())
                throw RestrictedApiException()
            }
        }
    }
}
