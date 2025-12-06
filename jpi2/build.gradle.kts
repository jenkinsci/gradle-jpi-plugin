plugins {
    `java-library`
    `maven-publish`
    signing
    alias(libs.plugins.plugin.publish)
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

publishing {
    publications {
        create<MavenPublication>("pluginV2") {
            pom {
                name.set("Gradle JPI Plugin V2")
                description.set("V2 plugin for building Jenkins plugins with Gradle 8+")
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

fun Project.stringProp(named: String): String? = findProperty(named) as String?
