package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.dsl.ComponentMetadataHandler;
import org.gradle.api.provider.Provider;

import java.util.Set;

/**
 * Gradle plugin that manages dependencies for Jenkins plugins.
 * <p>
 * This plugin automatically adds required dependencies based on the Jenkins core version
 * and configures component metadata rules for Jenkins core dependencies.
 */
public class DependenciesPlugin implements Plugin<Project> {
    /**
     * Applies this plugin to the given project.
     * <p>
     * This method configures the project with Jenkins-specific dependencies and
     * component metadata rules based on the Jenkins core version.
     *
     * @param project The project to apply the plugin to
     */
    @Override
    public void apply(Project project) {
        ConfigurationContainer configurations = project.getConfigurations();
        JpiExtensionBridge ext = project.getExtensions().getByType(JpiExtensionBridge.class);
        Provider<String> jenkinsCoreVersion = ext.getJenkinsCoreVersion();
        project.getDependencies().components(new Action<ComponentMetadataHandler>() {
            @Override
            public void execute(ComponentMetadataHandler components) {
                components.withModule("org.jenkins-ci.main:jenkins-core", JenkinsCoreBomRule.class);
            }
        });
        DependencyLookup lookup = new DependencyLookup();
        for (String config : lookup.configurations()) {
            configurations.getByName(config, new Action<Configuration>() {
                @Override
                public void execute(Configuration c) {
                    c.withDependencies(new Action<DependencySet>() {
                        @Override
                        public void execute(DependencySet deps) {
                            String jenkinsVersion = jenkinsCoreVersion.get();
                            Set<DependencyFactory> factories = lookup.find(c.getName(), jenkinsVersion);
                            for (DependencyFactory factory : factories) {
                                Dependency dependency = factory.create(project.getDependencies());
                                deps.add(dependency);
                            }
                        }
                    });
                }
            });
        }
    }
}
