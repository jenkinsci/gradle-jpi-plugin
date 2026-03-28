# Gradle JPI plugin

[![CI](https://github.com/jenkinsci/gradle-jpi-plugin/workflows/CI/badge.svg)][ci-workflow]
[![Regression](https://github.com/jenkinsci/gradle-jpi-plugin/workflows/Regression/badge.svg)][regression-workflow]

[ci-workflow]: https://github.com/jenkinsci/gradle-jpi-plugin/actions?query=workflow%3ACI
[regression-workflow]: https://github.com/jenkinsci/gradle-jpi-plugin/actions?query=workflow%3ARegression

This repository contains two Gradle plugins for building Jenkins plugins.

- `org.jenkins-ci.jpi2` is the recommended plugin for modern Gradle builds and is the primary focus of this README.

- `org.jenkins-ci.jpi` remains available for older Gradle builds, and its documentation now lives in [docs/legacy-jpi.md](docs/legacy-jpi.md).
  It worked with Gradle 8.13.x and below.
  The last meaningful update to the legacy plugin was in 2025-02 in [v0.53.1](https://github.com/jenkinsci/gradle-jpi-plugin/releases/tag/v0.53.1).
  It was still being built until 2026-04, because we built both plugins in one build.
  As of [v0.58.0](https://github.com/jenkinsci/gradle-jpi-plugin/releases/tag/v0.53.1), the legacy plugin is no longer built or published, but the source code is still available in the repository.
  This allows us to modernize the gradle version used to build the plugin.

If you are moving an existing build forward, start with [docs/migrating-to-jpi2.md](docs/migrating-to-jpi2.md).

## Choosing a plugin

Use `org.jenkins-ci.jpi2` for Gradle 8 and newer.
Use `org.jenkins-ci.jpi` only when you must stay on an older Gradle release.
The legacy plugin has additional compatibility notes and historical configuration examples in [docs/legacy-jpi.md](docs/legacy-jpi.md).

## :warning: New OSS Plugins Rejected

As of December 2022, new OSS plugins will be [rejected by the Jenkins hosting team][213] until [hosting requirements][host-reqs] are met.
Hosting requirements are well-defined and expected to be a small amount of work, but this is not prioritized.
Contributions are welcome.
Existing OSS plugins can continue to use this plugin.
Plugins not hosted by the Jenkins infra team, such as internal-only plugins, are not impacted.

[213]: https://github.com/jenkinsci/gradle-jpi-plugin/issues/213
[host-reqs]: https://github.com/jenkinsci/gradle-jpi-plugin/milestone/10

## JPI2 Quick Start

Add the plugin to your `build.gradle.kts`.

```kotlin
plugins {
    id("org.jenkins-ci.jpi2") version "<current-version>"
}

group = "org.jenkins-ci.plugins"
version = "1.0.0-SNAPSHOT"
description = "A Jenkins plugin built with Gradle"

repositories {
    mavenCentral()
    jenkinsPublic()
}

`jenkinsPublic()` adds the Jenkins public repository (`https://repo.jenkins-ci.org/public/`).
`jenkinsIncrementals()` adds the Jenkins incrementals repository (`https://repo.jenkins-ci.org/incrementals/`).
`jenkinsSnapshots()` adds the Jenkins snapshots repository (`https://repo.jenkins-ci.org/snapshots/`).

jenkinsPlugin {
    jenkinsVersion.set("2.492.3")
    pluginId.set("hello-world")
    displayName.set("Hello World")
    homePage.set(uri("https://github.com/jenkinsci/hello-world-plugin"))
}

dependencies {
    implementation("org.jenkins-ci.plugins:git:5.7.0")
}
```

The plugin archive defaults to the `jpi` extension.
Set `archiveExtension` if you need `hpi` instead.

```kotlin
jenkinsPlugin {
    archiveExtension.set("hpi")
}
```

## JPI2 Defaults

`jpi2` defaults Jenkins core to `2.492.3`.
`jpi2` defaults the Jenkins test harness to `2414.v185474555e66`.
`jpi2` defaults the localizer generator to `1.31`.
You can override those defaults in the `jenkinsPlugin` block or in `gradle.properties`.

```properties
jenkins.version=2.492.3
jenkins.testharness.version=2414.v185474555e66
jenkins.localizer.version=1.31
```

## Dependencies

Use standard Gradle dependency configurations such as `implementation`, `api`, `runtimeOnly`, and `testImplementation`.
Use Gradle feature variants for optional Jenkins plugin dependencies.
The plugin will package plain Java libraries into the plugin archive and treat Jenkins plugin coordinates as plugin dependencies.

## Common Tasks

- `./gradlew jpi` builds the plugin archive.
- `./gradlew server` starts Jenkins with the built plugin installed.
- `./gradlew hplRun` starts Jenkins with the current project wired in through HPL files for faster local iteration.
- `./gradlew testServer` verifies that the installed-plugin launch boots successfully and then shuts down.
- `./gradlew testHplRun` verifies that the HPL-based launch boots successfully and then shuts down.
- `./gradlew localizeMessages` generates Java sources from `Messages.properties` files under `src/main/resources`.
- `./gradlew generateGitVersion` writes a Git-derived version file for builds that use `versionSource.set(VersionSource.GIT)`.

## JPI2 Features

### Manifest generation

`jpi2` generates Jenkins manifest entries for both the `jar` and `jpi` artifacts.
`Support-Dynamic-Loading` is derived from the generated `@Extension` metadata.

### Localization

`jpi2` registers a `localizeMessages` task that scans `src/main/resources/**/Messages.properties`.
Generated sources are written to `build/generated-src/localizer` by default and are added to the main Java source set automatically.
Configure the task directly if you want a different output directory.

```kotlin
tasks.named<org.jenkinsci.gradle.plugins.jpi2.localization.LocalizationTask>("localizeMessages") {
    outputDir.set(layout.buildDirectory.dir("custom-localizer"))
}
```

### Version sources

By default, `jpi2` uses `project.version`.
Set `versionSource` to `FIXED` to publish a fixed string.
Set `versionSource` to `GIT` to derive the version from the Git history.

```kotlin
jenkinsPlugin {
    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.GIT)
    gitVersion {
        allowDirty.set(true)
        versionFormat.set("%d.%s")
    }
}
```

### Running Jenkins locally

The `server` task listens on port `8080` by default.
Set `server.port` to change the HTTP port.

```shell
./gradlew server -Dserver.port=8090
```

You can also configure the task directly.

```kotlin
tasks.named<JavaExec>("server") {
    systemProperty("server.port", "8090")
}
```

Both `server` and `hplRun` use `${projectDir}/work` by default.
Set `jenkinsPlugin.workDir` to move that directory for normal development.
Set `jpi2.workDir` as a Gradle property when you need a one-off override.
The Gradle property takes precedence over the extension.

```kotlin
jenkinsPlugin {
    workDir = layout.projectDirectory.dir("custom-work")
}
```

```shell
./gradlew hplRun -Pjpi2.workDir=/tmp/jenkins-dev
```

`testServer` and `testHplRun` always launch Jenkins with a temporary work directory so they can run safely in parallel.
Those temporary directories are deleted after the task finishes.
Set `jpi2.preserveTestWorkDir=true` if you want to keep them for debugging.

## Migration And Legacy Docs

Use [docs/migrating-to-jpi2.md](docs/migrating-to-jpi2.md) when moving an existing plugin from `org.jenkins-ci.jpi` to `org.jenkins-ci.jpi2`.
Use [docs/legacy-jpi.md](docs/legacy-jpi.md) when you need the older `jpi` plugin documentation for historical or maintenance work.
Release process notes still live in [RELEASING.md](RELEASING.md).
Historical release notes still live in [CHANGELOG.md](CHANGELOG.md).
