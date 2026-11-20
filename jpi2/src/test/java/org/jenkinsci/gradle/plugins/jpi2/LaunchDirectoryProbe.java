package org.jenkinsci.gradle.plugins.jpi2;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Stand-in for Jenkins that records and writes to its launch directories. */
public final class LaunchDirectoryProbe {
    public static void main(String[] args) throws Exception {
        var paths = new ArrayList<String>();
        paths.add(System.getenv("JENKINS_HOME"));
        for (var prefix : List.of("--webroot=", "--pluginroot=", "--extractedFilesFolder=")) {
            for (var arg : args) {
                if (arg.startsWith(prefix)) {
                    paths.add(arg.substring(prefix.length()));
                }
            }
        }
        if (paths.size() != 4) throw new IllegalStateException("Missing launch directories: " + paths);
        for (var path : paths) {
            var dir = Path.of(path);
            Files.createDirectories(dir);
            Files.writeString(dir.resolve("probe"), "written");
        }
        Files.write(Path.of(System.getProperty("probe.output")), paths);
        System.out.println("Jenkins is fully up and running");
        System.out.flush();
        Thread.sleep(30000);
    }
}
