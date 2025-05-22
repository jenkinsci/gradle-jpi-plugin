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
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jenkinsci.gradle.plugins.jpi2.ArtifactType.ARTIFACT_TYPE_ATTRIBUTE;

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

        var jenkinsCore = configurations.create("jenkinsCore");

        var runtimeClasspath = configurations.getByName("runtimeClasspath");
        runtimeClasspath.shouldResolveConsistentlyWith(jenkinsCore);
        runtimeClasspath.getAttributes().attribute(ARTIFACT_TYPE_ATTRIBUTE, project.getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR));

        var testRuntimeClasspath = configurations.getByName("testRuntimeClasspath");
        testRuntimeClasspath.shouldResolveConsistentlyWith(jenkinsCore);
        testRuntimeClasspath.getAttributes().attribute(ARTIFACT_TYPE_ATTRIBUTE, project.getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR));

        var defaultRuntime = configurations.create("defaultRuntime");
        runtimeClasspath.getExtendsFrom().forEach(defaultRuntime::extendsFrom);
        defaultRuntime.shouldResolveConsistentlyWith(jenkinsCore);
        defaultRuntime.getAttributes().attribute(ARTIFACT_TYPE_ATTRIBUTE, project.getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));

        var testCompileClasspath = configurations.getByName("testCompileClasspath");
        testCompileClasspath.shouldResolveConsistentlyWith(jenkinsCore);

        JavaPluginExtension ext = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = ext.getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        main.getResources().getSrcDirs().add(project.file("src/main/webapp"));

        var jpiTask = project.getTasks().register(JPI_TASK, War.class, new ConfigureJpiAction(project, defaultRuntime, jenkinsVersion));
        project.getTasks().named("jar", Jar.class).configure(new Action<>() {
            @Override
            public void execute(@NotNull Jar jarTask) {
                jarTask.manifest(new ManifestAction(project, defaultRuntime, jenkinsVersion));
            }
        });
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

        final var projectRoot = project.getLayout().getProjectDirectory().getAsFile().getAbsolutePath();
        final var prepareServer = createPrepareServerTask(project, projectRoot, defaultRuntime, runtimeClasspath, jpiTask);

        var serverTask = project.getTasks().register("server", JavaExec.class, new ServerAction(serverTaskClasspath, projectRoot, prepareServer));
        project.getPlugins().withType(JavaBasePlugin.class, new SezpozJavaAction(project));
        project.getPlugins().withType(GroovyBasePlugin.class, new SezpozGroovyAction(project));

        /*
         * We want sezpoz to be the last annotation processor.
         * If other annotation processors contribute methods/controllers that sezpoz expects, this will ensure it happens.
         */
        var lastAnnotationProcessor = project.getConfigurations().create("lastAnnotationProcessor");
        lastAnnotationProcessor.setVisible(false);
        project.getDependencies().add("lastAnnotationProcessor", "net.java.sezpoz:sezpoz:1.13");
        project.getDependencies().add("lastAnnotationProcessor", "org.jenkins-ci.main:jenkins-core:" + jenkinsVersion);
        project.getConfigurations().getByName("annotationProcessor").extendsFrom(lastAnnotationProcessor);
        lastAnnotationProcessor.shouldResolveConsistentlyWith(jenkinsCore);

        dependencies.add("compileOnly", "org.jenkins-ci.main:jenkins-core:" + jenkinsVersion);
        dependencies.add("compileOnly", "jakarta.servlet:jakarta.servlet-api:5.0.0");
        dependencies.add("serverTaskClasspath", "org.jenkins-ci.main:jenkins-war:" + jenkinsVersion);

        dependencies.add("testImplementation", "org.jenkins-ci.main:jenkins-core:" + jenkinsVersion);
        dependencies.add("testImplementation", "org.jenkins-ci.main:jenkins-war:" + jenkinsVersion);
        dependencies.add("testImplementation", "org.jenkins-ci.main:jenkins-test-harness:" + testHarnessVersion);

        dependencies.add("jenkinsCore", "org.jenkins-ci.main:jenkins-core:" + jenkinsVersion);

        dependencies.getComponents().all(HpiMetadataRule.class);
        configurePublishing(project, jpiTask, defaultRuntime);

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
    private static TaskProvider<?> createPrepareServerTask(@NotNull Project project, String projectRoot, Configuration defaultRuntime, Configuration runtimeClasspath, TaskProvider<?> jpiTaskProvider) {
        return project.getTasks().register("prepareServer", Sync.class, new ConfigurePrepareServerAction(jpiTaskProvider, projectRoot, defaultRuntime, runtimeClasspath, project));
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
