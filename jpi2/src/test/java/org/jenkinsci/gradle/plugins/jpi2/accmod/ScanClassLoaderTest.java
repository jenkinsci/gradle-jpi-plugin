package org.jenkinsci.gradle.plugins.jpi2.accmod;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScanClassLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void closingOneScannerDoesNotInvalidateAnotherScannersResourceStream() throws Exception {
        var jar = tempDir.resolve("shared.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry("metadata"));
            output.write("shared metadata".getBytes(UTF_8));
            output.closeEntry();
        }
        var urls = new URL[] {jar.toUri().toURL()};
        try (var first = new CheckAccess.ScanClassLoader(urls, null);
                var second = new CheckAccess.ScanClassLoader(urls, null);
                var firstInput = first.getResourceAsStream("metadata");
                var secondInput = second.getResourceAsStream("metadata")) {
            assertThat(firstInput).isNotNull();
            assertThat(secondInput).isNotNull();
            first.close();
            assertThat(new String(secondInput.readAllBytes(), UTF_8)).isEqualTo("shared metadata");
        }
        Files.delete(jar);
    }
}
