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

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logger
import org.jenkinsci.gradle.plugins.jpi.internal.ConfigureUtil

/**
 * Information on a single developer for the <developer> tag in the output POM.
 *
 * @author Andrew Bayer
 * @deprecated To be removed in 1.0.0
 * @see org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper
 */
@Deprecated
class JpiDeveloper {
    final static LEGAL_FIELDS = ['id', 'name', 'email', 'url', 'organization', 'organizationUrl', 'timezone']

    private final fields = [:]

    private final Logger logger

    JpiDeveloper(Logger logger) {
        this.logger = logger
    }

    def getProperty(String f) {
        fields[f]
    }

    void setProperty(String f, val) {
        fields[f] = val
    }

    def methodMissing(String name, args) {
        if (LEGAL_FIELDS.contains(name)) {
            setProperty(name, *args)
        } else {
            logger.log(LogLevel.WARN, "JPI POM developer field ${name} not implemented.")
        }
    }

    def configure(Closure closure) {
        ConfigureUtil.configure(closure, this)
    }
}
