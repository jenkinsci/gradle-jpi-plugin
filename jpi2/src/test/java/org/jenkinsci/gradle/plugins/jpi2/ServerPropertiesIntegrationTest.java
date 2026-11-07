package org.jenkinsci.gradle.plugins.jpi2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.stream.Stream;
import org.jenkinsci.gradle.plugins.jpi.IntegrationTestHelper;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class ServerPropertiesIntegrationTest extends V2IntegrationTestBase {
    @TestFactory
    Stream<DynamicTest> nestedServerReceivesUnmodifiedProperties() {
        return Stream.of(
                        "\\d+",
                        "domain\\user",
                        "{\"line\":\"\\n\"}",
                        "C:\\Program Files\\",
                        "%USERPROFILE%\\cache",
                        "café",
                        "日本語")
                .map(value -> dynamicTest(value, () -> {
                    // Java 17's Windows launcher converts arguments through the native code page.
                    assumeTrue(Charset.forName(System.getProperty("native.encoding", "UTF-8"))
                            .newEncoder()
                            .canEncode(value));
                    var root = Files.createTempDirectory(tempDir.toPath(), "properties-");
                    var ith = new IntegrationTestHelper(root.toFile(), "8.14");
                    configureSimpleBuildForVerification(ith);
                    Files.writeString(root.resolve("gradle.properties"), "projectValue=conflicting-file-value\\n");
                    Files.writeString(
                            ith.inProjectDir("build.gradle.kts").toPath(),
                            "import java.util.Base64\n"
                                    + Files.readString(
                                            ith.inProjectDir("build.gradle.kts").toPath()) + """
                    tasks.named<org.jenkinsci.gradle.plugins.jpi2.TestServerTask>("testServer") {
                        gradleExecutable.set(gradle.gradleHomeDir!!.resolve("bin/gradle.bat").absolutePath)
                        systemProperties.put("org.gradle.jvmargs", "-Xmx384m -Dverification.label=\\\"quoted label\\\"")
                    }
                    val bootstrapValue = System.getProperty("verification.label", "missing")
                    val mainOutput = the<SourceSetContainer>()["main"].output
                    tasks.named<JavaExec>("server") {
                        mainClass.set("example.EchoProperties")
                        classpath = files(mainOutput)
                        // Isolate forwarding from JavaExec's separate native argument parsing.
                        val encoder = Base64.getEncoder()
                        environment("BOOTSTRAP_VALUE", encoder.encodeToString(bootstrapValue.toByteArray(Charsets.UTF_8)))
                        environment("PROJECT_VALUE", encoder.encodeToString(providers.gradleProperty("projectValue").get().toByteArray(Charsets.UTF_8)))
                        environment("SYSTEM_VALUE", encoder.encodeToString(providers.systemProperty("systemValue").get().toByteArray(Charsets.UTF_8)))
                    }
                    """);
                    var source = Files.createDirectories(root.resolve("src/main/java/example"));
                    Files.writeString(source.resolve("EchoProperties.java"), """
                    package example;
                    public class EchoProperties {
                        public static void main(String[] args) {
                            System.out.println("BOOTSTRAP_VALUE=" + System.getenv("BOOTSTRAP_VALUE"));
                            System.out.println("PROJECT_VALUE=" + System.getenv("PROJECT_VALUE"));
                            System.out.println("SYSTEM_VALUE=" + System.getenv("SYSTEM_VALUE"));
                            System.out.println("Jenkins is fully up and running");
                        }
                    }
                    """);

                    var result = ith.gradleRunner()
                            .withArguments("testServer", "-PprojectValue=" + value, "-DsystemValue=" + value, "--info")
                            .build();

                    var encoded = Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
                    var bootstrap = Base64.getEncoder().encodeToString("quoted label".getBytes(StandardCharsets.UTF_8));
                    assertThat(result.getOutput())
                            .contains(
                                    "PROJECT_VALUE=" + encoded,
                                    "SYSTEM_VALUE=" + encoded,
                                    "BOOTSTRAP_VALUE=" + bootstrap);
                }));
    }
}
