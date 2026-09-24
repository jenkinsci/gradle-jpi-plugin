package org.jenkinsci.gradle.plugins.jpi2;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the JavaExec task for running the Jenkins server.
 */
@SuppressWarnings({
    "Convert2Lambda", // Gradle doesn't like lambdas
})
class ServerAction implements Action<JavaExec> {
    private final Configuration serverTaskClasspath;
    private final String projectRoot;
    private final Provider<String> workDir;
    private final TaskProvider<?> prepareServer;

    public ServerAction(
            Configuration serverTaskClasspath,
            String projectRoot,
            Provider<String> workDir,
            TaskProvider<?> prepareServer) {
        this.serverTaskClasspath = serverTaskClasspath;
        this.projectRoot = projectRoot;
        this.workDir = workDir;
        this.prepareServer = prepareServer;
    }

    @Override
    public void execute(@NotNull JavaExec spec) {
        spec.classpath(serverTaskClasspath);
        spec.setStandardOutput(System.out);
        spec.setErrorOutput(System.err);
        spec.getMainClass().set("executable.Main");
        spec.doFirst(task -> {
            var resolvedWorkDir = workDir.get();
            var serverPort = task.getProject().findProperty("server.port");
            var args = new ArrayList<String>();
            args.addAll(List.of(
                    "--webroot=" + projectRoot + "/build/jenkins/war",
                    "--pluginroot=" + projectRoot + "/build/jenkins/plugins",
                    "--extractedFilesFolder=" + projectRoot + "/build/jenkins/extracted",
                    "--commonLibFolder=" + resolvedWorkDir + "/lib",
                    "--httpPort=" + (serverPort != null ? serverPort : "8080")));
            args.addAll(spec.getArgs());
            spec.setArgs(args);
            spec.environment("JENKINS_HOME", resolvedWorkDir);
        });

        spec.dependsOn(prepareServer);

        spec.getOutputs().upToDateWhen(new Spec<>() {
            @Override
            public boolean isSatisfiedBy(Task element) {
                return false;
            }
        });
    }
}
