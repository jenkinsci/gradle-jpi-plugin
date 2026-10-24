buildscript {
    dependencies {
        classpath(libs.nebula.release.plugin)
    }
}
plugins {
    alias(libs.plugins.distribution.sha)
    alias(libs.plugins.test.logger) apply false
    alias(libs.plugins.test.retry) apply false
    alias(libs.plugins.spotless)
}

val isWindows =
    providers
        .systemProperty("os.name")
        .map { it.startsWith("Windows", ignoreCase = true) }
        .get()
val voltaHome =
    providers.environmentVariable("VOLTA_HOME").orNull?.let { file(it) }
        ?: if (isWindows) {
            providers.environmentVariable("LOCALAPPDATA").orNull?.let { file(it).resolve("Volta") }
        } else {
            file(providers.systemProperty("user.home").get()).resolve(".volta")
        }
val voltaBin = voltaHome?.resolve("bin")
val voltaNpm = voltaBin?.resolve(if (isWindows) "npm.cmd" else "npm")?.takeIf { it.isFile }
val voltaNode = voltaBin?.resolve(if (isWindows) "node.exe" else "node")?.takeIf { it.isFile }

allprojects {
    group = "org.jenkins-ci.tools"
    plugins.apply("nebula.release")
}

repositories {
    mavenCentral()
}

subprojects {
    repositories {
        maven {
            url = uri("https://repo.jenkins-ci.org/public")
            mavenContent {
                excludeGroup("commons-io")
                excludeGroup("org.apache.commons")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    plugins.withId("java") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
        plugins.apply("com.adarshr.test-logger")
        plugins.apply("org.gradle.test-retry")
        tasks.withType<Test>().configureEach {
            retry {
                // Integration tests launch nested Gradle builds and Jenkins JVMs, which occasionally
                // trip over transient Gradle-internal races on CI runners. Locally, failures should
                // surface immediately so they get investigated rather than hidden.
                if (providers.environmentVariable("CI").isPresent) {
                    maxRetries.set(2)
                    maxFailures.set(5)
                }
            }
        }
    }
}

spotless {
    java {
        palantirJavaFormat()
        target("**/*.java")
    }
    kotlin {
        ktlint()
        target("**/*.kt")
    }
    kotlinGradle {
        ktlint()
        target("**/*.gradle.kts")
    }
    yaml {
        val prettier = prettier()
        voltaNpm?.let(prettier::npmExecutable)
        voltaNode?.let(prettier::nodeExecutable)
        target("**/*.yaml", "**/*.yml")
    }
    json {
        val prettier = prettier()
        voltaNpm?.let(prettier::npmExecutable)
        voltaNode?.let(prettier::nodeExecutable)
        target("**/*.json")
    }
}