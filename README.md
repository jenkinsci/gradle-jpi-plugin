# Gradle JPI plugin

[![CI](https://github.com/jenkinsci/gradle-jpi-plugin/workflows/CI/badge.svg)][ci-workflow]
[![Regression](https://github.com/jenkinsci/gradle-jpi-plugin/workflows/Regression/badge.svg)][regression-workflow]

[ci-workflow]: https://github.com/jenkinsci/gradle-jpi-plugin/actions?query=workflow%3ACI
[regression-workflow]: https://github.com/jenkinsci/gradle-jpi-plugin/actions?query=workflow%3ARegression

This is a Gradle plugin for building [Jenkins](http://jenkins-ci.org)
plugins, written in Groovy or Java.

## Compatibility with Gradle versions

The latest version of the JPI plugin requires **Gradle 6+** to make use of advanced dependency management features.

For Gradle versions 4.x or 5.x, please use version `0.38.0` of the JPI plugin.

## Configuration

Add the following to your build.gradle:

```groovy
plugins {
  id 'org.jenkins-ci.jpi' version '0.39.0'
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

## Examples

Here are some real world examples of Jenkins plugins using the Gradle JPI plugin:

* [Synopsys Detect Plugin](https://github.com/jenkinsci/synopsys-detect-plugin)
* [Selenium Axis Plugin](https://github.com/jenkinsci/selenium-axis-plugin)
* [Doktor Plugin](https://github.com/jenkinsci/doktor-plugin)

[javaexecspec]: https://docs.gradle.org/current/javadoc/org/gradle/process/JavaExecSpec.html
