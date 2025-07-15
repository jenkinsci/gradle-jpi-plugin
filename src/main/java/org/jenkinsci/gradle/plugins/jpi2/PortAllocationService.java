package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.IOException;
import java.net.ServerSocket;

public abstract class PortAllocationService implements BuildService<BuildServiceParameters.None> {
    private static final int RETRY_LIMIT = 3;

    public int findAndReserveFreePort() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            int port = findFreePort();
            if (port != -1) {
                return port;
            }
        }
        throw new IllegalStateException("Could not reserve a free port after " + RETRY_LIMIT + " attempts");
    }

    private int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        } catch (IOException e) {
            return -1;
        }
    }
}

