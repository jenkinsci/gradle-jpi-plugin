package org.jenkinsci.gradle.plugins.jpi2;

import java.util.ArrayList;
import java.util.List;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Action to configure the JavaExec task for running the Jenkins server.
 */
class ServerAction implements Action<JavaExec> {
    private static final String LAUNCH_DIR_PLACEHOLDER = "__jpi2_launch_directory__";
    private final Configuration serverTaskClasspath;
    private final Provider<String> workDir;
    private final Provider<String> serverPort;
    private final Provider<String> launchDir;
    private final TaskProvider<?> prepareServer;

    /** Creates an action that configures the Jenkins server process. */
    public ServerAction(
            Configuration serverTaskClasspath,
            Provider<String> workDir,
            Provider<String> serverPort,
            Provider<String> launchDir,
            TaskProvider<?> prepareServer) {
        this.serverTaskClasspath = serverTaskClasspath;
        this.workDir = workDir;
        this.serverPort = serverPort;
        this.launchDir = launchDir;
        this.prepareServer = prepareServer;
    }

    @Override
    public void execute(@NotNull JavaExec spec) {
        spec.classpath(serverTaskClasspath);
        spec.setStandardOutput(System.out);
        spec.setErrorOutput(System.err);
        spec.getMainClass().set("executable.Main");
        var resolvedWorkDir = workDir.get();
        var args = new ArrayList<String>();
        args.addAll(List.of(
                "--webroot=" + LAUNCH_DIR_PLACEHOLDER + "/war",
                "--pluginroot=" + LAUNCH_DIR_PLACEHOLDER + "/plugins",
                "--extractedFilesFolder=" + LAUNCH_DIR_PLACEHOLDER + "/extracted",
                "--commonLibFolder=" + resolvedWorkDir + "/lib",
                "--httpPort=" + serverPort.getOrElse("8080")));
        args.addAll(spec.getArgs());
        spec.setArgs(args);
        // Argument providers are appended after ordinary args; resolving placeholders here keeps
        // the defaults ahead of user-supplied arguments, including when the task is realized early.
        spec.doFirst(new ResolveLaunchDirectory(launchDir));
        spec.environment("JENKINS_HOME", resolvedWorkDir);

        spec.dependsOn(prepareServer);

        spec.getOutputs().upToDateWhen(element -> false);
    }

    private static final class ResolveLaunchDirectory implements Action<Task> {
        private final Provider<String> launchDir;

        private ResolveLaunchDirectory(Provider<String> launchDir) {
            this.launchDir = launchDir;
        }

        @Override
        public void execute(Task task) {
            var javaExec = (JavaExec) task;
            var directory = launchDir.get();
            javaExec.setArgs(javaExec.getArgs().stream()
                    .map(arg -> arg.replace(LAUNCH_DIR_PLACEHOLDER, directory))
                    .toList());
        }
    }
}
