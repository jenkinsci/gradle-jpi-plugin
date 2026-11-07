package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

class WindowsGradleLauncherTest {
    @Test
    void preservesJvmEnvironmentOptionsAndPrefersTheGradleNineLauncher(@TempDir Path root) throws Exception {
        var lib = Files.createDirectories(root.resolve("gradle/lib"));
        Files.write(lib.resolve("gradle-launcher-9.2.1.jar"), new byte[0]);
        var main = Files.write(lib.resolve("gradle-gradle-cli-main-9.2.1.jar"), new byte[0]);

        var command = WindowsGradleLauncher.command(
                root.resolve("gradle/bin/gradle.bat"),
                root.resolve("java"),
                Map.of(
                        "JAVA_OPTS",
                        "-Dplain=value \"-Dlabel=hello world\"",
                        "GRADLE_OPTS",
                        "-Xmx512m -Dquoted=\\\"text\\\""));

        assertThat(command)
                .containsSubsequence(
                        "-Dplain=value",
                        "-Dlabel=hello world",
                        "-Xmx512m",
                        "-Dquoted=\"text\"",
                        "-Dorg.gradle.appname=gradle",
                        "-classpath",
                        main.toString(),
                        "org.gradle.launcher.GradleMain");
    }

    @Test
    void childJvmReceivesQuotedArgumentsVerbatim() throws Exception {
        var values = List.of(
                "-Dorg.gradle.jvmargs=-Xmx384m -Dlabel=\"quoted label\"",
                "-Pjson={\"path\":\"C:\\cache\\\"quoted\"\"}",
                "-Pdirectory=C:\\Program Files\\",
                "-Pquoted=\"C:\\Program Files\\\"",
                "-Ppattern=**/src/**");
        var executable = Path.of(System.getProperty("java.home"), "bin", "java");
        var classpath = Path.of(ArgumentEcho.class
                .getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI());
        var command = new ArrayList<>(
                List.of(executable.toString(), "-cp", classpath.toString(), ArgumentEcho.class.getName()));
        command.addAll(values);
        var process = new ProcessBuilder(WindowsGradleLauncher.processArguments(command))
                .redirectErrorStream(true)
                .start();
        try {
            assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(process.exitValue()).as(output).isZero();
            assertThat(output.lines().toList())
                    .isEqualTo(values.stream()
                            .map(value -> Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)))
                            .toList());
        } finally {
            process.destroyForcibly();
        }
    }

    @TestFactory
    Stream<DynamicTest> quotesEmbeddedQuotesForWindowsProcesses() {
        return Stream.of("-Dlabel=\"café 日本語\"", "-Pjson={\"line\":\"\\n\"}", "-Ppath=\"C:\\Program Files\\\"")
                .map(value -> dynamicTest(
                        value,
                        () -> assertThat(WindowsGradleLauncher.splitOptions(WindowsGradleLauncher.quoteArgument(value)))
                                .containsExactly(value)));
    }

    public static class ArgumentEcho {
        public static void main(String[] arguments) {
            for (var argument : arguments) {
                System.out.println(Base64.getEncoder().encodeToString(argument.getBytes(StandardCharsets.UTF_8)));
            }
        }
    }

    @TestFactory
    Stream<DynamicTest> followsWindowsArgumentParsing() {
        return Map.of(
                        "-Dpath=C:\\cache \"-Ddirectory=C:\\Program Files\\\\\"",
                        List.of("-Dpath=C:\\cache", "-Ddirectory=C:\\Program Files\\"),
                        "-Dlabel=one\u2003two",
                        List.of("-Dlabel=one\u2003two"),
                        "-Dlabel=\"a\"\"b\"",
                        List.of("-Dlabel=a\"b"))
                .entrySet()
                .stream()
                .map(entry -> dynamicTest(
                        entry.getKey(),
                        () -> assertThat(WindowsGradleLauncher.splitOptions(entry.getKey()))
                                .isEqualTo(entry.getValue())));
    }

    @Test
    void retainsCustomBatchAndWrapperCommands(@TempDir Path root) throws Exception {
        var rootExecutable = root.toAbsolutePath().getRoot().resolve("gradle.bat");
        assertThat(WindowsGradleLauncher.command(rootExecutable, root.resolve("java"), Map.of()))
                .containsExactly(rootExecutable.toString());
        var executable = root.resolve("bin/gradlew.bat");
        var library = Files.createDirectories(root.resolve("lib"));
        Files.createFile(library.resolve("gradle-gradle-cli-main-9.0.jar"));
        assertThat(WindowsGradleLauncher.command(executable, root.resolve("java"), Map.of()))
                .containsExactly(executable.toString());
    }
}
