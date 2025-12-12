plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.plugin.publish)
    alias(libs.plugins.nebula.release)
    `java-gradle-plugin`
}

description = "V2 Gradle plugin for building Jenkins plugins with Gradle 8+"

dependencies {
    implementation(gradleApi())
    compileOnly("org.jetbrains:annotations:24.0.1")

    // Test dependencies
    testImplementation(testFixtures(project(":core")))
    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testImplementation(libs.awaitility)
    testImplementation(libs.commons.io)
    testImplementation("org.apache.maven:maven-model:3.9.9")
    testImplementation("com.google.guava:guava:31.1-jre")
    testCompileOnly("org.jetbrains:annotations:24.0.1")
    testRuntimeOnly(libs.junit5.jupiter)
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

gradlePlugin {
    plugins {
        create("pluginV2") {
            id = "org.jenkins-ci.jpi2"
            implementationClass = "org.jenkinsci.gradle.plugins.jpi2.V2JpiPlugin"
            displayName = "A plugin for building Jenkins plugins"
            website.set("https://github.com/jenkinsci/gradle-jpi-plugin")
            vcsUrl.set("https://github.com/jenkinsci/gradle-jpi-plugin")
            description = "A plugin for building Jenkins plugins with Gradle 8+"
            tags.set(listOf("jenkins"))
        }
    }
}

tasks.addRule("Pattern: testGradle<ID>") {
    val taskName = this
    if (!taskName.startsWith("testGradle")) return@addRule
    val task = tasks.register(taskName)
    for (javaVersion in listOf(17)) {
        val javaSpecificTask = tasks.register<Test>("${taskName}onJava${javaVersion}") {
            val gradleVersion = taskName.substringAfter("testGradle")
            systemProperty("gradle.under.test", gradleVersion)
            setTestNameIncludePatterns(listOf("*IntegrationTest"))
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(javaVersion))
            })
        }
        task.configure {
            dependsOn(javaSpecificTask)
        }
    }
}

val checkPhase = tasks.named("check")
val publishToGradle = tasks.named("publishPlugins")
publishToGradle.configure {
    dependsOn(checkPhase)
}

rootProject.tasks.named("postRelease").configure {
    dependsOn(publishToGradle)
}
