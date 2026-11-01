@file:JvmName("RepositoryShortcuts")

package org.jenkinsci.gradle.plugins.jpi2

import groovy.lang.Closure
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

/** Adds the Jenkins public Maven repository for plugin and core dependencies. */
fun RepositoryHandler.jenkinsPublic(): MavenArtifactRepository =
    maven {
        name = JENKINS_PUBLIC_REPO_NAME
        url = JENKINS_PUBLIC_REPO_URL
    }

/** Adds the Jenkins incrementals Maven repository. */
fun RepositoryHandler.jenkinsIncrementals(): MavenArtifactRepository =
    maven {
        name = JENKINS_INCREMENTALS_REPO_NAME
        url = JENKINS_INCREMENTALS_REPO_URL
    }

/** Adds the Jenkins snapshots Maven repository. */
fun RepositoryHandler.jenkinsSnapshots(): MavenArtifactRepository =
    maven {
        name = JENKINS_SNAPSHOTS_REPO_NAME
        url = JENKINS_SNAPSHOTS_REPO_URL
    }

private const val PROJECT_EXTRA_KEY = "org.jenkinsci.gradle.plugins.jpi2.project"

/** Adds the Jenkins publishing repository selected by the project version. */
fun RepositoryHandler.publishToJenkins(): MavenArtifactRepository {
    val project = (this as ExtensionAware).extensions.extraProperties[PROJECT_EXTRA_KEY] as Project
    val repositoryUrl =
        project.providers.provider {
            val projectVersion =
                project.extensions
                    .findByType(JenkinsPluginExtension::class.java)
                    ?.getEffectiveVersion()
                    ?.get()
                    ?: project.version.toString()
            when {
                projectVersion.endsWith("-SNAPSHOT") -> JENKINS_SNAPSHOTS_REPO_URL
                INCREMENTALS_PATTERN.matches(projectVersion) -> JENKINS_INCREMENTALS_REPO_URL
                else -> JENKINS_RELEASES_REPO_URL
            }
        }
    return maven {
        name = JENKINS_PUBLISH_REPO_NAME
        setUrl(repositoryUrl)
        credentials(PasswordCredentials::class.java)
    }
}

/** Registers repository shortcuts, including publishing, for the given project. */
fun registerRepositoryShortcuts(
    repositories: RepositoryHandler,
    project: Project,
) {
    if (repositories is ExtensionAware) {
        repositories.extensions.extraProperties[PROJECT_EXTRA_KEY] = project
        if (repositories.extensions.findByName("publishToJenkins") == null) {
            repositories.extensions.add(
                "publishToJenkins",
                object : Closure<MavenArtifactRepository>(repositories, repositories) {
                    @Suppress("unused")
                    fun doCall(): MavenArtifactRepository = repositories.publishToJenkins()
                },
            )
        }
    }
    registerRepositoryShortcuts(repositories)
}

/** Registers Jenkins dependency repository shortcuts on the repository handler. */
fun registerRepositoryShortcuts(repositories: RepositoryHandler) {
    if (repositories is ExtensionAware) {
        val extensions = repositories.extensions
        if (extensions.findByName("jenkinsPublic") == null) {
            extensions.add(
                "jenkinsPublic",
                object : Closure<MavenArtifactRepository>(repositories, repositories) {
                    @Suppress("unused")
                    fun doCall(): MavenArtifactRepository = repositories.jenkinsPublic()
                },
            )
        }
        if (extensions.findByName("jenkinsIncrementals") == null) {
            extensions.add(
                "jenkinsIncrementals",
                object : Closure<MavenArtifactRepository>(repositories, repositories) {
                    @Suppress("unused")
                    fun doCall(): MavenArtifactRepository = repositories.jenkinsIncrementals()
                },
            )
        }
        if (extensions.findByName("jenkinsSnapshots") == null) {
            extensions.add(
                "jenkinsSnapshots",
                object : Closure<MavenArtifactRepository>(repositories, repositories) {
                    @Suppress("unused")
                    fun doCall(): MavenArtifactRepository = repositories.jenkinsSnapshots()
                },
            )
        }
    }
}