package org.jenkinsci.gradle.plugins.jpi.core;

import org.gradle.api.artifacts.ResolvedArtifact;

import java.util.HashSet;
import java.util.Set;

/**
 * Utility class for handling Jenkins plugin archive extensions.
 * Provides constants and methods for working with HPI and JPI extensions.
 */
public class ArchiveExtensions {
    /** Constant representing the HPI (Hudson Plugin Interface) file extension. */
    public static final String HPI = "hpi";
    /** Constant representing the JPI (Jenkins Plugin Interface) file extension. */
    public static final String JPI = "jpi";
    private static Set<String> ALL_EXTENSIONS;
    private static Set<String> ALL_PATHS;

    private ArchiveExtensions() {
    }

    /**
     * Appends the JPI extension to the artifact name.
     *
     * @param artifact The resolved artifact to get the name from
     * @return The artifact name with JPI extension
     */
    public static String nameWithJpi(ResolvedArtifact artifact) {
        return artifact.getName() + "." + JPI;
    }

    /**
     * Returns a set of all supported plugin archive extensions (JPI and HPI).
     *
     * @return A set containing all supported extensions
     */
    public static Set<String> allExtensions() {
        if (ALL_EXTENSIONS == null) {
            Set<String> extensions = new HashSet<>();
            extensions.add(JPI);
            extensions.add(HPI);
            ALL_EXTENSIONS = extensions;
        }
        return ALL_EXTENSIONS;
    }

    /**
     * Returns a set of file patterns for all supported extensions.
     * Each pattern is in the form "*.extension".
     *
     * @return A set of file patterns for all supported extensions
     */
    public static Set<String> allPathPatterns() {
        if (ALL_PATHS == null) {
            Set<String> paths = new HashSet<>();
            Set<String> allExtensions = allExtensions();
            for (String extension : allExtensions) {
                paths.add("*." + extension);
            }
            ALL_PATHS = paths;
        }
        return ALL_PATHS;
    }
}
