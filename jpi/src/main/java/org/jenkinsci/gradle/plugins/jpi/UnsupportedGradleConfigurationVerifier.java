package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvableDependencies;

public class UnsupportedGradleConfigurationVerifier {
    /**
     * Represented the dependencies on other Jenkins plugins.
     * Now it should be replaced with implementation
     */
    public static final String PLUGINS_DEPENDENCY_CONFIGURATION_NAME = "jenkinsPlugins";

    /**
     * Represented the dependencies on other Jenkins plugins.
     * Now it should be replaced with gradle feature variants
     */
    public static final String OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME = "optionalJenkinsPlugins";

    /**
     * Represented the Jenkins plugin test dependencies.
     * Now it should be replaced with testImplementation
     */
    public static final String JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME = "jenkinsTest";

    static void configureDeprecatedConfigurations(Project project) {
        configureDeprecatedConfiguration(project,
                PLUGINS_DEPENDENCY_CONFIGURATION_NAME,
                "is not supported anymore. Please use implementation configuration");
        configureDeprecatedConfiguration(project,
                OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME,
                "is not supported anymore. Please use Gradle feature variants");
        configureDeprecatedConfiguration(project,
                JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME,
                "is not supported anymore. Please use testImplementation configuration");
    }

    private static void configureDeprecatedConfiguration(Project project, String confName, String errorMessage) {
        project.getConfigurations().create(confName, new Action<Configuration>() {
            @Override
            public void execute(Configuration conf) {
                conf.setVisible(false);
                conf.getIncoming().beforeResolve(new Action<ResolvableDependencies>() {
                    @Override
                    public void execute(ResolvableDependencies resolvable) {
                        if (resolvable.getDependencies().size() > 0) {
                            throw new GradleException(String.join(" ", confName, errorMessage));
                        }
                    }
                });
            }
        });
    }
}
