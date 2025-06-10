# Gradle JPI plugin

[![CI](https://github.com/jenkinsci/gradle-jpi-plugin/workflows/CI/badge.svg)][ci-workflow]
[![Regression](https://github.com/jenkinsci/gradle-jpi-plugin/workflows/Regression/badge.svg)][regression-workflow]

[ci-workflow]: https://github.com/jenkinsci/gradle-jpi-plugin/actions?query=workflow%3ACI
[regression-workflow]: https://github.com/jenkinsci/gradle-jpi-plugin/actions?query=workflow%3ARegression

This is a Gradle plugin for building [Jenkins](http://jenkins-ci.org)
plugins, written in Groovy or Java.

## :warning: New OSS Plugins Rejected

As of December 2022, new OSS plugins will be [rejected by the Jenkins hosting team][213] until [hosting requirements][host-reqs] are met.

Hosting requirements are well-defined and expected to be a small amount of work, but this is not prioritized.
Contributions are welcome.
Existing OSS plugins can continue to use this plugin.
Plugins not hosted by the Jenkins infra team (internal-only plugins) are not impacted.

[213]: https://github.com/jenkinsci/gradle-jpi-plugin/issues/213
[host-reqs]: https://github.com/jenkinsci/gradle-jpi-plugin/milestone/10

## Compatibility with Gradle versions

The latest version of the JPI plugin requires **Gradle 7.1 or later** to make use of modern Gradle APIs.

For Gradle versions 6.3-6.9.x, please use version `0.50.0` of the JPI plugin.

For Gradle versions 6.0-6.2.1, please use version `0.46.0` of the JPI plugin.

For Gradle versions 4.x or 5.x, please use version `0.38.0` of the JPI plugin.

## Configuration

Add the following to your build.gradle:

```groovy
plugins {
  id 'org.jenkins-ci.jpi' version '0.52.0'
}

group = 'org.jenkins-ci.plugins'
version = '1.2.0-SNAPSHOT'
description = 'A description of your plugin'

jenkinsPlugin {
    // version of Jenkins core this plugin depends on, must be 1.420 or later
    jenkinsVersion = '1.420'

    // ID of the plugin, defaults to the project name without trailing '-plugin'
    shortName = 'hello-world'

    // human-readable name of plugin
    displayName = 'Hello World plugin built with Gradle'

    // URL for plugin on Jenkins wiki or elsewhere
    url = 'http://wiki.jenkins-ci.org/display/JENKINS/SomePluginPage'

    // plugin URL on GitHub, optional
    gitHubUrl = 'https://github.com/jenkinsci/some-plugin'
  
    // scm tag eventually set in the published pom, optional
    scmTag = 'v1.0.0'

    // use the plugin class loader before the core class loader, defaults to false
    pluginFirstClassLoader = true

    // optional list of package prefixes that your plugin doesn't want to see from core
    maskClasses = 'groovy.grape org.apache.commons.codec'

    // optional version number from which this plugin release is configuration-compatible
    compatibleSinceVersion = '1.1.0'

    // set the directory from which the development server will run, defaults to 'work'
    workDir = file('/tmp/jenkins')

    // URL used to deploy the plugin, defaults to the value shown
    // the system property 'jpi.repoUrl' can be used to override this option
    repoUrl = 'https://repo.jenkins-ci.org/releases'

    // URL used to deploy snapshots of the plugin, defaults to the value shown
    // the system property 'jpi.snapshotRepoUrl' can be used to override this option
    snapshotRepoUrl = 'https://repo.jenkins-ci.org/snapshots'

    // enable injection of additional tests for checking the syntax of Jelly and other things
    disabledTestInjection = false

    // the output directory for the localizer task relative to the project root, defaults to the value shown
    localizerOutputDir = "${project.buildDir}/generated-src/localizer"

    // disable configuration of Maven Central, the local Maven cache and the Jenkins Maven repository, defaults to true
    configureRepositories = false

    // skip configuration of publications and repositories for the Maven Publishing plugin, defaults to true
    configurePublishing = false

    // plugin file extension, either 'jpi' or 'hpi', defaults to 'hpi'
    fileExtension = 'hpi'

    // the developers section is optional, and corresponds to the POM developers section
    developers {
        developer {
            id 'abayer'
            name 'Andrew Bayer'
            email 'andrew.bayer@gmail.com'
        }
    }

    // the licenses section is optional, and corresponds to the POM licenses section
    licenses {
        license {
            name 'Apache License, Version 2.0'
            url 'https://www.apache.org/licenses/LICENSE-2.0.txt'
            distribution 'repo'
            comments 'A business-friendly OSS license'
        }
    }

    // Git based version generation is optional
    gitVersion {
        // Don't fail if changes are not committed (default: false)
        allowDirty = true
        // Customize version format (default: %d.%s where %d is the commit depth, %s the abbreviated sha)
        versionFormat = 'rc-%d.%s'
        // Sanitize the hash according to Jenkins requirements (default: false)  
        sanitize = true 
        // Customize abbreviated sha length (default: 12)
        abbrevLength = 10
        // Customize git root (default: project directory)
        gitRoot = file('/some/external/git/repo')
    }

    // "Incrementals" custom repository (default: https://repo.jenkins-ci.org/incrementals)
    incrementalsRepoUrl = 'https://custom'

    // Enable quality check plugins
    enableSpotBugs()
    enableCheckstyle()
    enableJacoco()
}
```

Be sure to add the `jenkinsPlugin { ... }` section before any additional
repositories are defined in your build.gradle.

## Dependencies on other Jenkins Plugins

If your plugin depends on other Jenkins plugins, you can use the same _configurations_ as in Gradle's `java-libary` plugin.
See [the documentation](https://docs.gradle.org/current/userguide/java_library_plugin.html#sec:java_library_separation) for details on the difference of `api` and `implementation` dependencies.
For _optional dependencies_, you can use Gradle's [feature variants](https://docs.gradle.org/current/userguide/feature_variants.html).

You can define both dependencies to Jenkins plugins and plain Java libraries.
The JPI plugin will figure out what you are depending on and process it accordingly (Java libraries will be packaged in the your Jenkins plugin's hpi/jpi file).

The additional `jenkinsServer` configuration can be used to install extra plugins for the `server` task (see below).

Examples:

    java {
        // define features for 'optional dependencies'
        registerFeature('ant') {
            usingSourceSet(sourceSets.main)
        }
    }

    dependencies {
        implementation 'org.jenkinsci.plugins:git:1.1.15'
        api 'org.jenkins-ci.plugins:credentials:1.9.4'

        // dependency of the (optional) ant feature
        antImplementation 'org.jenkins-ci.plugins:ant:1.2'

        // dependency for testing only
        testImplementation 'org.jenkins-ci.main:maven-plugin:1.480'

        // addition dependencies for manual tests on the server started with `gradle server`
        jenkinsServer 'org.jenkins-ci.plugins:ant:1.2'
    }


## Usage

* `gradle jpi` - Build the Jenkins plugin file, which can then be
  found in the build directory. The file will currently end in ".hpi".
* `gradle publishToMavenLocal` - Build the Jenkins plugin and install it into your
  local Maven repository.
* `gradle publish` - Deploy your plugin to
  the Jenkins Maven repository to be included in the Update Center.
* `gradle server` - Start a local instance of Jenkins with the plugin pre-installed for testing
  and debugging.

### Running Jenkins Locally

The `server` task creates a [hpl][hpl], installs plugins, and starts up Jenkins on port 8080. The server runs
in the foreground.

Plugins added to any of these configurations will be installed to `${jenkinsPlugin.workDir}/plugins`:
- `api`
- `implementation`
- `runtimeOnly`
- `jenkinsServer`

#### Default System Properties

Jenkins starts up with these system properties set to `true`:
- `stapler.trace`
- `stapler.jelly.noCache`
- `debug.YUI`
- `hudson.Main.development`

Each can be overridden as described in the _Customizing Further_ section below.

[hpl]: https://wiki.jenkins.io/display/JENKINS/Plugin+Structure#PluginStructure-DebugPluginLayout:.hpl


#### Customizing Port

Jenkins starts by default on port 8080. This can be changed with the `--port` flag or `port` property.

For example to run on port 7000:

```
$ ./gradlew server --port=7000
```

or in build.gradle:

```groovy
tasks.named('server').configure {
    it.port.set(7000)
}
```

#### Customizing Further

The `server` task accepts a [`JavaExecSpec`][javaexecspec] that allows extensive customization.

Here's an example with common options:

```groovy
tasks.named('server').configure {
    execSpec {
        systemProperty 'some.property', 'true'
        environment 'SOME_ENV_VAR', 'HelloWorld'
        maxHeapSize = '2g'
    }
}
```


#### Debugging

To start Jenkins in a suspended state with a debug port of 5005, add the `--debug-jvm` flag:

```
$ ./gradlew server --debug-jvm

> Task :server
Listening for transport dt_socket at address: 5005
```

Debug options can be customized by the `server` task's `execSpec` action:

```groovy
tasks.named('server').configure {
    execSpec {
        debugOptions {
            port.set(6000)
            suspend.set(false)
        }
    }
}
```
```
$ ./gradlew server --debug-jvm

> Task :server
Listening for transport dt_socket at address: 6000
```



#### Additional Server Dependencies

Any additional dependencies for the `server` task's classpath can be added to the `jenkinsServerRuntimeOnly`
configuration. This can be useful for alternative logging implementations.


### Checking for Restricted APIs

Starting with v0.41.0, we now [check for using `@Restricted`][restricted] types, methods, and fields.

Initially this functionality will warn by default (see [#176][176]), but will eventually fail the build. To opt-into failing
the build now, add this configuration to build.gradle:

```gradle
tasks.named('checkAccessModifier').configure {
    ignoreFailures.set(false)
}
```

`checkAccessModifier` will only be cached on success if `ignoreFailures` is `false`.

### Disabling appending timestamp to the SNAPSHOT plugin version

By default, `generateJenkinsManifest` task appends the current timestamp and the current username to the `-SNAPSHOT` plugin version.
It leads to a non-repeatable outputs that affect the task cacheability.

To opt-out from modifying the `-SNAPSHOT` plugin version, add this configuration to build.gradle:

```gradle
tasks.named('generateJenkinsManifest').configure {
    dynamicSnapshotVersion.set(false)
}
```

### Using Git based version
[JEP-229](https://github.com/jenkinsci/jep/blob/master/jep/229/README.adoc#version-format) outlines requirements for creating sensible version numbers automatically.
The plugin registers a `generateGitVersion` task that generates a Git based version in a text file (1st line an abbreviated hash, 2nd line the full hash). 
This version scheme is typically used on ci.jenkins.io by first generating the version and then setting it when 
building with `-Pversion=${versionFile.readLines()[0]}`. This works fine as long as no version is set in `build.gradle`.

See [Configuration](#configuration) to customize the generation.

### Using Jenkins "incrementals" repository
[JEP-305](https://github.com/jenkinsci/jep/tree/master/jep/305) specifies how to deploy incremental versions.
This applies mainly for the ci.jenkins.io infrastructure, for Gradle the logic is defined in https://github.com/jenkins-infra/pipeline-library/blob/master/vars/buildPluginWithGradle.groovy.

For local builds, the plugin defines the https://repo.jenkins-ci.org/incrementals/ repository. The `publish` task will not publish to this one by default, instead one should call
the `publish*PublicationToJenkinsIncrementalsRepository` task(s) separately (so `publishMavenJpiPublicationToJenkinsIncrementalsRepository` for the default publication)
It's also possible to specify a different repository, see [Configuration](#configuration).

### Enabling quality checks
To eventually publish reports to ci.jenkins.io, one can enable SpotBugs, Checkstyle or JaCoCo plugins:
```
jenkinsPlugin {
    enableSpotBugs()
    enableCheckstyle()
    enableJacoco()
}
```
When enabled, plugins are configured with sensitive defaults: only xml reports, checkstyle rules default to sun-checks.xml... still the plugins can be configured as usual, see their corresponding docs.

## Disabling SHA256 and SHA512 checksums when releasing a plugin

This section applies to the warning:

```
Cannot upload checksum for module-maven-metadata.xml. Remote repository doesn't support sha-256. Error: Could not PUT 'https://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/<shortname>/maven-metadata.xml.sha256'. Received status code 403 from server: Forbidden
Cannot upload checksum for module-maven-metadata.xml. Remote repository doesn't support sha-512. Error: Could not PUT 'https://repo.jenkins-ci.org/releases/org/jenkins-ci/plugins/<shortname>/maven-metadata.xml.sha512'. Received status code 403 from server: Forbidden
```

When performing a release via `gradle publish`, gradle will automatically try to upload artifacts with SHA 256 and 512 checksums. This is not currently supported in the public artifact repository for Jenkins.  To disable this, you can pass [a command line argument][shasum] `gradle publish -Dorg.gradle.internal.publish.checksums.insecure` or include a `gradle.properties` file with the line `org.gradle.internal.publish.checksums.insecure=true`.


[shasum]: https://docs.gradle.org/6.0.1/release-notes.html#publication-of-sha256-and-sha512-checksums

## Gradle 4+

Note that Gradle 4.0 changed the default layout of the classes folders. Where Gradle 3.x put all classes of groovy and java code into a single directory, Gradle 4 by default creates separate directories for all languages. Unfortunately, this breaks the way
SezPoz (the library indexing the Extension annotations) works, meaning that all annotations from java code are effectively ignored.

If you combine java and groovy code and both provide extensions you need to either:

- Use joint compilation, i.e. put your java source files into the groovy source path (src/main/groovy)
- or force Gradle to use the old layout by including something like `sourceSets.main.output.classesDir = new File(buildDir, "classes/main")` in your build.gradle as a workaround.

# JPI2 Plugin (Next Generation)

The JPI2 plugin (`org.jenkins-ci.jpi2`) is a modernized alternative to the original JPI plugin, designed with simpler dependency management.
The JPI plugin (`org.jenkins-ci.jpi`) is still available, but parts of it don't work with Gradle 8+.

## JPI2 Configuration

Add the following to your `build.gradle.kts`.

```kotlin
plugins {
    id("org.jenkins-ci.jpi2")
}

group = "com.example"
version = "1.0.0"

repositories {
    mavenCentral()
    maven {
        name = "jenkins-releases"
        url = uri("https://repo.jenkins-ci.org/releases/")
    }
}

dependencies {
    implementation("org.jenkins-ci.plugins:git:5.7.0")        // Plugin dependency
    implementation("com.openai:openai-java:2.5.0")            // Library dependency
}
```

## JPI2 Version Configuration

Configure Jenkins and test harness versions in `gradle.properties`:

```properties
jenkins.version=2.492.3
jenkins.testharness.version=2414.v185474555e66
```

### Server Customization

The server task can be customized for different ports. The default is `8080`.

```kotlin
tasks.named<JavaExec>("server") {
    systemProperty("server.port", "8090")
}
```

That can also be set on the command line:

```shell
./gradlew server -Dserver.port=8090
```

## Examples

Here are some real world examples of Jenkins plugins using the Gradle JPI plugin:

* [Job DSL Plugin](https://github.com/jenkinsci/job-dsl-plugin)
* [Selenium Axis Plugin](https://github.com/jenkinsci/selenium-axis-plugin)
* [Doktor Plugin](https://github.com/jenkinsci/doktor-plugin)

[javaexecspec]: https://docs.gradle.org/current/javadoc/org/gradle/process/JavaExecSpec.html
[restricted]: https://tiny.cc/jenkins-restricted
[176]: https://github.com/jenkinsci/gradle-jpi-plugin/issues/176
