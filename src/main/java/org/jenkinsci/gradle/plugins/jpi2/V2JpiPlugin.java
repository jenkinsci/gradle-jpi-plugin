package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.publish.PublishingExtension;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.plugins.MavenPublishPlugin;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
        "unused" // This is tested in an acceptance test
})
public class V2JpiPlugin implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(V2JpiPlugin.class);

    public static final String EXPLODED_JPI_TASK = "explodedJpi";
    public static final String JPI_TASK = "jpi";

    public static final String JENKINS_VERSION_PROPERTY = "jenkins.version";
    public static final String DEFAULT_JENKINS_VERSION = "2.492.3";

    public static final String TEST_HARNESS_VERSION_PROPERTY = "jenkins.testharness.version";
    public static final String DEFAULT_TEST_HARNESS_VERSION = "2414.v185474555e66";

    @Override
    public void apply(@NotNull Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(MavenPublishPlugin.class);

        var configurations = project.getConfigurations();
        var dependencies = project.getDependencies();

        var serverTaskClasspath = createServerTaskClasspathConfiguration(project);
        String jenkinsVersion = getVersionFromProperties(project, JENKINS_VERSION_PROPERTY, DEFAULT_JENKINS_VERSION);
        String testHarnessVersion = getVersionFromProperties(project, TEST_HARNESS_VERSION_PROPERTY, DEFAULT_TEST_HARNESS_VERSION);

        var runtimeClasspath = configurations.getByName("runtimeClasspath");

        var jpiTask = project.getTasks().register(JPI_TASK, War.class, new ConfigureJpiAction(project, runtimeClasspath, jenkinsVersion));
        project.getTasks().register(EXPLODED_JPI_TASK, Sync.class, new Action<>() {
            @Override
            public void execute(@NotNull Sync sync) {
                sync.into(project.getLayout().getBuildDirectory().dir("jpi"));
                sync.with((War) project.getTasks().getByName(JPI_TASK));
            }
        });
        project.getTasks().named("assemble", new Action<>() {
            @Override
            public void execute(@NotNull Task task) {
                task.dependsOn(jpiTask);
            }
        });

        project.getTasks().named("assemble", new Action<>() {
            @Override
            public void execute(@NotNull Task task) {
                task.dependsOn(jpiTask);
            }
        });

        final var projectRoot = project.getLayout().getProjectDirectory().getAsFile().getAbsolutePath();
        final var prepareServer = createPrepareServerTask(project, projectRoot, runtimeClasspath, jpiTask);

        var serverTask = project.getTasks().register("server", JavaExec.class, new ServerAction(serverTaskClasspath, projectRoot, prepareServer));
        project.getPlugins().withType(JavaBasePlugin.class, new SezpozJavaAction(project));
        project.getPlugins().withType(GroovyBasePlugin.class, new SezpozGroovyAction(project));

        /*
         * We want sezpoz to be the last annotation processor.
         * If other annotation processors contribute methods/controllers that sezpoz expects, this will ensure it happens.
         */
        var sezpozAnnotationProcessor = project.getConfigurations().create("sezpozAnnotationProcessor");
        project.getDependencies().add("sezpozAnnotationProcessor", "net.java.sezpoz:sezpoz:1.13");
        project.getConfigurations().getByName("annotationProcessor").extendsFrom(sezpozAnnotationProcessor);

        dependencies.add("compileOnly", "org.jenkins-ci.main:jenkins-core:" + jenkinsVersion);
        dependencies.add("compileOnly", "jakarta.servlet:jakarta.servlet-api:5.0.0");
        dependencies.add("compileOnly", "jakarta.inject:jakarta.inject-api:2.0.1");
        dependencies.add("serverTaskClasspath", "org.jenkins-ci.main:jenkins-war:" + jenkinsVersion);

        dependencies.add("testImplementation", "org.jenkins-ci.main:jenkins-core:" + jenkinsVersion);
        dependencies.add("testImplementation", "org.jenkins-ci.main:jenkins-war:" + jenkinsVersion);
        dependencies.add("testImplementation", "org.jenkins-ci.main:jenkins-test-harness:" + testHarnessVersion);

        dependencies.getComponents().all(HpiMetadataRule.class);
        configurePublishing(project, jpiTask, runtimeClasspath);

        project.getTasks().register("testServer", new ConfigureTestServerAction(project));
    }

    private static void configurePublishing(@NotNull Project project, TaskProvider<?> jpiTask, Configuration runtimeClasspath) {
        var publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
        var existingPublication = !publishingExtension.getPublications().isEmpty() ? publishingExtension.getPublications().iterator().next() : null;
        var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        javaPlugin.withJavadocJar();
        javaPlugin.withSourcesJar();
        if (existingPublication instanceof MavenPublication publication) {
            configurePublication(publication, jpiTask, runtimeClasspath, project);
        } else {
            publishingExtension.getPublications().create("mavenJpi", MavenPublication.class, new Action<>() {
                @Override
                public void execute(@NotNull MavenPublication publication) {
                    publication.from(project.getComponents().getByName("java"));
                    configurePublication(publication, jpiTask, runtimeClasspath, project);
                }
            });
        }
    }

    private static void configurePublication(@NotNull MavenPublication publication, TaskProvider<?> jpiTask, Configuration runtimeClasspath, Project project) {
        publication.artifact(jpiTask);
        publication.getPom().setPackaging("jpi");
        publication.getPom().withXml(new PomBuilder(runtimeClasspath, project));
    }

    @NotNull
    private static TaskProvider<?> createPrepareServerTask(@NotNull Project project, String projectRoot, Configuration serverJenkinsPlugin, TaskProvider<?> jpiTaskProvider) {
        return project.getTasks().register("prepareServer", Sync.class, new ConfigurePrepareServerAction(jpiTaskProvider, projectRoot, serverJenkinsPlugin, project));
    }

    @NotNull
    private static Configuration createServerTaskClasspathConfiguration(@NotNull Project project) {
        return project.getConfigurations().create("serverTaskClasspath", new Action<>() {
            @Override
            public void execute(@NotNull Configuration c) {
                c.setCanBeConsumed(false);
                c.setTransitive(false);
            }
        });
    }

    private static String getVersionFromProperties(@NotNull Project project, String propertyName, String defaultVersion) {
        Provider<String> myProperty = project.getProviders().gradleProperty(propertyName);
        return myProperty.getOrElse(defaultVersion);
    }

}
