package org.jenkinsci.gradle.plugins.jpi.server;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.process.ExecOperations;
import org.gradle.process.JavaExecSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * Runs a Jenkins server for local development and testing.
 */
public abstract class JenkinsServerTask extends DefaultTask {
    /** The name of this task. */
    public static final String TASK_NAME = "server";
    private static final Logger LOGGER = LoggerFactory.getLogger(JenkinsServerTask.class);
    private static final java.util.Set<String> DEFAULTED_PROPERTIES = new HashSet<>(Arrays.asList(
            "stapler.trace",
            "stapler.jelly.noCache",
            "debug.YUI",
            "hudson.Main.development"
    ));
    private final List<Action<JavaExecSpec>> execSpecActions = new LinkedList<>();

    /**
     * @return the configuration containing the Jenkins server runtime
     */
    @Classpath
    public abstract Property<Configuration> getJenkinsServerRuntime();

    /**
     * @return the Jenkins home directory
     */
    @Input
    public abstract Property<File> getJenkinsHome();

    /**
     * @return the port to start Jenkins on
     */
    @Input
    @Option(option = "port", description = "Port to start Jenkins on (default: 8080)")
    public abstract Property<String> getPort();

    /**
     * @return whether to start Jenkins in debug mode
     */
    @Input
    @Option(option = "debug-jvm", description = "Start Jenkins suspended and listening on debug port (default: 5005)")
    public abstract Property<Boolean> getDebug();

    /**
     * @return whether the main class property is available
     */
    @Input
    public abstract Property<Boolean> getMainClassPropertyAvailable();

    /**
     * @return the main class extracted from the Jenkins war file
     */
    @Internal
    public Provider<String> getExtractedMainClass() {
        return getJenkinsServerRuntime().map(new Transformer<String, Configuration>() {
            @Override
            public String transform(Configuration files) {
                ResolvedConfiguration resolved = files.getResolvedConfiguration();
                Set<ResolvedDependency> directs = resolved.getFirstLevelModuleDependencies();
                ResolvedDependency war = null;
                for (ResolvedDependency direct : directs) {
                    boolean warGroup = Objects.equals(direct.getModuleGroup(), "org.jenkins-ci.main");
                    boolean warArtifact = Objects.equals(direct.getModuleName(), "jenkins-war");
                    if (warGroup && warArtifact) {
                        war = direct;
                        break;
                    }
                }
                if (war == null) {
                    throw new RuntimeException("war file not found in configuration " + files.getName());
                }
                Set<ResolvedArtifact> artifacts = war.getModuleArtifacts();
                for (ResolvedArtifact artifact : artifacts) {
                    if (Objects.equals(artifact.getExtension(), "war")) {
                        File file = artifact.getFile();
                        try (JarFile jar = new JarFile(file)) {
                            Manifest manifest = jar.getManifest();
                            Attributes attributes = manifest.getMainAttributes();
                            return attributes.getValue("Main-Class");
                        } catch (IOException e) {
                            throw new RuntimeException("Main-Class not found in war file " + file.getAbsolutePath());
                        }
                    }
                }
                throw new RuntimeException("War file had no artifacts " + war.getModuleVersion());
            }
        });
    }

    /**
     * @return the exec operations service
     */
    @Inject
    public abstract ExecOperations getExecOperations();

    /** Creates a new Jenkins server task. */
    public JenkinsServerTask() {
        getPort().convention("8080");
        getDebug().convention(false);
        getMainClassPropertyAvailable().convention(true);
    }

    @TaskAction
    void run() {
        getExecOperations().javaexec(new Action<JavaExecSpec>() {
            @Override
            public void execute(JavaExecSpec s) {
                s.classpath(getJenkinsServerRuntime().get());
                if (getMainClassPropertyAvailable().get()) {
                    s.getMainClass().set(getExtractedMainClass());
                } else {
                    s.setMain(getExtractedMainClass().get());
                }
                s.args("--httpPort=" + getPort().get());
                s.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED");
                s.jvmArgs("--add-opens=java.base/java.lang.reflect=ALL-UNNAMED");
                s.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED");
                s.jvmArgs("--add-opens=java.base/java.text=ALL-UNNAMED");
                s.jvmArgs("--add-opens=java.base/java.util.concurrent=ALL-UNNAMED");
                s.jvmArgs("--add-opens=java.base/java.util=ALL-UNNAMED");
                s.jvmArgs("--add-opens=java.desktop/java.awt.font=ALL-UNNAMED");
                s.systemProperty("JENKINS_HOME", getJenkinsHome().get());
                for (String prop : DEFAULTED_PROPERTIES) {
                    s.systemProperty(prop, "true");
                }
                // Disable CSRF protection to avoid DefaultCrumbIssuer descriptor issues
                s.systemProperty("hudson.security.csrf.GlobalCrumbIssuerConfiguration.DISABLE_CSRF_PROTECTION", "true");
                passThroughForBackwardsCompatibility(s);
                s.setDebug(getDebug().get());
                for (Action<JavaExecSpec> action : execSpecActions) {
                    action.execute(s);
                }
            }
        });
    }

    /**
     * Adds an action to configure the Java exec spec.
     *
     * @param action the action to add
     */
    public void execSpec(Action<JavaExecSpec> action) {
        execSpecActions.add(action);
    }

    /**
     * Discovers system properties set in gradle and passes them through to the task.
     * <p>
     * Implemented for backwards-compatibility. Will be removed in 1.0.0.
     *
     * @param spec - to be mutated
     */
    public void passThroughForBackwardsCompatibility(JavaExecSpec spec) {
        boolean anyDefined = false;
        for (String prop : DEFAULTED_PROPERTIES) {
            String defined = System.getProperty(prop);
            if (defined != null) {
                anyDefined = true;
                LOGGER.warn("Passing through system property {} to server task is deprecated", prop);
                spec.systemProperty(prop, defined);
            }
        }
        if (anyDefined) {
            LOGGER.warn("Please configure server task with system properties directly");
            LOGGER.warn("Passing through will be removed in v1.0.0 of gradle-jpi-plugin");
        }
    }
}
