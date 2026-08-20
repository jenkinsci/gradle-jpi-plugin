package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.net.BindException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Covers <a href="https://github.com/jenkinsci/gradle-jpi-plugin/issues/344">#344</a>: a port
 * allocated for the Jenkins test server could be stolen by another process before it was
 * actually used, because the {@link ServerSocket} used to find a free port was closed the instant
 * it was found.
 */
class PortAllocationServiceTest {

    private final PortAllocationService service =
            ProjectBuilder.builder().build().getObjects().newInstance(PortAllocationService.class);

    @Test
    void findAndReserveFreePortReleasesImmediately_soItCanStillBeStolen() throws Exception {
        // findAndReserveFreePort() is kept only as a convenience for callers that don't need a
        // held reservation; it's still just as vulnerable to being stolen as before.
        int port = service.findAndReserveFreePort();

        try (ServerSocket thief = new ServerSocket()) {
            thief.setReuseAddress(true);
            thief.bind(new InetSocketAddress(port));

            assertThatThrownBy(() -> new ServerSocket(port))
                    .isInstanceOf(BindException.class)
                    .hasMessageContaining("Address already in use");
        }
    }

    @Test
    void reservePortHoldsTheSocketUntilReleased() throws Exception {
        int port = service.reservePort();

        // While the reservation is outstanding, nothing else -- including a concurrent
        // reservation from a sibling module's testServer task -- can bind that port.
        assertThatThrownBy(() -> new ServerSocket(port))
                .isInstanceOf(BindException.class)
                .hasMessageContaining("Address already in use");

        service.releasePort(port);

        // Once released, the port is free again for the caller to actually hand to the process
        // that will bind it.
        assertThatCode(() -> {
            try (ServerSocket bound = new ServerSocket(port)) {
                bound.setReuseAddress(true);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void everyOutstandingReservationStaysHeld() {
        // The guarantee #344 needs: every port handed out is still held while its reservation is
        // outstanding, so the OS cannot hand the same one to a sibling module's testServer task
        // under org.gradle.parallel=true. Asserting "all distinct" would NOT catch the old bug --
        // the OS cycles ephemeral ports, so the pre-fix code produced no duplicates in practice.
        // Asserting each one is genuinely unbindable does: pre-fix, every port here was free.
        var reserved = new ArrayList<Integer>();
        try {
            for (int i = 0; i < 20; i++) {
                reserved.add(service.reservePort());
            }

            assertThat(reserved).allSatisfy(port ->
                    assertThatThrownBy(() -> new ServerSocket(port))
                            .as("reservation for port %s must still be held", port)
                            .isInstanceOf(BindException.class));
        } finally {
            reserved.forEach(service::releasePort);
        }
    }

    @Test
    void releasedPortsCanBeHandedOutAgain() {
        // The flip side: releasing must actually return the port to the pool, otherwise a long
        // build would exhaust the ephemeral range instead of recycling.
        int first = service.reservePort();
        service.releasePort(first);

        int second = service.reservePort();
        try {
            // Not necessarily the same number, but reserving must keep working after a release.
            assertThat(second).isGreaterThan(0);
        } finally {
            service.releasePort(second);
        }
    }

    @Test
    void releasePortIsIdempotentAndToleratesUnknownPorts() {
        int port = service.reservePort();

        service.releasePort(port);
        assertThatCode(() -> service.releasePort(port)).doesNotThrowAnyException();
        assertThatCode(() -> service.releasePort(65535)).doesNotThrowAnyException();
    }

    @Test
    void closeReleasesAnyReservationsLeftOutstanding() throws Exception {
        int port = service.reservePort();

        service.close();

        try (ServerSocket bound = new ServerSocket(port)) {
            bound.setReuseAddress(true);
        }
    }
}
