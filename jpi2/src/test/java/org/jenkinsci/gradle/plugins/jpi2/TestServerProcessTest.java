package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class TestServerProcessTest {
    @TempDir
    Path tempDir;

    @TestFactory
    Stream<DynamicTest> terminatesProcessesAfterEveryStartupVerdict() {
        return Stream.of("success", "crash", "exit")
                .map(mode -> dynamicTest(mode, () -> {
                    var pidFile = tempDir.resolve(mode + ".pid");
                    var process = command(mode, pidFile).start();

                    var result = TestServerTask.awaitLaunch(process, 15, ignored -> {});

                    var expected = switch (mode) {
                        case "success" -> TestServerTask.Status.SUCCESS;
                        case "crash" -> TestServerTask.Status.CRASH;
                        default -> TestServerTask.Status.EXITED;
                    };
                    assertThat(result.status()).isEqualTo(expected);
                    if (mode.equals("exit")) {
                        assertThat(result.exitCode()).isEqualTo(7);
                    }
                    assertStopped(pidFile);
                }));
    }

    @Test
    void interruptionTerminatesProcessTreeWhileOutputIsIdle() throws Exception {
        var pidFile = tempDir.resolve("interrupted.pid");
        var process = command("hang", pidFile).start();
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                TestServerTask.awaitLaunch(process, 60, ignored -> {});
            } catch (Exception e) {
                failure.set(e);
            }
        });
        thread.start();
        try {
            await().atMost(Duration.ofSeconds(15))
                    .until(() ->
                            Files.exists(pidFile) && Files.readAllLines(pidFile).size() == 2);
        } finally {
            thread.interrupt();
            thread.join(10000);
        }

        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        assertStopped(pidFile);
    }

    @Test
    void timeoutTerminatesProcessTreeWhileOutputIsIdle() throws Exception {
        var pidFile = tempDir.resolve("timeout.pid");
        var process = command("hang", pidFile).start();
        await().atMost(Duration.ofSeconds(15))
                .until(() ->
                        Files.exists(pidFile) && Files.readAllLines(pidFile).size() == 2);

        assertThat(TestServerTask.awaitLaunch(process, 1, ignored -> {}).status())
                .isEqualTo(TestServerTask.Status.TIMEOUT);
        assertStopped(pidFile);
    }

    @Test
    void interruptionDuringCleanupPropagatesInsteadOfReturningSuccess() throws Exception {
        var process = mock(Process.class);
        when(process.getInputStream())
                .thenReturn(
                        new ByteArrayInputStream("Jenkins is fully up and running\n".getBytes(StandardCharsets.UTF_8)));
        when(process.descendants()).thenReturn(Stream.empty());
        when(process.waitFor(5, TimeUnit.SECONDS)).thenThrow(new InterruptedException("cleanup cancelled"));

        assertThatThrownBy(() -> TestServerTask.awaitLaunch(process, 15, ignored -> {}))
                .isInstanceOf(InterruptedException.class)
                .hasMessage("cleanup cancelled");
    }

    @Test
    void outputFailureStillTerminatesProcessTree() throws Exception {
        var pidFile = tempDir.resolve("output-failure.pid");
        var process = command("success", pidFile).start();

        assertThatThrownBy(() -> TestServerTask.awaitLaunch(process, 15, line -> {
                    throw new IllegalStateException("output unavailable");
                }))
                .hasMessage("output unavailable");
        assertStopped(pidFile);
    }

    @Test
    void capturesChildrenBeforeClosingTheLaunchersOutputPipe() throws Exception {
        var closed = new AtomicBoolean();
        var process = mock(Process.class);
        var child = mock(ProcessHandle.class);
        var output = new ByteArrayInputStream("Jenkins is fully up and running\n".getBytes(StandardCharsets.UTF_8)) {
            @Override
            public void close() {
                closed.set(true);
            }
        };
        when(process.getInputStream()).thenReturn(output);
        when(process.descendants()).thenAnswer(ignored -> closed.get() ? Stream.empty() : Stream.of(child));

        assertThat(TestServerTask.awaitLaunch(process, 15, ignored -> {}).status())
                .isEqualTo(TestServerTask.Status.SUCCESS);

        verify(child).destroyForcibly();
        assertThat(closed).isTrue();
    }

    @Test
    void propagatesFatalOutputErrors() throws Exception {
        var pidFile = tempDir.resolve("fatal-output.pid");
        var process = command("success", pidFile).start();
        var failure = new AssertionError("fatal output failure");

        assertThatThrownBy(() -> TestServerTask.awaitLaunch(process, 15, line -> {
                    throw failure;
                }))
                .isSameAs(failure);
        assertStopped(pidFile);
    }

    private static void assertStopped(Path pidFile) throws Exception {
        for (var value : Files.readAllLines(pidFile)) {
            var pid = Long.parseLong(value);
            await().atMost(Duration.ofSeconds(15))
                    .until(() ->
                            !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
        }
    }

    private static ProcessBuilder command(String mode, Path pidFile) throws Exception {
        var executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var classes = Path.of(
                Child.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new ProcessBuilder(
                        executable, "-cp", classes.toString(), Child.class.getName(), mode, pidFile.toString())
                .redirectErrorStream(true);
    }

    public static class Child {
        public static void main(String[] args) throws Exception {
            if (args[0].equals("child")) {
                Thread.sleep(60000);
                return;
            }
            var pidFile = Path.of(args[1]);
            var child = args[0].equals("exit")
                    ? null
                    : command("child", pidFile).inheritIO().start();
            Files.write(
                    pidFile,
                    child == null
                            ? List.of(Long.toString(ProcessHandle.current().pid()))
                            : List.of(Long.toString(ProcessHandle.current().pid()), Long.toString(child.pid())));
            switch (args[0]) {
                case "success" -> System.out.println("Jenkins is fully up and running");
                case "crash" -> System.out.println("Failed Loading plugin");
                case "exit" -> System.exit(7);
                default -> {}
            }
            Thread.sleep(60000);
        }
    }
}
