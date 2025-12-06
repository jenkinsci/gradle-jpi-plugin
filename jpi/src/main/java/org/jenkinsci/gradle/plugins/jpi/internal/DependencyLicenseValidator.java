package org.jenkinsci.gradle.plugins.jpi.internal;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Validates that all requested dependencies have their licenses resolved through their POMs.
 * <p>
 * This class compares requested dependencies against resolved dependencies and generates
 * appropriate messages for any unresolved licenses.
 */
public class DependencyLicenseValidator {
    private static final String HEADER = "Could not resolve license(s) via POM for %d %s:%n";
    private static final String DEPENDENCY = "\t- %s%n";
    private static final String FOOTER = "The above will be missing from %s%n";
    
    /**
     * Validates that all requested dependencies have their licenses resolved.
     *
     * @param requested The set of requested dependency identifiers
     * @param resolved The set of resolved dependency identifiers
     * @param destination The destination path where license information will be written
     * @return A result object containing validation status and message
     */
    static Result validate(Set<String> requested, Set<String> resolved, Path destination) {
        Set<String> unresolvable = new HashSet<>();
        for (String req : requested) {
            if (!resolved.contains(req)) {
                unresolvable.add(req);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (!unresolvable.isEmpty()) {
            String pluralized = unresolvable.size() == 1 ? "dependency" : "dependencies";
            sb.append(String.format(HEADER, unresolvable.size(), pluralized));
            unresolvable.stream().sorted().map(r -> String.format(DEPENDENCY, r)).forEach(sb::append);
            sb.append(String.format(FOOTER, destination));
        }
        return new Result(unresolvable.size() > 0, sb.toString());
    }
    
    /**
     * Represents the result of a dependency license validation.
     * <p>
     * Contains information about whether any licenses were unresolved and
     * a message describing the unresolved licenses.
     */
    public static class Result {
        private final boolean unresolved;
        private final String message;

        /**
         * Constructs a new validation result.
         *
         * @param unresolved Whether any licenses were unresolved
         * @param message A message describing the unresolved licenses
         */
        public Result(boolean unresolved, String message) {
            this.unresolved = unresolved;
            this.message = message;
        }

        /**
         * Checks if any licenses were unresolved.
         *
         * @return true if any licenses were unresolved, false otherwise
         */
        public boolean isUnresolved() {
            return unresolved;
        }

        /**
         * Gets the message describing the unresolved licenses.
         *
         * @return A message describing the unresolved licenses
         */
        public String getMessage() {
            return message;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Result result = (Result) o;
            return unresolved == result.unresolved && Objects.equals(message, result.message);
        }

        @Override
        public int hashCode() {
            return Objects.hash(unresolved, message);
        }

        @Override
        public String toString() {
            return new StringJoiner(", ", Result.class.getSimpleName() + "[", "]")
                    .add("unresolved=" + unresolved)
                    .add("message='" + message + "'")
                    .toString();
        }
    }
}
