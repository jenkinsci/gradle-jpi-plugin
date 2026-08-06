package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.util.Locale;

/**
 * A shared Gradle build service used purely as a concurrency gate for launching Jenkins.
 *
 * <p>Each {@link TestServerTask} spawns a nested Gradle build that boots a full Jenkins JVM, so every
 * {@code testServer} / {@code testHplRun} task is two heavyweight JVMs. When many such tasks run at
 * once — a monorepo can have dozens, one per plugin module — the machine saturates and Jenkins fails
 * to finish booting within the startup timeout, then gets terminated (exit code 143).
 *
 * <p>Registering this service with a bounded {@code maxParallelUsages} and having every launch task
 * declare {@code usesService(...)} caps the number of <em>concurrent Jenkins launches</em> without
 * throttling the rest of the build (compilation, unit tests, etc. keep full {@code --max-workers}
 * parallelism). This replaces the blunt {@code --max-workers=N} workaround.
 *
 * <p>The service holds no state; it exists only so Gradle can enforce the parallelism limit.
 */
public abstract class JenkinsLaunchThrottle implements BuildService<BuildServiceParameters.None> {

    /**
     * System property that sets the concurrent-launch cap. Accepts an absolute integer (e.g.
     * {@code 3}), or a processor-relative value {@code C} (all available processors) or {@code C/D}
     * (processors divided by {@code D}). The processor-relative form lets a single setting scale
     * across machines, so a user does not have to re-tune an absolute number per machine.
     */
    public static final String MAX_PARALLEL_LAUNCHES_PROPERTY = "testServer.maxParallelLaunches";

    /**
     * Default cap when the property is unset: one launch per this many processors. A Jenkins boot is
     * a bursty, largely CPU-bound workload (plugin init, Jelly/Groovy compilation, classloading), so
     * the cap scales with cores rather than memory. This divisor is the tuning knob — override the
     * whole value with {@code C/D} to change it without editing the plugin.
     */
    static final int DEFAULT_PROCESSOR_DIVISOR = 3;

    /**
     * Resolves the configured concurrent-launch cap from the raw property value.
     *
     * @param spec                 the raw property value, or {@code null}/blank to use the default {@code C/3}
     * @param availableProcessors  processor count to scale {@code C}-relative specs against
     * @return the cap, always at least {@code 1}
     * @throws IllegalArgumentException if {@code spec} is neither a non-negative integer nor {@code C}/{@code C/D}
     */
    static int resolveMaxParallelLaunches(String spec, int availableProcessors) {
        var trimmed = spec == null ? "" : spec.trim();
        if (trimmed.isEmpty()) {
            return atLeastOne(availableProcessors / DEFAULT_PROCESSOR_DIVISOR);
        }
        if (trimmed.matches("\\d+")) {
            return atLeastOne(Integer.parseInt(trimmed));
        }
        var relative = trimmed.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        if (relative.equals("C")) {
            return atLeastOne(availableProcessors);
        }
        if (relative.matches("C/\\d+")) {
            var divisor = Integer.parseInt(relative.substring(2));
            if (divisor <= 0) {
                throw new IllegalArgumentException(invalidValueMessage(spec));
            }
            return atLeastOne(availableProcessors / divisor);
        }
        throw new IllegalArgumentException(invalidValueMessage(spec));
    }

    private static int atLeastOne(int value) {
        return Math.max(1, value);
    }

    private static String invalidValueMessage(String spec) {
        return "Invalid " + MAX_PARALLEL_LAUNCHES_PROPERTY + " value '" + spec
                + "'. Expected a non-negative integer (e.g. 3), or a processor-relative value "
                + "'C' or 'C/D' (e.g. C/3).";
    }
}
