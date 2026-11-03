package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitCommandTest {
    @TempDir
    Path tempDir;

    @Test
    void capturesOutputLargerThanAPipeBuffer() throws Exception {
        assertThat(GitVersion.runCommand(command("output"), Duration.ofSeconds(15)))
                .containsExactly("x".repeat(256 * 1024));
    }

    @Test
    void reportsFailedCommandOutput() throws Exception {
        var builder = command("fail");
        assertThatThrownBy(() -> GitVersion.runCommand(builder, Duration.ofSeconds(15)))
                .hasMessageContaining("failed: failure details");
    }

    @Test
    void timesOutAndTerminatesACommandThatKeepsStdoutOpen() throws Exception {
        var pidFile = tempDir.resolve("timeout.pid");
        var builder = command("hang", pidFile.toString());
        var start = System.nanoTime();

        assertThatThrownBy(() -> GitVersion.runCommand(builder, Duration.ofSeconds(3)))
                .hasMessageContaining("timed out");

        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(15));
        assertStopped(pidFile);
    }

    @Test
    void interruptionTerminatesTheChildProcess() throws Exception {
        var pidFile = tempDir.resolve("interrupted.pid");
        var builder = command("hang", pidFile.toString());
        var failure = new AtomicReference<Throwable>();
        var thread = new Thread(() -> {
            try {
                GitVersion.runCommand(builder, Duration.ofMinutes(1));
            } catch (Exception e) {
                failure.set(e);
            }
        });
        thread.start();
        try {
            await().atMost(Duration.ofSeconds(15)).until(() -> Files.exists(pidFile));
        } finally {
            thread.interrupt();
            thread.join(10000);
        }
        assertThat(thread.isAlive()).isFalse();
        assertThat(failure.get()).isInstanceOf(InterruptedException.class);
        assertStopped(pidFile);
    }

    private static void assertStopped(Path pidFile) throws Exception {
        var pid = Long.parseLong(Files.readString(pidFile));
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false));
    }

    private static ProcessBuilder command(String... arguments) throws Exception {
        var executable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        var classes = Path.of(
                Child.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var builder = new ProcessBuilder(executable, "-cp", classes.toString(), Child.class.getName());
        builder.command().addAll(List.of(arguments));
        return builder;
    }

    public static class Child {
        public static void main(String[] args) throws Exception {
            switch (args[0]) {
                case "output" -> System.out.print("x".repeat(256 * 1024));
                case "fail" -> {
                    System.err.print("failure details");
                    System.exit(7);
                }
                case "hang" -> {
                    Files.writeString(
                            Path.of(args[1]),
                            Long.toString(ProcessHandle.current().pid()));
                    Thread.sleep(60000);
                }
                default -> throw new IllegalArgumentException(args[0]);
            }
        }
    }
}
