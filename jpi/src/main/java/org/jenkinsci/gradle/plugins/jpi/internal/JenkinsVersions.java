package org.jenkinsci.gradle.plugins.jpi.internal;

import shaded.hudson.util.VersionNumber;

class JenkinsVersions {
    static final String FIRST_BOM_VERSION = "2.195";

    private JenkinsVersions() {
    }

    static boolean beforeBomExists(String jenkinsVersion) {
        return new VersionNumber(jenkinsVersion).compareTo(new VersionNumber(FIRST_BOM_VERSION)) < 0;
    }
}
