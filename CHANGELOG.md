## 0.23.0 (unreleased)

  * updated to Gradle 3.5.1
  * fixed transitive plugin dependency resolution, plugin dependencies must not longer be specified with artifact only
    notation
    ([JENKINS-35412](https://issues.jenkins-ci.org/browse/JENKINS-35412))

## 0.22.0 (2017-02-23)

  * fixed compatibility with Gradle 3.4
  * removed the classes `org.jenkinsci.gradle.plugins.jpi.Jpi` and `org.jenkinsci.gradle.plugins.jpi.JpiComponent` as
    they are no longer used by the plugin
  * the `jpi` task has been replaced by the standard `war` task and changed to a no-op tasks that depends on the `war`
    task, use the `war` task to customize the JPI/HPI archive

## 0.21.0 (2016-12-02)

  * updated to Gradle 3.2.1
  * strip version from plugin file names in `test-dependencies` directory to mimic the behavior of the Maven HPI plugin
    better
  * allow to configure licenses for the generated POM
    ([#83](https://github.com/jenkinsci/gradle-jpi-plugin/pull/83))

## 0.20.0 (2016-11-17)

  * updated to Gradle 3.2
  * dropped support for building with Java 6

## 0.19.0 (2016-11-16)

  * do not apply "maven-publish" plugin if `configurePublishing` is `false`
    ([#80](https://github.com/jenkinsci/gradle-jpi-plugin/pull/80))
  * fixed problem with missing `Plugin-Class` attribute in generated manifest
    ([JENKINS-38920](https://issues.jenkins-ci.org/browse/JENKINS-38920))

## 0.18.1 (2016-05-22)

  * Fixed Servlet API dependency for Jenkins 2.0 and later
    ([JENKINS-34945](https://issues.jenkins-ci.org/browse/JENKINS-34945))

## 0.18.0 (2016-05-18)

  * removed reference to parent POM from generated POM and declare relevant dependencies directly
    ([JENKINS-34874](https://issues.jenkins-ci.org/browse/JENKINS-34874))

## 0.17.0 (2016-04-26)

  * updated Gradle to version 2.13
  * copy plugin dependencies to `test-dependencies` directory instead of `plugins` directory to mimic the behavior of
    the Maven HPI plugin
    ([#74](https://github.com/jenkinsci/gradle-jpi-plugin/pull/74))

## 0.16.0 (2016-03-22)

  * updated Gradle to version 2.8
  * fixed a classpath problem in the `localizer` task
    ([#71](https://github.com/jenkinsci/gradle-jpi-plugin/pull/71))
  * allow to specify the HTTP port for the `server` task with the `jenkins.httpPort` project or system property
    ([JENKINS-31881](https://issues.jenkins-ci.org/browse/JENKINS-31881))
  * changed default repository URL to `https://repo.jenkins-ci.org/releases`
  * changed default snapshot repository URL to `https://repo.jenkins-ci.org/snapshots`
  * use HTTPS URLs for `repo.jenkins-ci.org`

## 0.15.0 (2016-02-18)

  * updated Gradle to version 2.5
  * removed the `stapler` task and the `staplerStubDir` property, Gradle will generate stubs for annotation processors
    (see [Support for “annotation processing” of Groovy code](https://docs.gradle.org/2.4/release-notes#support-for-%E2%80%9Cannotation-processing%E2%80%9D-of-groovy-code))
  * set outputs for `insertTest` and `generate-test-hpl` tasks to fix problems with incremental builds
  * fixed injected test suite to avoid compile time warnings

## 0.14.3 (2016-02-17)

  * make [SezPoz](https://github.com/jglick/sezpoz) quiet by default, use `--info` or `--debug` to get output

## 0.14.2 (2016-02-16)

  * use Jenkins Test Harness 2.0 for core versions greater than 1.644
    ([JENKINS-32478](https://issues.jenkins-ci.org/browse/JENKINS-32478))

## 0.14.1 (2015-11-27)

  * added `Extension-Name` entry to `MANIFEST.MF`
    ([JENKINS-31542](https://issues.jenkins-ci.org/browse/JENKINS-31542))

## 0.14.0 (2015-11-13)

  * copy HPI/JPI dependencies from {{jenkinsTest}} configuration to {{plugin}} folder on test classpath
    ([JENKINS-31451](https://issues.jenkins-ci.org/browse/JENKINS-31451))

## 0.13.1 (2015-11-05)

  * removed install script
  * fixed a regression introduced in 0.13.0 which causes the manifest to be empty
    ([JENKINS-31426](https://issues.jenkins-ci.org/browse/JENKINS-31426))

## 0.13.0 (2015-10-27)

  * fixed a problem with incremental builds
    ([JENKINS-31186](https://issues.jenkins-ci.org/browse/JENKINS-31186))
  * fixed StackOverflowError when using Gradle 2.8
    ([JENKINS-31188](https://issues.jenkins-ci.org/browse/JENKINS-31188))

## 0.12.2 (2015-08-17)

  * allow to override system properties for the embedded Jenkins instance which is started by the `server` task
    ([JENKINS-29297](https://issues.jenkins-ci.org/browse/JENKINS-29297))

## 0.12.1 (2015-06-17)

  * added a dependency from the `assemble` to the `jpi` task to hook into the standard lifecycle

## 0.12.0 (2015-06-11)

  * allow JPI/HPI file extension and packaging type to be configured by the `fileExtension` property, which will default
    to `hpi`
    ([JENKINS-28408](https://issues.jenkins-ci.org/browse/JENKINS-28408))

## 0.11.1 (2015-05-08)

  * changed packaging in generated POM back to jpi to fix a regression bug
    ([JENKINS-28305](https://issues.jenkins-ci.org/browse/JENKINS-28305))

## 0.11.0 (2015-04-28)

  * add manifest to the plugin JAR file
    ([JENKINS-27994](https://issues.jenkins-ci.org/browse/JENKINS-27994))
  * removed `jenkinsCore` dependencies from generated POM because those are inherited from the parent POM
  * removed runtime scope from plugin and compile dependencies in generated POM
  * mark optional plugins in generated POM
  * changed packaging in generated POM to hpi for compatibility with maven-hpi-plugin

## 0.10.2 (2015-04-01)

  * copy plugin dependencies to test resources output directory to be able to use `@WithPlugin` in tests

## 0.10.1 (2015-03-23)

  * localize only `Message.properties` files
    ([JENKINS-27451](https://issues.jenkins-ci.org/browse/JENKINS-27451))

## 0.10.0 (2015-02-28)

  * renamed the `localizerDestDir` option to `localizerOutputDir`, changed its type to `Object` and fixed the
    configuration to recognize a non-default value
  * add a JAR dependency for each HPI/JPI dependency to the `testCompile` configuration
    ([JENKINS-17129](https://issues.jenkins-ci.org/browse/JENKINS-17129))
  * added `configureRepositories` option to be able to skip configuration of repositories
    ([JENKINS-17130](https://issues.jenkins-ci.org/browse/JENKINS-17130))
  * added `configurePublishing` option to be able to skip configuration of publications or repositories for the Maven
    Publishing plugin

## 0.9.1 (2015-02-17)

  * use classpath from jpi task to build classpath for server task to allow customization in build scripts
    ([JENKINS-26377](https://issues.jenkins-ci.org/browse/JENKINS-26377))
  * the `runtimeClasspath` read-only property in `org.jenkinsci.gradle.plugins.jpi.JpiExtension` has been removed

## 0.9.0 (2015-02-16)

  * added task to inject tests for checking the syntax of Jelly and other things
    ([JENKINS-12193](https://issues.jenkins-ci.org/browse/JENKINS-12193))
  * updated Gradle to version 2.3
  * publish the plugin JAR
    ([JENKINS-25007](https://issues.jenkins-ci.org/browse/JENKINS-25007))

## 0.8.1 (2015-01-28)

  * create `target` directory (for coreVersions >= 1.545 and < 1.592), clean `target` directory (for coreVersions
    < 1.598) and set `buildDirectory` system property for Jenkins test harness
    ([JENKINS-26331](https://issues.jenkins-ci.org/browse/JENKINS-26331))

## 0.8.0 (2015-01-06)

  * support Java 8
    ([JENKINS-25643](https://issues.jenkins-ci.org/browse/JENKINS-25643))
  * updated Gradle to version 2.2.1
  * migrated to the maven-publish plugin
    * use the `publish` and `publishToMavenLocal` tasks for publishing, the `install`, `deploy` and `uploadArchives`
      tasks are no longer available
    * all dependencies in the generated POM have the runtime scope

## 0.7.2 (2014-12-19)

  * re-added the SCM `connection` element in the generated POM to fix a regression with Jenkins Update Center showing
    the wrong SCM URL

## 0.7.1 (2014-11-04)

  * fixed regression that caused libraries not to be included in the JPI file
    ([JENKINS-25401](https://issues.jenkins-ci.org/browse/JENKINS-25401))
  * dependencies from the `groovy` configuration are no longer excluded from the JPI archive, use the `providedCompile`
    configuration or the transitive dependencies from Jenkins core instead

## 0.7.0 (2014-10-23)

  * updated Gradle to version 1.12
  * added support for `Plugin-Developers` manifest attribute
  * added support for `Support-Dynamic-Loading` manifest attribute
  * set `stapler.jelly.noCache` system property to `true` when running `server` task
  * removed `v` attribute from manifest
  * copy plugin dependencies from `jenkinsPlugins` configuration into working directory when running `server` task
    ([JENKINS-25219](https://issues.jenkins-ci.org/browse/JENKINS-25219))
  * ignore non-existing source directories in `localizer` task
  * ignore non-existing library paths in `server` task
  * the value set for the `snapshotRepoUrl` option is no longer ignored
  * added `findbugs:annotations:1.0.0` to `jenkinsCore` configuration to avoid compiler warnings
    ([JENKINS-14400](https://issues.jenkins-ci.org/browse/JENKINS-14400))
  * the Maven `connection` and `developerConnection` SCM information is no longer generated into the POM
  * removed `gitHubSCMConnection` and `gitHubSCMDevConnection` read-only options 
  * replaced usages of deprecated `groovy` configuration by `compile` configuration
  * added a missing setter for `shortName`
  * added `org.jenkins-ci.jpi` as alternative qualified plugin id for Gradle plugin portal inclusion
  * removed `WEB_APP_GROUP` constant from `org.jenkinsci.gradle.plugins.jpi.JpiPlugin`
  * changed visibility of `org.jenkinsci.gradle.plugins.jpi.JpiPlugin.configureConfigurations` to private
  * `jpiDeployUser` and `jpiDeployPassword` properties from `org.jenkinsci.gradle.plugins.jpi.JpiExtension` were not
    used and have been removed
  * `org.jenkinsci.gradle.plugins.jpi.JpiPluginConvention` was not used and has been removed

## 0.6.0 (2014-10-01)

  * do not exclude org.jenkins-ci.modules:instance-identity from jenkinsTest configuration
    ([JENKINS-23603](https://issues.jenkins-ci.org/browse/JENKINS-23603))
  * set up the build to use http://repo.jenkins-ci.org/public/ similar to the Maven POMs
    ([JENKINS-19942](https://issues.jenkins-ci.org/browse/JENKINS-19942))
  * register input files for the localizer task to avoid that the task is skipped when the inputs change
    ([JENKINS-24298](https://issues.jenkins-ci.org/browse/JENKINS-24298))
  * added pluginFirstClassLoader attribute
    ([JENKINS-24808](https://issues.jenkins-ci.org/browse/JENKINS-24808))
  * removed deprecation warnings by using newer API which has been introduced in Gradle 1.6 

## 0.5.2 (unreleased)

  * use ui-samples-plugin version 2.0 in jenkinsTests configuration when using Jenkins core 1.533 or later
    ([JENKINS-21431](https://issues.jenkins-ci.org/browse/JENKINS-21431))
