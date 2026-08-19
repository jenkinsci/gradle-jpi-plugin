package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.gradle.api.Project;
import org.gradle.api.provider.Provider;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Extracts Gradle project properties that share a common prefix and strips that prefix,
 * making them available as a plain map to the access-modifier checker.
 */
public class PrefixedPropertiesProvider {
    private PrefixedPropertiesProvider() {
    }

    /**
     * @param project the Gradle project whose properties are scanned
     * @param prefix  only properties with this prefix are included; the prefix is stripped from the keys
     */
    public static Provider<Map<String, String>> gradlePropertiesPrefixedBy(Project project, String prefix) {
        return project.getProviders().gradlePropertiesPrefixedBy(prefix).map(properties ->
                properties.entrySet().stream()
                        .filter(e -> e.getValue() != null)
                        .collect(Collectors.toMap(
                                e -> e.getKey().substring(prefix.length()),
                                Map.Entry::getValue))
        );
    }
}
