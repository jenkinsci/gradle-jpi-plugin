package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.Project;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoField;
import java.util.function.Supplier;

/**
 * Calculates version strings for plugin manifests.
 */
public class VersionCalculator {
    private final Clock clock;
    private final Supplier<String> username;

    /**
     * Creates a new version calculator.
     *
     * @param clock the clock to use for timestamps
     * @param username supplier for the username
     */
    public VersionCalculator(Clock clock, Supplier<String> username) {
        this.clock = clock;
        this.username = username;
    }

    /**
     * Creates a new version calculator with system defaults.
     */
    public VersionCalculator() {
        this(Clock.systemDefaultZone(), new SystemUsernameSupplier());
    }

    /**
     * Calculates the version string.
     *
     * @param version the input version
     * @return the calculated version string
     */
    public String calculate(String version) {
        String output = version;
        if (Project.DEFAULT_VERSION.equals(output)) {
            output = "1.0-SNAPSHOT";
        }
        if (output.endsWith("-SNAPSHOT")) {
            ZonedDateTime nowUtc = Instant.now(clock).with(ChronoField.MILLI_OF_SECOND, 0).atZone(ZoneOffset.UTC);
            String dt = DateTimeFormatter.ISO_INSTANT.format(nowUtc);
            output += String.format(" (private-%s-%s)", dt, username.get());
        }
        return output;
    }

    private static class SystemUsernameSupplier implements Supplier<String> {
        @Override
        public String get() {
            return System.getProperty("user.name");
        }
    }
}
