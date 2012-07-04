# Gradle JPI plugin

This is a Gradle plugin for building [Jenkins](http://jenkins-ci.org)
plugins, written in Groovy or Java.

## Configuration

Add the following to your build.gradle:

>        apply from:"https://raw.github.com/jenkinsci/gradle-jpi-plugin/master/install"
>        // ...or copy the contents of that URL into your build.gradle directly.
>
>        // Whatever other plugins you need to load.
>
>        group = "org.jenkins-ci.plugins"
>        version = "0.0.1-SNAPSHOT"    // Or whatever your version is.
>        description = "A description of your plugin"
>
>        jenkinsPlugin {
>            coreVersion = '1.420'                                               // Version of Jenkins core this plugin depends on.
>            displayName = 'Hello World plugin built with Gradle'                // Human-readable name of plugin.
>            url = 'http://wiki.jenkins-ci.org/display/JENKINS/SomePluginPage'   // URL for plugin on Jenkins wiki or elsewhere.
>            gitHubUrl = 'https://github.com/jenkinsci/some-plugin'              // Plugin URL on GitHub. Optional.
>
>            // The developers section is optional, and corresponds to the POM developers section.
>            developers {
>                developer {
>                    id 'abayer'
>                    name 'Andrew Bayer'
>                    email 'andrew.bayer@gmail.com'
>                }
>            }                           
>        }

Be sure to add the jenkinsPlugin { ... } section before any additional
repositories are defined in your build.gradle.

## Usage

* 'gradle jpi' - Build the Jenkins plugin file, which can then be
  found in the build directory. The file will currently end in ".hpi".
* 'gradle install' - Build the Jenkins plugin and install it into your
  local Maven repository.
* 'gradle uploadArchives' (or 'gradle deploy') - Deploy your plugin to
  the Jenkins Maven repository to be included in the Update Center.

## Caveats

* As of now, a minimum Jenkins core version of 1.420 is required.
