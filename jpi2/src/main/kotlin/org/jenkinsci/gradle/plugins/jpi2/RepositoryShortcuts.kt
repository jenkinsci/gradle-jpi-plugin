@file:JvmName("RepositoryShortcuts")

package org.jenkinsci.gradle.plugins.jpi2

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.plugins.ExtensionAware
import java.net.URI

internal const val JENKINS_PUBLIC_REPO_NAME = "jenkinsPublic"
internal val JENKINS_PUBLIC_REPO_URL = URI("https://repo.jenkins-ci.org/public/")
internal const val JENKINS_INCREMENTALS_REPO_NAME = "jenkinsIncrementals"
internal val JENKINS_INCREMENTALS_REPO_URL = URI("https://repo.jenkins-ci.org/incrementals/")
internal const val JENKINS_SNAPSHOTS_REPO_NAME = "jenkinsSnapshots"
internal val JENKINS_SNAPSHOTS_REPO_URL = URI("https://repo.jenkins-ci.org/snapshots/")
internal const val JENKINS_RELEASES_REPO_NAME = "jenkinsReleases"
internal val JENKINS_RELEASES_REPO_URL = URI("https://repo.jenkins-ci.org/releases/")
internal const val JENKINS_PUBLISH_REPO_NAME = "jenkinsPublish"
private val INCREMENTALS_PATTERN = Regex(".*-rc\\d+\\.\\w+")

fun RepositoryHandler.jenkinsPublic(): MavenArtifactRepository =
    maven {
        name = JENKINS_PUBLIC_REPO_NAME
        url = JENKINS_PUBLIC_REPO_URL
    }

fun RepositoryHandler.jenkinsIncrementals(): MavenArtifactRepository =
    maven {
        name = JENKINS_INCREMENTALS_REPO_NAME
        url = JENKINS_INCREMENTALS_REPO_URL
    }

fun RepositoryHandler.jenkinsSnapshots(): MavenArtifactRepository =
    maven {
        name = JENKINS_SNAPSHOTS_REPO_NAME
        url = JENKINS_SNAPSHOTS_REPO_URL
    }

private const val PROJECT_EXTRA_KEY = "org.jenkinsci.gradle.plugins.jpi2.project"

fun RepositoryHandler.publishToJenkins(): MavenArtifactRepository {
    val project = (this as ExtensionAware).extensions.extraProperties[PROJECT_EXTRA_KEY] as Project
    val repo = maven {
        name = JENKINS_PUBLISH_REPO_NAME
        credentials(PasswordCredentials::class.java)
    }
    project.afterEvaluate(object : Action<Project> {
        override fun execute(p: Project) {
            val version = p.version.toString()
            repo.url = when {
                version.endsWith("-SNAPSHOT") -> JENKINS_SNAPSHOTS_REPO_URL
                INCREMENTALS_PATTERN.matches(version) -> JENKINS_INCREMENTALS_REPO_URL
                else -> JENKINS_RELEASES_REPO_URL
            }
        }
    })
    return repo
}

fun registerRepositoryShortcuts(repositories: RepositoryHandler, project: Project) {
    if (repositories is ExtensionAware) {
        repositories.extensions.extraProperties[PROJECT_EXTRA_KEY] = project
        if (repositories.extensions.findByName("publishToJenkins") == null) {
            repositories.extensions.add(
                "publishToJenkins",
                object : Closure<MavenArtifactRepository>(repositories, repositories) {
                    @Suppress("unused")
                    fun doCall(): MavenArtifactRepository {
                        return repositories.publishToJenkins()
                    }
                }
            )
        }
    }
    registerRepositoryShortcuts(repositories)
}

fun registerRepositoryShortcuts(repositories: RepositoryHandler) {
    if (repositories is ExtensionAware) {
        val extensions = repositories.extensions
        if (extensions.findByName("jenkinsPublic") == null) {
            extensions.add("jenkinsPublic", object : Closure<MavenArtifactRepository>(repositories, repositories) {
                @Suppress("unused")
                fun doCall(): MavenArtifactRepository {
                    return repositories.jenkinsPublic()
                }
            })
        }
        if (extensions.findByName("jenkinsIncrementals") == null) {
            extensions.add("jenkinsIncrementals", object : Closure<MavenArtifactRepository>(repositories, repositories) {
                @Suppress("unused")
                fun doCall(): MavenArtifactRepository {
                    return repositories.jenkinsIncrementals()
                }
            })
        }
        if (extensions.findByName("jenkinsSnapshots") == null) {
            extensions.add("jenkinsSnapshots", object : Closure<MavenArtifactRepository>(repositories, repositories) {
                @Suppress("unused")
                fun doCall(): MavenArtifactRepository {
                    return repositories.jenkinsSnapshots()
                }
            })
        }
    }
}
