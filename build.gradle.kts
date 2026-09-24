buildscript {
    dependencies {
        classpath(libs.nebula.release.plugin)
    }
}
plugins {
    alias(libs.plugins.distribution.sha)
    alias(libs.plugins.test.logger) apply false
}

allprojects {
    group = "org.jenkins-ci.tools"
    apply(plugin = "nebula.release")
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
        apply(plugin = "com.adarshr.test-logger")
    }
}
