package org.jenkinsci.gradle.plugins.jpi2.accmod;

import org.gradle.api.Project;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Extracts Gradle project properties that share a common prefix and strips that prefix,
 * making them available as a plain map to the access-modifier checker.
 */
public class PrefixedPropertiesProvider implements Callable<Map<String, Object>> {
    private final Project project;
    private final String prefix;

    /**
     * @param project the Gradle project whose properties are scanned
     * @param prefix  only properties with this prefix are included; the prefix is stripped from the keys
     */
    public PrefixedPropertiesProvider(Project project, String prefix) {
        this.project = project;
        this.prefix = prefix;
    }

    @Override
    public Map<String, Object> call() {
        Map<String, Object> filtered = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : project.getProperties().entrySet()) {
            Object value = entry.getValue();
            String key = entry.getKey();
            if (key.startsWith(prefix) && value != null) {
                String trimmed = key.substring(prefix.length());
                filtered.put(trimmed, value);
            }
        }
        return filtered;
    }
}
