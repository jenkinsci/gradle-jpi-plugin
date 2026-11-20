package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

final class WorkDirectorySettings {
    static final String PROPERTY = "jpi2.workDir";
    static final String LAUNCH_DIR_PROPERTY = "jpi2.testLaunchDir";
    static final String PRESERVE_TEST_WORK_DIR_SYSTEM_PROPERTY = "jpi2.preserveTestWorkDir";

    private WorkDirectorySettings() {}

    static String getDefaultWorkDir(String projectRoot) {
        return projectRoot + "/work";
    }

    static Provider<String> getLaunchDir(Project project) {
        return project.getProviders()
                .gradleProperty(LAUNCH_DIR_PROPERTY)
                .orElse(project.getLayout()
                        .getBuildDirectory()
                        .dir("jenkins")
                        .map(dir -> dir.getAsFile().getAbsolutePath()));
    }

    static Provider<String> getWorkDir(Project project, JenkinsPluginExtension extension, String projectRoot) {
        return project.getProviders()
                .gradleProperty(PROPERTY)
                .orElse(extension.getWorkDir().map(dir -> dir.getAsFile().getAbsolutePath()))
                .orElse(getDefaultWorkDir(projectRoot));
    }
}
