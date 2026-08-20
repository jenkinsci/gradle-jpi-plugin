package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A shared Gradle build service that finds and reserves free TCP ports for use during the build,
 * e.g. the Jenkins server port for {@code testServer}/{@code testHplRun}.
 *
 * <p>{@link #reservePort()} keeps the underlying {@link ServerSocket} open so the reservation is
 * actually held: the OS cannot hand the same port to another concurrent reservation from this
 * build (e.g. a sibling module's {@code testServer} task running in parallel) while it is
 * outstanding. Callers must release it with {@link #releasePort(int)} once they are done with the
 * number -- typically right before spawning the process that will actually bind it, since the
 * socket can't be held open across that handoff. Any reservation a caller forgets to release is
 * closed when the build service itself is closed at the end of the build.
 */
public abstract class PortAllocationService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
    private static final int RETRY_LIMIT = 3;

    private final ConcurrentHashMap<Integer, ServerSocket> reservations = new ConcurrentHashMap<>();

    /**
     * Reserves a free port, holding it open until {@link #releasePort(int)} is called.
     *
     * @return a reserved port number
     * @throws IllegalStateException if no free port can be found after retrying
     */
    public int reservePort() {
        for (int attempt = 0; attempt < RETRY_LIMIT; attempt++) {
            int port = tryReserve(attempt);
            if (port > 0) {
                return port;
            }
        }

        throw new IllegalStateException("Could not reserve a free port after " + RETRY_LIMIT + " attempts");
    }

    /**
     * Releases a port previously obtained from {@link #reservePort()}, closing the socket that was
     * holding it open. Safe to call more than once, or with a port that was never reserved -- both
     * are no-ops.
     *
     * @param port The port to release
     */
    public void releasePort(int port) {
        closeQuietly(reservations.remove(port));
    }

    /**
     * Finds a free port and releases it again before returning, for callers that don't hold a
     * long-lived reservation. Because the socket is closed before this method returns, the port
     * can be claimed by anything else on the machine before the caller gets around to using it;
     * prefer {@link #reservePort()} paired with {@link #releasePort(int)} kept open across the
     * handoff to another process, which closes that window as much as a TCP port reservation can.
     *
     * @return a free port number
     * @throws IllegalStateException if no free port can be found after retrying
     */
    public int findAndReserveFreePort() {
        int port = reservePort();
        releasePort(port);
        return port;
    }

    /**
     * Closes any reservations that were never explicitly released, so a caller that fails between
     * {@link #reservePort()} and {@link #releasePort(int)} doesn't leak an open socket for the rest
     * of the build. Called automatically by Gradle when the build service is closed.
     */
    @Override
    public void close() {
        reservations.keySet().forEach(this::releasePort);
    }

    private int tryReserve(int attempt) {
        ServerSocket socket;
        try {
            socket = new ServerSocket(0);
            socket.setReuseAddress(true);
        } catch (IOException e) {
            if (attempt == RETRY_LIMIT - 1) {
                throw new IllegalStateException("Could not find a free port after " + RETRY_LIMIT + " attempts. Exception at server socket creation.", e);
            }
            return -1;
        }

        int port = socket.getLocalPort();
        reservations.put(port, socket);
        return port;
    }

    private static void closeQuietly(ServerSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Best effort: the port is being released either way.
        }
    }
}
