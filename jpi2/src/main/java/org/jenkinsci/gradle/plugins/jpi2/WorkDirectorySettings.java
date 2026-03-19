package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

final class WorkDirectorySettings {
    static final String SYSTEM_PROPERTY = "jpi2.workDir";
    static final String PRESERVE_TEST_WORK_DIR_SYSTEM_PROPERTY = "jpi2.preserveTestWorkDir";

    private WorkDirectorySettings() {
    }

    static String getDefaultWorkDir(String projectRoot) {
        return projectRoot + "/work";
    }

    static Provider<String> getWorkDir(Project project, JenkinsPluginExtension extension, String projectRoot) {
        return project.getProviders().systemProperty(SYSTEM_PROPERTY)
                .orElse(extension.getWorkDir().map(dir -> dir.getAsFile().getAbsolutePath()))
                .orElse(getDefaultWorkDir(projectRoot));
    }
}
