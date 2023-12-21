import okio.buffer
import okio.sink
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.nio.file.StandardOpenOption

buildscript {
    dependencies {
        classpath("com.squareup.okhttp3:okhttp:4.10.0")
    }
}
plugins {
    groovy
    `maven-publish`
    `kotlin-dsl`
    signing
    codenarc
    id("com.gradle.plugin-publish") version "1.2.0"
    `java-gradle-plugin`
    id("com.github.sghill.distribution-sha") version "0.4.0"
}

repositories {
    maven {
        url = uri("https://repo.jenkins-ci.org/public")
    }
    gradlePluginPortal()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(11))
    }
}

dependencies {
    compileOnly(libs.accessmodifier.checker)
    annotationProcessor(libs.sezpoz)
    implementation(gradleApi())
    compileOnly(libs.jgit)
    testImplementation(libs.jgit)
    compileOnly(libs.javapoet) {
        because("used for GenerateTestTask")
    }
    compileOnly(libs.jenkins.test.harness) {
        because("used for GenerateTestTask")
        isTransitive = false
    }
    compileOnly(libs.junit4) {
        because("used for GenerateTest")
    }
    compileOnly(libs.localizer.maven)
    implementation("com.github.spotbugs.snom:spotbugs-gradle-plugin:5.0.13")
    implementation(libs.sezpoz)
    implementation(localGroovy())
    testAnnotationProcessor(libs.sezpoz)
    testCompileOnly(libs.junit4) {
        because("used for generated tests with javapoet")
    }
    testImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
    testImplementation(libs.xmlunit)
    testImplementation(libs.commons.text)
    testImplementation(libs.javapoet)
    testImplementation(libs.accessmodifier.checker)
    testImplementation(libs.jenkins.core) {
        exclude(module = "groovy-all")
    }
    testImplementation(platform(libs.junit5.bom))
    testImplementation(libs.junit5.api)
    testImplementation(libs.assertj.core)
    testRuntimeOnly(libs.junit5.jupiter)
    testRuntimeOnly(libs.junit5.vintage)
}

publishing {
    publications {
        create<MavenPublication>("pluginMaven") {
            pom {
                name.set("Gradle JPI Plugin")
                description.set("The Gradle JPI plugin is a Gradle plugin for building Jenkins plugins")
                url.set("http://github.com/jenkinsci/gradle-jpi-plugin")
                scm {
                    url.set("https://github.com/jenkinsci/gradle-jpi-plugin")
                }
                licenses {
                    license {
                        name.set("Apache 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                        distribution.set("repo")
                    }
                }
                developers {
                    developer {
                        id.set("abayer")
                        name.set("Andrew Bayer")
                    }
                    developer {
                        id.set("kohsuke")
                        name.set("Kohsuke Kawaguchi")
                    }
                    developer {
                        id.set("daspilker")
                        name.set("Daniel Spilker")
                    }
                    developer {
                        id.set("sghill")
                        name.set("Steve Hill")
                    }
                }
            }
        }
    }
    repositories {
        maven {
            val path = if (version.toString().endsWith("SNAPSHOT")) "snapshots" else "releases"
            name = "JenkinsCommunity"
            url = uri("https://repo.jenkins-ci.org/${path}")
            credentials {
                username = project.stringProp("jenkins.username")
                password = project.stringProp("jenkins.password")
            }
        }
    }
}

signing {
    val signingKeyId: String? by project
    val signingKey: String? by project
    val signingPassword: String? by project
    useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    setRequired { setOf("jenkins.username", "jenkins.password").all { project.hasProperty(it) } }
}

tasks.addRule("Pattern: testGradle<ID>") {
    val taskName = this
    if (!taskName.startsWith("testGradle")) return@addRule
    val task = tasks.register(taskName)
    for (javaVersion in listOf(11)) {
        val javaSpecificTask = tasks.register<Test>("${taskName}onJava${javaVersion}") {
            val gradleVersion = taskName.substringAfter("testGradle")
            systemProperty("gradle.under.test", gradleVersion)
            setTestNameIncludePatterns(listOf("*IntegrationSpec"))
            javaLauncher.set(javaToolchains.launcherFor {
                languageVersion.set(JavaLanguageVersion.of(javaVersion))
            })
        }
        task.configure {
            dependsOn(javaSpecificTask)
        }
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        exceptionFormat = FULL
    }
}

codenarc {
    config = resources.text.fromFile(rootProject.file("config/codenarc/rules.groovy"))
    maxPriority3Violations = 261
}

tasks.named<CodeNarc>("codenarcTest").configure {
    enabled = false
}

group = "org.jenkins-ci.tools"
description = "Gradle plugin for building and packaging Jenkins plugins"

gradlePlugin {
    website = "https://wiki.jenkins-ci.org/display/JENKINS/Gradle+JPI+Plugin"
    vcsUrl = "https://github.com/jenkinsci/gradle-jpi-plugin"
    plugins {
        create("pluginMaven") {
            id = "org.jenkins-ci.jpi"
            implementationClass = "org.jenkinsci.gradle.plugins.jpi.JpiPlugin"
            displayName = "A plugin for building Jenkins plugins"
            description = "A plugin for building Jenkins plugins"
            tags = listOf("jenkins")
        }
    }
}

fun Project.stringProp(named: String): String? = findProperty(named) as String?

tasks.register("shadeLatestVersionNumber") {
    doLast {
        val response = okhttp3.OkHttpClient().newCall(okhttp3.Request.Builder().get()
                .url("https://raw.githubusercontent.com/jenkinsci/lib-version-number/master/src/main/java/hudson/util/VersionNumber.java")
                .build())
                .execute()
        if (!response.isSuccessful) {
            throw GradleException("${response.code} attempting to fetch latest hudson.util.VersionNumber")
        }
        val extension = project.extensions.getByType(JavaPluginExtension::class)
        val main = extension.sourceSets.getByName("main")
        val srcMainJava = main.java.srcDirs.single().toPath()
        val dest = srcMainJava.resolve("shaded/hudson/util/VersionNumber.java")
        val sink = dest.sink(StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).buffer()
        response.body?.use {
            it.source().use { input ->
                sink.use { output ->
                    output.writeAll(input)
                }
            }
        }
    }
}
