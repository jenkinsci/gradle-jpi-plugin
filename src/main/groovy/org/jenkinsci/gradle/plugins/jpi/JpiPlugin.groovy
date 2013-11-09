/*
 * Copyright 2009-2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jenkinsci.gradle.plugins.jpi;


import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.WarPlugin
import org.gradle.api.plugins.WarPluginConvention
import org.gradle.api.plugins.GroovyPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.plugins.MavenPluginConvention
import org.gradle.api.artifacts.maven.Conf2ScopeMappingContainer;
import org.gradle.api.artifacts.maven.MavenResolver
import org.gradle.api.artifacts.maven.MavenDeployer
import org.gradle.api.artifacts.maven.MavenPom;
import org.gradle.api.tasks.bundling.Jar

/**
 * Loads HPI related tasks into the current project.
 *
 * @author Hans Dockter
 * @author Kohsuke Kawaguchi
 * @author Andrew Bayer
 */
public class JpiPlugin implements Plugin<Project> {
    /**
     * Represents the dependency to the Jenkins core.
     */
    public static final String CORE_DEPENDENCY_CONFIGURATION_NAME = "jenkinsCore";

    /**
     * Represents the dependency to the Jenkins war. Test scope.
     */
    public static final String WAR_DEPENDENCY_CONFIGURATION_NAME = "jenkinsWar";

    /**
     * Represents the dependencies on other Jenkins plugins.
     */
    public static final String PLUGINS_DEPENDENCY_CONFIGURATION_NAME = "jenkinsPlugins"

    /**
     * Represents the dependencies on other Jenkins plugins.
     *
     * Using a separate configuration until we see GRADLE-1749.
     */
    public static final String OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME = "optionalJenkinsPlugins"

    /**
     * Represents the Jenkins plugin test dependencies.
     */
    public static final String JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME = "jenkinsTest"

    public static final String WEB_APP_GROUP = "web application";

    public void apply(final Project gradleProject) {
        gradleProject.plugins.apply(JavaPlugin);
        gradleProject.plugins.apply(WarPlugin);
        gradleProject.plugins.apply(MavenPlugin);
        gradleProject.plugins.apply(GroovyPlugin);
        def pluginConvention = new JpiPluginConvention();
        gradleProject.convention.plugins["jpi"] = pluginConvention

        def warConvention = gradleProject.convention.getPlugin(WarPluginConvention);

        // never run war as it's useless
        gradleProject.tasks.getByName("war").onlyIf { false }

        def ext = new JpiExtension(gradleProject)

        gradleProject.extensions.jenkinsPlugin = ext;

        gradleProject.tasks.withType(Jpi) { Jpi task ->
            task.dependsOn {
                ext.mainSourceTree().runtimeClasspath
            }
            task.setClasspath(ext.runtimeClasspath)
            task.archiveName = "${ext.shortName}.${ext.fileExtension}";
        }
        gradleProject.tasks.withType(ServerTask) { ServerTask task ->
            task.dependsOn {
                ext.mainSourceTree().runtimeClasspath
            }
        }
        gradleProject.tasks.withType(StaplerGroovyStubsTask) { StaplerGroovyStubsTask task ->
            task.destinationDir = ext.getStaplerStubDir()
        }
        gradleProject.tasks.withType(LocalizerTask) { LocalizerTask task ->
            task.destinationDir = ext.getLocalizerDestDir()
        }

        def jpi = gradleProject.tasks.add(Jpi.TASK_NAME, Jpi);
        jpi.description = "Generates the JPI package";
        jpi.group = BasePlugin.BUILD_GROUP;
        gradleProject.extensions.getByType(DefaultArtifactPublicationSet).addCandidate(new ArchivePublishArtifact(jpi));

        def server = gradleProject.tasks.add(ServerTask.TASK_NAME, ServerTask);
        server.description = "Run Jenkins in place with the plugin being developed";
        server.group = BasePlugin.BUILD_GROUP; // TODO

        def stubs = gradleProject.tasks.add(StaplerGroovyStubsTask.TASK_NAME, StaplerGroovyStubsTask)
        stubs.description = "Generates the Java stubs from Groovy source to enable Stapler annotation processing."
        stubs.group = BasePlugin.BUILD_GROUP

        gradleProject.sourceSets.main.java.srcDirs += ext.getStaplerStubDir()

        gradleProject.tasks.compileJava.dependsOn(StaplerGroovyStubsTask.TASK_NAME)

        def localizer = gradleProject.tasks.add(LocalizerTask.TASK_NAME, LocalizerTask)
        localizer.description = "Generates the Java source for the localizer."
        localizer.group = BasePlugin.BUILD_GROUP

        gradleProject.sourceSets.main.java.srcDirs += ext.getLocalizerDestDir()

        gradleProject.tasks.compileJava.dependsOn(LocalizerTask.TASK_NAME)

        def jar = gradleProject.tasks.add(Jar.TASK_NAME, Jar)
        def sourcesJar = gradleProject.task('sourcesJar', type: Jar, dependsOn:'classes') {
            classifier = 'sources'
            from gradleProject.sourceSets.main.allSource
        }
        gradleProject.artifacts {
            archives jar, sourcesJar
        }

        configureConfigurations(gradleProject.configurations);

        def mvnConvention = gradleProject.convention.getPlugin(MavenPluginConvention)
        mvnConvention.conf2ScopeMappings.addMapping(MavenPlugin.PROVIDED_COMPILE_PRIORITY,
                                                         gradleProject.configurations[CORE_DEPENDENCY_CONFIGURATION_NAME],
                                                         Conf2ScopeMappingContainer.PROVIDED)

        mvnConvention.conf2ScopeMappings.addMapping(MavenPlugin.PROVIDED_COMPILE_PRIORITY,
                                                         gradleProject.configurations[PLUGINS_DEPENDENCY_CONFIGURATION_NAME],
                                                         Conf2ScopeMappingContainer.PROVIDED)

        mvnConvention.conf2ScopeMappings.addMapping(MavenPlugin.PROVIDED_COMPILE_PRIORITY,
                                                         gradleProject.configurations[OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME],
                                                         Conf2ScopeMappingContainer.PROVIDED)

        def installer = gradleProject.tasks.getByName("install")


        installer.repositories.mavenInstaller.pom.whenConfigured { p -> 
            p.project { 
                parent {
                    groupId 'org.jenkins-ci.plugins'
                    artifactId 'plugin'
                    version ext.getCoreVersion()
                }
                url ext.url
                description gradleProject.description
                name ext.getDisplayName()
                artifactId ext.shortName
                if (ext.gitHubUrl != null && ext.gitHubUrl =~ /^https:\/\/github\.com/) {
                    scm {
                        connection ext.getGitHubSCMConnection()
                        developerConnection ext.getGitHubSCMDevConnection()
                        url ext.gitHubUrl
                    }
                }
                repositories { 
                    gradleProject.repositories.each { repo ->
                        if (repo.name == 'MavenRepo' || repo.name == 'MavenLocal') {
                            // do not include the Maven Central repository or the local cache.
                            return
                        }
                        repository {
                            id = repo.name
                            url = repo.url
                        }
                    }
                }
                developers {
                    ext.developers.each { dev ->
                        developer { 
                            id dev.id
                            if (dev.name != null)
                                name dev.name
                            if (dev.email != null)
                                email dev.email
                            if (dev.url != null)
                                url dev.url
                            if (dev.organization != null)
                                organization dev.organization
                            if (dev.organizationUrl != null)
                                organizationUrl dev.organizationUrl
                            if (dev.timezone != null)
                                timezone dev.timezone
                        }
                    }
                }
            }
        }

        // default configuration of uploadArchives Maven task
        def uploadArchives = gradleProject.tasks.getByName("uploadArchives")
        uploadArchives.doFirst {
            repositories {
                mavenDeployer {
                    // configure this only when the user didn't give any explicit configuration
                    // whatever in build.gradle should win what we have here
                    if (repository==null && snapshotRepository==null) {
                        gradleProject.logger.warn("Deploying to the Jenkins community repository")
                        def props = loadDotJenkinsOrg()

                        repository(url: ext.repoUrl) {
                            authentication(userName:props["userName"], password:props["password"])
                        }
                        snapshotRepository(url:ext.snapshotRepoUrl) {
                            authentication(userName:props["userName"], password:props["password"])
                        }
                    }
                    pom = installer.repositories.mavenInstaller.pom
                }
            }
        }
                

        // creating alias for making migration from Maven easy.
        gradleProject.tasks.create("deploy").dependsOn(uploadArchives)

        // generate test hpl manifest for the current plugin, to be used during unit test
        def generateTestHpl = gradleProject.tasks.create("generate-test-hpl") << {
            def hpl = new File(ext.testSourceTree().output.classesDir, "the.hpl")
            hpl.parentFile.mkdirs()
            new JpiHplManifest(gradleProject).writeTo(hpl)
        }
        gradleProject.tasks.getByName("test").dependsOn(generateTestHpl)
    }
    
    private Properties loadDotJenkinsOrg() {
        Properties props = new Properties()
        def dot = new File(new File(System.getProperty("user.home")), ".jenkins-ci.org")
        if (!dot.exists())
            throw new Exception("Trying to deploy to Jenkins community repository but there's no credential file ${dot}. See https://wiki.jenkins-ci.org/display/JENKINS/Dot+Jenkins+Ci+Dot+Org")
        dot.withInputStream { i -> props.load(i) }
        return props
    }

    public void configureConfigurations(ConfigurationContainer cc) {
        Configuration jenkinsCoreConfiguration = cc.add(CORE_DEPENDENCY_CONFIGURATION_NAME).setVisible(false).
                setDescription("Jenkins core that your plugin is built against");
        Configuration jenkinsPluginsConfiguration = cc.add(PLUGINS_DEPENDENCY_CONFIGURATION_NAME).setVisible(false).
                setDescription("Jenkins plugins which your plugin is built against");
        Configuration optionalJenkinsPluginsConfiguration = cc.add(OPTIONAL_PLUGINS_DEPENDENCY_CONFIGURATION_NAME).setVisible(false).
                setDescription("Optional Jenkins plugins dependencies which your plugin is built against");
        Configuration jenkinsTestConfiguration = cc.add(JENKINS_TEST_DEPENDENCY_CONFIGURATION_NAME).setVisible(false)
                .setDescription("Jenkins plugin test dependencies.")
        .exclude(group: "org.jenkins-ci.modules", module: 'instance-identity') 
        .exclude(group: "org.jenkins-ci.modules", module: 'ssh-cli-auth') 
        .exclude(group: "org.jenkins-ci.modules", module: 'sshd');
        cc.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME).extendsFrom(jenkinsCoreConfiguration);
        cc.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME).extendsFrom(jenkinsPluginsConfiguration);
        cc.getByName(WarPlugin.PROVIDED_COMPILE_CONFIGURATION_NAME).extendsFrom(optionalJenkinsPluginsConfiguration);
        cc.getByName(JavaPlugin.TEST_COMPILE_CONFIGURATION_NAME).extendsFrom(jenkinsTestConfiguration);

        cc.add(WAR_DEPENDENCY_CONFIGURATION_NAME).setVisible(false).
                setDescription("Jenkins war that corresponds to the Jenkins core");
    }

}
