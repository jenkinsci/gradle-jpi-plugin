package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskCollection;
import org.gradle.api.tasks.testing.Test;

import java.util.LinkedList;
import java.util.List;

/**
 * Plugin that configures JVM arguments for JDK 17+ compatibility.
 */
public class Jdk17Plugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        if (atLeastJdk17()) {
            JpiExtensionBridge ext = project.getExtensions().getByType(JpiExtensionBridge.class);
            List<String> args = ext.getTestJvmArguments().getOrElse(new LinkedList<>());
            ConfigureJvmArgs configureJvmArgs = new ConfigureJvmArgs(args);
            TaskCollection<Test> tests = project.getTasks().withType(Test.class);
            tests.named("test").configure(configureJvmArgs);
            tests.named("generatedJenkinsTest").configure(configureJvmArgs);
        }
    }

    private static boolean atLeastJdk17() {
        return JavaVersion.current().compareTo(JavaVersion.VERSION_17) >= 0;
    }

    private static class ConfigureJvmArgs implements Action<Test> {
        private final List<String> jvmArgs;

        private ConfigureJvmArgs(List<String> jvmArgs) {
            this.jvmArgs = jvmArgs;
        }

        @Override
        public void execute(Test test) {
            test.jvmArgs(jvmArgs);
        }
    }
}
