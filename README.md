# Gradle JPI plugin

This is a Gradle plugin for building [Jenkins](http://jenkins-ci.org)
plugins, written in Groovy or Java.

## Configuration

Add the following to your build.gradle:

```groovy
plugins {
  id 'org.jenkins-ci.jpi' version '0.22.0'
}

group = 'org.jenkins-ci.plugins'
version = '1.2.0-SNAPSHOT'
description = 'A description of your plugin'

jenkinsPlugin {
    // version of Jenkins core this plugin depends on, must be 1.420 or later
    coreVersion = '1.420'

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
    repoUrl = 'https://repo.jenkins-ci.org/releases'

    // URL used to deploy snapshots of the plugin, defaults to the value shown
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

If your plugin depends on other Jenkins plugins you can specify the dependencies in the following way:

	dependencies {
		jenkinsPlugins 'org.jenkinsci.plugins:git:1.1.15'
		optionalJenkinsPlugins 'org.jenkins-ci.plugins:ant:1.2'
		jenkinsTest 'org.jenkins-ci.main:maven-plugin:1.480'
	}

Adding the dependency to the `jenkinsPlugins` configuration will make all classes available during compilation and
also add the dependency to the manifest of your plugin. To define an optional dependency on a plugin then use
the `optionalJenkinsPlugins` configuration and to use a plugin only for testing, add a dependency to the `jenkinsTest`
configuration.

## Usage

* `gradle jpi` - Build the Jenkins plugin file, which can then be
  found in the build directory. The file will currently end in ".hpi".
* `gradle publishToMavenLocal` - Build the Jenkins plugin and install it into your
  local Maven repository.
* `gradle publish` - Deploy your plugin to
  the Jenkins Maven repository to be included in the Update Center.
* `gradle server` - Start a local instance of Jenkins (http://localhost:8080) with the plugin pre-installed for testing
  and debugging. The HTTP port can be changed with the `jenkins.httpPort` project or system property, e.g.
  `gradle server -Djenkins.httpPort=8082`.

## Debugging

It is possible to attach a remote debugger to the Jenkins instance started by `gradle server`. The `GRADLE_OPTS`
environment variable must be used to configure the JVM debug options.

    $ export GRADLE_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005
    $ ./gradlew server

The `server` task enables several debug options: `stapler.trace`, `stapler.jelly.noCache` and `debug.YUI`. This
increases the page load time. All option can be changed and new options can be added by passing them as system
properties to the Gradle command line.

    $ ./gradlew -Dstapler.jelly.noCache=false server

## Examples

Here are some real world examples of Jenkins plugins using the Gradle JPI plugin:

* [Job DSL Plugin](https://github.com/jenkinsci/job-dsl-plugin)
* [Selenium Axis Plugin](https://github.com/jenkinsci/selenium-axis-plugin)
* [more plugins built with Gradle](https://jenkins.ci.cloudbees.com/job/jenkins%20plugin%20%28gradle%29/)
