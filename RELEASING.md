Releasing the Gradle JPI Plugin
===============================

These are the instructions to release the Gradle JPI Plugin.


Prerequisites
-------------

The following Gradle properties must be set:

- `gradle.publish.key` (from Gradle Plugin Portal)
- `gradle.publish.secret` (from Gradle Plugin Portal)

They can be set using the `-P` syntax on the command line, or as `ORG_GRADLE_PROJECT_{name}` environment variables ([docs]).

[docs]: https://docs.gradle.org/current/userguide/build_environment.html#sec:project_specific_properties


Steps
-----

This repo uses the [nebula.release plugin][nebula.release] to manage versions automatically.
For a candidate, use the `candidate` task.
For a final release, use the `final` task.

* Ensure you have the latest code: `git checkout master && git pull`
* Update `CHANGELOG.md`, set the release date
* Update the version in `README.md`
* Ensure everything is checked in: `git commit -S -am "releasing 0.6.0"`
* Build the code: `gradlew clean check install`
* Test the plugin with Jenkins plugin projects using it (e.g. https://github.com/jenkinsci/job-dsl-plugin)
* Deploy: `./gradlew candidate`
* Update `CHANGELOG.md`, add the next version
* Close all resolved issues in JIRA: https://issues.jenkins-ci.org/secure/Dashboard.jspa?selectPageId=15444
* Send an email to jenkinsci-dev@googlegroups.com

[nebula.release]: https://github.com/nebula-plugins/nebula-release-plugin

