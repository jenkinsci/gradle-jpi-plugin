@file:JvmName("JenkinsRepositoryExtensions")

package org.gradle.kotlin.dsl

import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.jenkinsci.gradle.plugins.jpi2.jenkinsIncrementals as jpi2JenkinsIncrementals
import org.jenkinsci.gradle.plugins.jpi2.jenkinsPublic as jpi2JenkinsPublic
import org.jenkinsci.gradle.plugins.jpi2.jenkinsSnapshots as jpi2JenkinsSnapshots

fun RepositoryHandler.jenkinsPublic(): MavenArtifactRepository = jpi2JenkinsPublic()

fun RepositoryHandler.jenkinsIncrementals(): MavenArtifactRepository = jpi2JenkinsIncrementals()

fun RepositoryHandler.jenkinsSnapshots(): MavenArtifactRepository = jpi2JenkinsSnapshots()