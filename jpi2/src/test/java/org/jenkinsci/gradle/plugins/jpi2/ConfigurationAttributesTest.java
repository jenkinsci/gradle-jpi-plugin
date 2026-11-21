package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

class ConfigurationAttributesTest {
    @Test
    void everyPluginConfigurationThatCanBeResolvedOrConsumedHasAttributes() {
        var project = ProjectBuilder.builder().build();
        project.getPlugins().apply(JavaLibraryPlugin.class);
        var javaConfigurations = new HashSet<>(project.getConfigurations().getNames());

        project.getPlugins().apply(V2JpiPlugin.class);

        project.getConfigurations().stream()
                .filter(configuration -> !javaConfigurations.contains(configuration.getName()))
                .filter(configuration -> configuration.isCanBeResolved() || configuration.isCanBeConsumed())
                .forEach(configuration -> assertThat(
                                configuration.getAttributes().keySet())
                        .as("attributes of %s", configuration.getName())
                        .isNotEmpty());
        // These carry dependencies and constraints for other configurations to inherit.
        for (var name : new String[] {"lastAnnotationProcessor", "jenkinsCoreAlignment"}) {
            var configuration = project.getConfigurations().getByName(name);
            assertThat(configuration.isCanBeConsumed()).as(name).isFalse();
            assertThat(configuration.isCanBeResolved()).as(name).isFalse();
        }
        for (var name : new String[] {"jenkinsCore", "testDefaultRuntime", "serverTaskClasspath"}) {
            assertThat(project.getConfigurations().getByName(name).isCanBeConsumed())
                    .as(name)
                    .isFalse();
        }
    }
}
