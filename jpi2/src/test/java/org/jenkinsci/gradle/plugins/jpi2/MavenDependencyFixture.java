package org.jenkinsci.gradle.plugins.jpi2;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarOutputStream;

final class MavenDependencyFixture {
    private MavenDependencyFixture() {}

    static void writeModule(Path root, String group, String name, String packaging, String dependencies)
            throws IOException {
        var directory = Files.createDirectories(root.resolve("repo/" + group.replace('.', '/') + "/" + name + "/1"));
        Files.writeString(directory.resolve(name + "-1.pom"), """
                <project><modelVersion>4.0.0</modelVersion><groupId>%s</groupId>
                <artifactId>%s</artifactId><version>1</version><packaging>%s</packaging>
                <dependencies>%s</dependencies></project>
                """.formatted(group, name, packaging, dependencies));
        try (var ignored = new JarOutputStream(Files.newOutputStream(directory.resolve(name + "-1." + packaging)))) {}
    }

    static String dependency(String group, String name) {
        return "<dependency><groupId>" + group + "</groupId><artifactId>" + name
                + "</artifactId><version>1</version></dependency>";
    }
}
