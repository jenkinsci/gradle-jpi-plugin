package org.jenkinsci.gradle.plugins.accmod

import org.apache.maven.plugin.logging.Log
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object MavenLoggingBridge : Log {
    private val logger: Logger = LoggerFactory.getLogger(MavenLoggingBridge.javaClass)
    private const val exceptionMessage: String = "An error occurred"

    override fun isDebugEnabled(): Boolean = logger.isDebugEnabled

    override fun debug(content: CharSequence?) {
        logger.debug(content as String?)
    }

    override fun debug(content: CharSequence?, error: Throwable?) {
        logger.debug(content as String?, error)
    }

    override fun debug(error: Throwable?) {
        logger.debug(exceptionMessage, error)
    }

    override fun isInfoEnabled(): Boolean = logger.isInfoEnabled

    override fun info(content: CharSequence?) {
        logger.info(content as String?)
    }

    override fun info(content: CharSequence?, error: Throwable?) {
        logger.info(content as String?, error)
    }

    override fun info(error: Throwable?) {
        logger.info(exceptionMessage, error)
    }

    override fun isWarnEnabled(): Boolean = logger.isWarnEnabled

    override fun warn(content: CharSequence?) {
        logger.warn(content as String?)
    }

    override fun warn(content: CharSequence?, error: Throwable?) {
        logger.warn(content as String?, error)
    }

    override fun warn(error: Throwable?) {
        logger.warn(exceptionMessage, error)
    }

    override fun isErrorEnabled(): Boolean = logger.isErrorEnabled

    override fun error(content: CharSequence?) {
        logger.error(content as String?)
    }

    override fun error(content: CharSequence?, error: Throwable?) {
        logger.error(content as String?)
    }

    override fun error(error: Throwable?) {
        logger.error(exceptionMessage, error)
    }
}
