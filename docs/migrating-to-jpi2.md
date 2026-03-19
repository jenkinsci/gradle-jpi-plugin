# Migrating from JPI to JPI2

Use this guide when you want to move a plugin build from `org.jenkins-ci.jpi` to `org.jenkins-ci.jpi2`.
The short version is that `jpi2` is the path for modern Gradle builds, while `jpi` is now the legacy option for older Gradle versions.

## Before you start

Plan to migrate when you need Gradle 8 or newer.
Keep a copy of your current `jpi` build script nearby while you make the switch.
If you need to stay on Gradle 7 or earlier for now, keep using `jpi` and refer to [legacy-jpi.md](legacy-jpi.md).

## 1. Swap the plugin ID

Replace the legacy plugin ID with `org.jenkins-ci.jpi2`.

```groovy
plugins {
    id 'org.jenkins-ci.jpi' version '<old-version>'
}
```

```kotlin
plugins {
    id("org.jenkins-ci.jpi2") version "<current-version>"
}
```

## 2. Declare repositories explicitly

The legacy plugin could configure repositories for you.
`jpi2` expects you to declare the repositories you need in your build.

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "jenkins-releases"
        url = uri("https://repo.jenkins-ci.org/releases/")
    }
}
```

## 3. Update the `jenkinsPlugin` block

Several legacy settings map directly to `jpi2`.
In Kotlin DSL, these can be written with direct assignment, which keeps the migrated block close to the old shape.

- `jenkinsVersion = "..."` stays `jenkinsVersion = "..."`.
- `shortName = "..."` becomes `pluginId = "..."`.
- `displayName = "..."` stays `displayName = "..."`.
- `url = "..."` becomes `homePage = uri("...")`.
- `compatibleSinceVersion = "..."` stays `compatibleSinceVersion = "..."`.
- `pluginFirstClassLoader = true` stays `pluginFirstClassLoader = true`.
- `maskClasses = "a b"` becomes `maskClasses.add("a")` and `maskClasses.add("b")`.
- `fileExtension = "hpi"` becomes `archiveExtension = "hpi"`.
- `developers { ... }` and `licenses { ... }` still exist.

Here is a minimal before and after example.

```groovy
jenkinsPlugin {
    jenkinsVersion = '2.440.1'
    shortName = 'example'
    displayName = 'Example Plugin'
    url = 'https://github.com/jenkinsci/example-plugin'
    fileExtension = 'hpi'
}
```

```kotlin
jenkinsPlugin {
    jenkinsVersion = "2.492.3"
    pluginId = "example"
    displayName = "Example Plugin"
    homePage = uri("https://github.com/jenkinsci/example-plugin")
    archiveExtension = "hpi"
}
```

## 4. Move legacy-only settings out of the extension

The `jpi2` extension intentionally drops several `jpi` convenience settings.
Handle these concerns directly in your build instead of expecting them on `jenkinsPlugin`.

- `configureRepositories` is no longer needed because repositories are declared explicitly in `repositories { ... }`.
- `configurePublishing` is no longer needed because `jpi2` always configures Maven publication wiring.
- `repoUrl`, `snapshotRepoUrl`, and `incrementalsRepoUrl` do not have direct `jpi2` extension equivalents.
- `gitHubUrl` and `scmTag` do not have direct `jpi2` extension equivalents.
- `disabledTestInjection` does not have a `jpi2` replacement on the extension.
- `enableSpotBugs()`, `enableCheckstyle()`, and `enableJacoco()` are not built into `jpi2`.

`workDir` is available on the `jpi2` extension and defaults to `${projectDir}/work`.
That value is used by both `server` and `hplRun`.
If you need a one-off override, pass `-Djpi2.workDir=...`.
The system property takes precedence over `jenkinsPlugin.workDir`.

If you need custom publishing metadata, configure the generated `MavenPublication` directly.

```kotlin
publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            scm {
                connection.set("scm:git:https://github.com/jenkinsci/example-plugin.git")
                developerConnection.set("scm:git:git@github.com:jenkinsci/example-plugin.git")
                tag.set("HEAD")
                url.set("https://github.com/jenkinsci/example-plugin")
            }
        }
    }
}
```

## 5. Update task customization

Most day-to-day task names stay familiar, but a few details change.

- `jpi` still builds the plugin archive.
- `server` still starts a local Jenkins instance.
- `hplRun` and `testHplRun` are new `jpi2` tasks for HPL-based development and verification.
- `localizeMessages` is the supported localization task in both plugins.
- `localizerOutputDir` should be replaced with direct configuration of the `localizeMessages` task.

```kotlin
tasks.named<org.jenkinsci.gradle.plugins.jpi2.localization.LocalizationTask>("localizeMessages") {
    outputDir.set(layout.buildDirectory.dir("generated-src/localizer"))
}
```

For the Jenkins HTTP port, set the `server.port` system property instead of using the legacy `server --port=...` convention.

```shell
./gradlew server -Dserver.port=8090
```

For the Jenkins work directory, use the extension for the steady-state default.

```kotlin
jenkinsPlugin {
    workDir = layout.projectDirectory.dir("custom-work")
}
```

`testServer` and `testHplRun` use temporary work directories automatically so they can run safely in parallel.
Set `jpi2.preserveTestWorkDir=true` when you want to keep those directories for debugging.

## 6. Decide how plugin versions should be produced

Legacy `jpi` users often relied on `generateGitVersion`.
`jpi2` still supports Git-based versions, but you now choose the source explicitly with `versionSource`.

```kotlin
jenkinsPlugin {
    versionSource.set(org.jenkinsci.gradle.plugins.jpi2.VersionSource.GIT)
    gitVersion {
        allowDirty.set(true)
        versionFormat.set("%d.%s")
    }
}
```

Use `VersionSource.PROJECT` when `project.version` should stay authoritative.
Use `VersionSource.FIXED` when you want the plugin to publish a fixed string that is separate from `project.version`.

## 7. Re-run your checks

After the migration, run your normal verification tasks and then boot Jenkins locally.
At a minimum, verify `./gradlew check`, `./gradlew jpi`, and either `./gradlew server` or `./gradlew hplRun`.
