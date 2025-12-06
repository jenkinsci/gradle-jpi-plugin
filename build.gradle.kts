plugins {
    alias(libs.plugins.nebula.release)
    alias(libs.plugins.distribution.sha)
}

allprojects {
    group = "org.jenkins-ci.tools"
}

subprojects {
    repositories {
        maven {
            url = uri("https://repo.jenkins-ci.org/public")
        }
        gradlePluginPortal()
    }

    plugins.withId("java") {
        configure<JavaPluginExtension> {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(17))
            }
        }
    }
}
