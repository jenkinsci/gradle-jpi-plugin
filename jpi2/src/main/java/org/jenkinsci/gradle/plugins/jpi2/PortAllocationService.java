package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * A Shared Gradle build service that provides port allocation functionality.
 * This service finds and reserves free ports for use during the build process.
 */
public abstract class PortAllocationService implements BuildService<BuildServiceParameters.None> {
    private static final int RETRY_LIMIT = 3;

    /**
     * Finds and reserves a free port for use during the build.
     *
     * @return a free port number
     * @throws IllegalStateException if no free port can be found after retrying
     */
    public int findAndReserveFreePort() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            int port = findFreePort(attempt);
            if (port > 0) {
                return port;
            }
        }

        throw new IllegalStateException("Could not reserve a free port after " + RETRY_LIMIT + " attempts");
    }

    private int findFreePort(int attempt) {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            if (attempt == RETRY_LIMIT - 1) {
                throw new IllegalStateException("Could not find a free port after " + RETRY_LIMIT + " attempts. Exception at server socket creation.", e);
            }
            return -1;
        }
    }
}

