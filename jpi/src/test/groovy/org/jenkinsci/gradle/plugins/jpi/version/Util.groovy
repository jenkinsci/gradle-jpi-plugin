package org.jenkinsci.gradle.plugins.jpi.version

import java.util.regex.Pattern

class Util {

    static final Pattern GIT_HASH = ~/[a-f0-9]{40}/

    static boolean isGitHash(String version) {
        GIT_HASH.matcher(version).matches()
    }

}
