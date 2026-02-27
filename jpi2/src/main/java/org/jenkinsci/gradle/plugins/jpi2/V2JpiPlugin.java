package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.GroovyBasePlugin;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaBasePlugin;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.provider.Provider;
import org.gradle.api.services.BuildServiceRegistry;
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
import org.jenkinsci.gradle.plugins.jpi2.accmod.CheckAccessModifierTask;
import org.jenkinsci.gradle.plugins.jpi2.accmod.PrefixedPropertiesProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.jenkinsci.gradle.plugins.jpi2.ArtifactType.ARTIFACT_TYPE_ATTRIBUTE;

/**
 * Gradle plugin for building Jenkins plugins (JPI files).
 */
@SuppressWarnings({
        "Convert2Lambda", // Gradle doesn't like lambdas
        "unused" // This is tested in an acceptance test
})
public class V2JpiPlugin implements Plugin<Project> {

    private static final Logger log = LoggerFactory.getLogger(V2JpiPlugin.class);

    /** Task name for creating an exploded JPI directory. */
    public static final String EXPLODED_JPI_TASK = "explodedJpi";
    /** Task name for creating the JPI archive. */
    public static final String JPI_TASK = "jpi";

    @Override
    public void apply(@NotNull Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(MavenPublishPlugin.class);

        var extension = project.getExtensions().create("jenkinsPlugin", JenkinsPluginExtension.class, project);

        var configurations = project.getConfigurations();
        var dependencies = project.getDependencies();

        var serverTaskClasspath = createServerTaskClasspathConfiguration(project);
        var jenkinsVersion = extension.getJenkinsVersion();
        var testHarnessVersion = extension.getTestHarnessVersion();
        var jenkinsCoreCoordinate = jenkinsVersion.map(version -> "org.jenkins-ci.main:jenkins-core:" + version);
        var jenkinsWarCoordinate = jenkinsVersion.map(version -> "org.jenkins-ci.main:jenkins-war:" + version);
        var jenkinsTestHarnessCoordinate = testHarnessVersion.map(version -> "org.jenkins-ci.main:jenkins-test-harness:" + version);

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

        var licenseTask = project.getTasks().register(GenerateLicenseInfoTask.NAME, GenerateLicenseInfoTask.class, new Action<>() {
            @Override
            public void execute(@NotNull GenerateLicenseInfoTask task) {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Generates license information.");
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("licenses"));
                task.setLibraryConfiguration(defaultRuntime);
            }
        });

        var testCompileClasspath = configurations.getByName("testCompileClasspath");
        testCompileClasspath.shouldResolveConsistentlyWith(jenkinsCore);

        JavaPluginExtension ext = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = ext.getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.TEST_SOURCE_SET_NAME);
        main.getResources().getSrcDirs().add(project.file("src/main/webapp"));

        var jpiTask = project.getTasks().register(JPI_TASK, War.class, new ConfigureJpiAction(project, defaultRuntime, jenkinsCore, extension));
        jpiTask.configure(new Action<>() {
            @Override
            public void execute(@NotNull War war) {
                war.dependsOn(licenseTask);
                war.getWebInf().from(licenseTask.flatMap(GenerateLicenseInfoTask::getOutputDirectory));
            }
        });
        project.getTasks().named("jar", Jar.class).configure(new Action<>() {
            @Override
            public void execute(@NotNull Jar jarTask) {
                jarTask.manifest(new ManifestAction(project, defaultRuntime, extension));
            }
        });
        Provider<Directory> jpiDirectory = project.getLayout().getBuildDirectory().dir("jpi");
        project.getTasks().register(EXPLODED_JPI_TASK, Sync.class, new Action<>() {
            @Override
            public void execute(@NotNull Sync sync) {
                sync.into(jpiDirectory);
                sync.with(jpiTask.get());
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
        configureAccessModifier(project);

        /*
         * We want sezpoz to be the last annotation processor.
         * If other annotation processors contribute methods/controllers that sezpoz expects, this will ensure it happens.
         */
        var lastAnnotationProcessor = project.getConfigurations().create("lastAnnotationProcessor");
        lastAnnotationProcessor.setVisible(false);
        project.getDependencies().add("lastAnnotationProcessor", "net.java.sezpoz:sezpoz:1.13");
        project.getDependencies().add("lastAnnotationProcessor", jenkinsCoreCoordinate);
        project.getConfigurations().getByName("annotationProcessor").extendsFrom(lastAnnotationProcessor);
        lastAnnotationProcessor.shouldResolveConsistentlyWith(jenkinsCore);

        dependencies.add("compileOnly", jenkinsCoreCoordinate);
        dependencies.add("compileOnly", "jakarta.servlet:jakarta.servlet-api:5.0.0");
        dependencies.add(serverTaskClasspath.getName(), jenkinsWarCoordinate);

        dependencies.add("testImplementation", jenkinsCoreCoordinate);
        dependencies.add("testImplementation", jenkinsWarCoordinate);
        dependencies.add("testImplementation", jenkinsTestHarnessCoordinate);
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter");
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher");

        dependencies.add(jenkinsCore.getName(), jenkinsCoreCoordinate);

        dependencies.getComponents().all(HpiMetadataRule.class);
        configurePublishing(project, jpiTask, defaultRuntime);

        BuildServiceRegistry buildServices = project.getGradle().getSharedServices();
        var portAllocationService = buildServices.registerIfAbsent("portAllocation", PortAllocationService.class, spec -> {
        });

        project.getTasks().register("testServer", new ConfigureTestServerAction(project, portAllocationService.get()));
    }

    private static void configureAccessModifier(@NotNull Project project) {
        var library = project.getDependencies().create("org.kohsuke:access-modifier-checker:1.33");
        var mavenLog = project.getDependencies().create("org.apache.maven:maven-plugin-api:2.0.1");
        var jenkinsAccessModifier = project.getConfigurations().create("jenkinsAccessModifier", c -> {
            c.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.withDependencies(dependencies -> {
                dependencies.add(library);
                dependencies.add(mavenLog);
            });
        });

        var propertyProvider = project.provider(new PrefixedPropertiesProvider(project, CheckAccessModifierTask.PREFIX));
        var checkAccessModifier = project.getTasks().register(CheckAccessModifierTask.NAME, CheckAccessModifierTask.class, task -> {
            task.setGroup("Verification");
            task.setDescription("Checks if Jenkins restricted apis are used (https://tiny.cc/jenkins-restricted).");
            var dirs = project.getExtensions()
                    .getByType(JavaPluginExtension.class)
                    .getSourceSets()
                    .getByName(SourceSet.MAIN_SOURCE_SET_NAME)
                    .getOutput()
                    .getClassesDirs();
            task.getAccessModifierClasspath().from(jenkinsAccessModifier);
            task.getAccessModifierProperties().set(propertyProvider);
            task.getCompileClasspath().from(project.getConfigurations().getByName("compileClasspath"));
            task.getCompilationDirs().from(dirs);
            task.getIgnoreFailures().convention(true);
            task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("access-modifier"));
            task.getOutputs().upToDateWhen(element -> !task.getIgnoreFailures().get());
        });

        project.getTasks().named("check", task -> task.dependsOn(checkAccessModifier));
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
