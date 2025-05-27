package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Action to configure the JavaExec task for running the Jenkins server.
 */
@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
})
class ServerAction implements Action<JavaExec> {
    private final Configuration serverTaskClasspath;
    private final String projectRoot;
    private final TaskProvider<?> prepareServer;

    public ServerAction(Configuration serverTaskClasspath, String projectRoot, TaskProvider<?> prepareServer) {
        this.serverTaskClasspath = serverTaskClasspath;
        this.projectRoot = projectRoot;
        this.prepareServer = prepareServer;
    }

    @Override
    public void execute(@NotNull JavaExec spec) {
        spec.classpath(serverTaskClasspath);
        spec.setStandardOutput(System.out);
        spec.setErrorOutput(System.err);
        spec.getMainClass().set("executable.Main");
        spec.args(List.of(
                "--webroot=" + projectRoot + "/build/jenkins/war",
                "--pluginroot=" + projectRoot + "/build/jenkins/plugins",
                "--extractedFilesFolder=" + projectRoot + "/build/jenkins/extracted",
                "--commonLibFolder=" + projectRoot + "/work/lib",
                "--httpPort=" + System.getProperty("server.port", "8080")
        ));
        spec.environment("JENKINS_HOME", projectRoot + "/work");

        spec.dependsOn(prepareServer);

        spec.getOutputs().upToDateWhen(new Spec<>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return false;
            }
        });
    }
}
