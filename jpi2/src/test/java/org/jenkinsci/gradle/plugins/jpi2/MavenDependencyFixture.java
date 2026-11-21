package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

final class MavenDependencyFixture {
    private MavenDependencyFixture() {}

    static void writeModule(Path root, String group, String name, String packaging, String dependencies)
            throws IOException {
        writeModule(root, group, name, "1", packaging, dependencies);
    }

    static void writeModule(Path root, String group, String name, String version, String packaging, String dependencies)
            throws IOException {
        var directory =
                Files.createDirectories(root.resolve("repo/" + group.replace('.', '/') + "/" + name + "/" + version));
        Files.writeString(directory.resolve(name + "-" + version + ".pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>%s</groupId>
                <artifactId>%s</artifactId><version>%s</version><packaging>%s</packaging>
                <dependencies>%s</dependencies></project>
                """.formatted(
                        group, name, version, packaging, dependencies));
        try (var ignored = new JarOutputStream(
                Files.newOutputStream(directory.resolve(name + "-" + version + "." + packaging)))) {}
    }

    static String dependency(String group, String name) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + name
                + "</artifactId><version>1</version></dependency>";
    }
}
