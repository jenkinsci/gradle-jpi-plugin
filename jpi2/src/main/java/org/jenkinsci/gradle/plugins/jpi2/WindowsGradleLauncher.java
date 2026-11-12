package org.jenkinsci.gradle.plugins.jpi2;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.gradle.api.GradleException;

/** Avoids the extra shell parsing performed by Gradle's Windows batch launcher. */
final class WindowsGradleLauncher {
    private WindowsGradleLauncher() {}

    static List<String> command(Path executable, Path javaHome, Map<String, String> environment) {
        var absoluteExecutable = executable.toAbsolutePath();
        if (!absoluteExecutable.getFileName().toString().equalsIgnoreCase("gradle.bat")
                || absoluteExecutable.getParent() == null
                || absoluteExecutable.getParent().getFileName() == null
                || !absoluteExecutable.getParent().getFileName().toString().equalsIgnoreCase("bin")) {
            return new ArrayList<>(List.of(executable.toString()));
        }
        var libraryDirectory = absoluteExecutable.getParent().getParent().resolve("lib");
        if (!Files.isDirectory(libraryDirectory)) {
            return new ArrayList<>(List.of(executable.toString()));
        }
        try (var libraries = Files.list(libraryDirectory)) {
            var launcher = libraries
                    .filter(path ->
                            path.getFileName().toString().matches("gradle-(gradle-cli-main|launcher)-[^/]+\\.jar"))
                    .sorted((first, second) -> {
                        var firstCli = first.getFileName().toString().startsWith("gradle-gradle-cli-main-");
                        var secondCli = second.getFileName().toString().startsWith("gradle-gradle-cli-main-");
                        return Boolean.compare(secondCli, firstCli);
                    })
                    .findFirst();
            if (launcher.isEmpty()) {
                return new ArrayList<>(List.of(executable.toString()));
            }
            var java = javaHome.resolve("bin")
                    .resolve(Files.exists(javaHome.resolve("bin/java.exe")) ? "java.exe" : "java");
            var command = new ArrayList<>(List.of(java.toString(), "-Xmx64m", "-Xms64m"));
            command.addAll(splitOptions(environmentValue(environment, "JAVA_OPTS")));
            command.addAll(splitOptions(environmentValue(environment, "GRADLE_OPTS")));
            command.add("-Dorg.gradle.appname=gradle");
            command.add("-classpath");
            command.add(launcher.get().toString());
            command.add("org.gradle.launcher.GradleMain");
            return command;
        } catch (IOException e) {
            throw new GradleException("Cannot read Gradle's Java launcher in " + libraryDirectory, e);
        }
    }

    static List<String> processArguments(List<String> command) {
        if (File.separatorChar != '\\'
                || "false".equalsIgnoreCase(System.getProperty("jdk.lang.Process.allowAmbiguousCommands", "true"))) {
            return command;
        }
        var arguments = new ArrayList<String>();
        arguments.add(command.get(0));
        command.subList(1, command.size()).stream()
                .map(WindowsGradleLauncher::quoteArgument)
                .forEach(arguments::add);
        return arguments;
    }

    static String quoteArgument(String argument) {
        if (argument.indexOf('"') < 0) {
            return argument;
        }
        // Java's default Windows process mode leaves embedded quotes unescaped.
        // A fully quoted argument bypasses that mode's partial quoting.
        var quoted = new StringBuilder("\"");
        var backslashes = 0;
        for (var character : argument.toCharArray()) {
            if (character == '\\') {
                backslashes++;
                continue;
            }
            quoted.append("\\".repeat(character == '"' ? 2 * backslashes + 1 : backslashes));
            quoted.append(character);
            backslashes = 0;
        }
        return quoted.append("\\".repeat(2 * backslashes)).append('"').toString();
    }

    private static String environmentValue(Map<String, String> environment, String name) {
        return environment.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(name))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
    }

    /** Matches Java's Windows parsing of the JVM options supplied by the batch launcher. */
    static List<String> splitOptions(String text) {
        var result = new ArrayList<String>();
        var token = new StringBuilder();
        var quoted = false;
        var started = false;
        for (var index = 0; index < text.length(); index++) {
            var character = text.charAt(index);
            if ((character == ' ' || character == '\t') && !quoted) {
                if (started) {
                    result.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
                continue;
            }
            started = true;
            if (character == '\\') {
                var start = index;
                while (index < text.length() && text.charAt(index) == '\\') {
                    index++;
                }
                var count = index - start;
                if (index < text.length() && text.charAt(index) == '"') {
                    token.append("\\".repeat(count / 2));
                    if (count % 2 == 0) {
                        if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                            token.append('"');
                            index++;
                        } else {
                            quoted = !quoted;
                        }
                    } else {
                        token.append('"');
                    }
                } else {
                    token.append("\\".repeat(count));
                    index--;
                }
            } else if (character == '"') {
                if (quoted && index + 1 < text.length() && text.charAt(index + 1) == '"') {
                    token.append('"');
                    index++;
                } else {
                    quoted = !quoted;
                }
            } else {
                token.append(character);
            }
        }
        if (started) {
            result.add(token.toString());
        }
        return result;
    }
}
