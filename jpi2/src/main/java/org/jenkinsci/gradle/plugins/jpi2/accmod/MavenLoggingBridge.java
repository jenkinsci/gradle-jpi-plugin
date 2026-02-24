package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.apache.maven.plugin.logging.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MavenLoggingBridge implements Log {
    private static final Logger LOGGER = LoggerFactory.getLogger(MavenLoggingBridge.class);
    private static final String EXCEPTION_MESSAGE = "An error occurred";

    @Override
    public boolean isDebugEnabled() {
        return LOGGER.isDebugEnabled();
    }

    @Override
    public void debug(CharSequence content) {
        LOGGER.debug(content == null ? null : content.toString());
    }

    @Override
    public void debug(CharSequence content, Throwable error) {
        LOGGER.debug(content == null ? null : content.toString(), error);
    }

    @Override
    public void debug(Throwable error) {
        LOGGER.debug(EXCEPTION_MESSAGE, error);
    }

    @Override
    public boolean isInfoEnabled() {
        return LOGGER.isInfoEnabled();
    }

    @Override
    public void info(CharSequence content) {
        LOGGER.info(content == null ? null : content.toString());
    }

    @Override
    public void info(CharSequence content, Throwable error) {
        LOGGER.info(content == null ? null : content.toString(), error);
    }

    @Override
    public void info(Throwable error) {
        LOGGER.info(EXCEPTION_MESSAGE, error);
    }

    @Override
    public boolean isWarnEnabled() {
        return LOGGER.isWarnEnabled();
    }

    @Override
    public void warn(CharSequence content) {
        LOGGER.warn(content == null ? null : content.toString());
    }

    @Override
    public void warn(CharSequence content, Throwable error) {
        LOGGER.warn(content == null ? null : content.toString(), error);
    }

    @Override
    public void warn(Throwable error) {
        LOGGER.warn(EXCEPTION_MESSAGE, error);
    }

    @Override
    public boolean isErrorEnabled() {
        return LOGGER.isErrorEnabled();
    }

    @Override
    public void error(CharSequence content) {
        LOGGER.error(content == null ? null : content.toString());
    }

    @Override
    public void error(CharSequence content, Throwable error) {
        LOGGER.error(content == null ? null : content.toString(), error);
    }

    @Override
    public void error(Throwable error) {
        LOGGER.error(EXCEPTION_MESSAGE, error);
    }
}
