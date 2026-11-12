package org.jenkinsci.gradle.plugins.jpi2;

import static org.jenkinsci.gradle.plugins.jpi2.ArtifactType.ARTIFACT_TYPE_ATTRIBUTE;

import java.io.File;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.result.ResolvedDependencyResult;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.Directory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.ExtensionAware;
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
import org.jenkinsci.gradle.plugins.jpi2.localization.LocalizationPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gradle plugin for building Jenkins plugins (JPI files).
 */
@SuppressWarnings("unused") // This is tested in an acceptance test
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
        RepositoryShortcuts.registerRepositoryShortcuts(project.getRepositories());
        var publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
        RepositoryShortcuts.registerRepositoryShortcuts(publishingExtension.getRepositories(), project);

        var extension = project.getExtensions().create("jenkinsPlugin", JenkinsPluginExtension.class, project);
        ((ExtensionAware) extension)
                .getExtensions()
                .create(
                        "gitVersion",
                        GitVersionExtension.class,
                        project.getObjects(),
                        project.getLayout(),
                        project.getProviders());

        var gitVersion = extension.getGitVersion();
        project.getTasks().register(GenerateGitVersionTask.NAME, GenerateGitVersionTask.class, task -> {
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setDescription("Writes the Git-derived plugin version and commit hash.");
            task.getGitRoot().set(gitVersion.getGitRoot());
            task.getVersionFormat().set(gitVersion.getVersionFormat());
            task.getVersionPrefix().set(gitVersion.getVersionPrefix());
            task.getAbbrevLength().set(gitVersion.getAbbrevLength());
            task.getAllowDirty().set(gitVersion.getAllowDirty());
            task.getOutputFile().set(gitVersion.getOutputFile());
        });

        var configurations = project.getConfigurations();
        var dependencies = project.getDependencies();

        var serverTaskClasspath = createServerTaskClasspathConfiguration(project);
        var jenkinsVersion = extension.getJenkinsVersion();
        var testHarnessVersion = extension.getTestHarnessVersion();
        var jenkinsCoreCoordinate = jenkinsVersion.map(version -> "org.jenkins-ci.main:jenkins-core:" + version);
        var jenkinsWarCoordinate = jenkinsVersion.map(version -> "org.jenkins-ci.main:jenkins-war:" + version);
        var jenkinsTestHarnessCoordinate =
                testHarnessVersion.map(version -> "org.jenkins-ci.main:jenkins-test-harness:" + version);

        var jenkinsCore = configurations.create("jenkinsCore");
        var jenkinsCoreAlignment = ConsistentResolution.createAlignment(project, jenkinsCore);

        var runtimeClasspath = configurations.getByName("runtimeClasspath");
        runtimeClasspath.extendsFrom(jenkinsCoreAlignment);
        runtimeClasspath
                .getAttributes()
                .attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        project.getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR));

        var testRuntimeClasspath = configurations.getByName("testRuntimeClasspath");
        testRuntimeClasspath.extendsFrom(jenkinsCoreAlignment);
        testRuntimeClasspath
                .getAttributes()
                .attribute(
                        ARTIFACT_TYPE_ATTRIBUTE,
                        project.getObjects().named(ArtifactType.class, ArtifactType.PLUGIN_JAR));

        var testDefaultRuntime = configurations.create("testDefaultRuntime");
        testDefaultRuntime.setCanBeConsumed(false);
        testRuntimeClasspath.getExtendsFrom().forEach(testDefaultRuntime::extendsFrom);
        testDefaultRuntime.extendsFrom(jenkinsCoreAlignment);
        testDefaultRuntime
                .getAttributes()
                .attribute(
                        ARTIFACT_TYPE_ATTRIBUTE, project.getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));

        var defaultRuntime = configurations.create("defaultRuntime");
        runtimeClasspath.getExtendsFrom().forEach(defaultRuntime::extendsFrom);
        defaultRuntime.extendsFrom(jenkinsCoreAlignment);
        defaultRuntime
                .getAttributes()
                .attribute(
                        ARTIFACT_TYPE_ATTRIBUTE, project.getObjects().named(ArtifactType.class, ArtifactType.DEFAULT));

        var runtimeClasspathArtifacts = new RuntimeClasspathArtifacts(project, defaultRuntime, jenkinsCore);
        var pomFiles = resolvePomFiles(project, runtimeClasspathArtifacts);
        var licenseTask = project.getTasks()
                .register(GenerateLicenseInfoTask.NAME, GenerateLicenseInfoTask.class, task -> {
                    task.setGroup(BasePlugin.BUILD_GROUP);
                    task.setDescription("Generates license information.");
                    task.getOutputDirectory()
                            .set(project.getLayout().getBuildDirectory().dir("licenses"));
                    task.getPomFiles().from(pomFiles);
                    task.getProjectVersion().set(extension.getEffectiveVersion());
                    task.getProjectName().set(project.getName());
                    task.getProjectGroup()
                            .set(project.provider(() -> project.getGroup().toString()));
                    task.getProjectDescription().set(project.provider(project::getDescription));
                    task.getProjectUrl().set(project.getProviders().gradleProperty("url"));
                });

        var testCompileClasspath = configurations.getByName("testCompileClasspath");
        testCompileClasspath.extendsFrom(jenkinsCoreAlignment);

        JavaPluginExtension ext = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = ext.getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        main.getResources().getSrcDirs().add(project.file("src/main/webapp"));
        project.getPlugins().apply(LocalizationPlugin.class);
        var checkOverlappingSources = project.getTasks()
                .register(CheckOverlappingSourcesTask.NAME, CheckOverlappingSourcesTask.class, task -> {
                    task.setGroup("Verification");
                    task.setDescription(
                            "Checks for overlapping generated Jenkins metadata across main source outputs.");
                    task.getClassesDirs().from(main.getOutput().getClassesDirs());
                    task.getOutputFile()
                            .set(project.getLayout().getBuildDirectory().file("check-overlap/discovered.txt"));
                    task.dependsOn(project.getTasks().named("classes"));
                });
        project.getTasks().named("check", task -> task.dependsOn(checkOverlappingSources));

        var optionalManifest = project.getTasks()
                .register(GenerateOptionalJenkinsManifestTask.NAME, GenerateOptionalJenkinsManifestTask.class, task -> {
                    task.setGroup(BasePlugin.BUILD_GROUP);
                    task.setDescription("Generates optional Jenkins manifest attributes.");
                    task.getInspectionDirectories().from(main.getOutput());
                    task.getOutputFile()
                            .set(project.getLayout().getBuildDirectory().file("jenkins-manifests/optional.mf"));
                });
        var optionalManifestFile = optionalManifest.flatMap(GenerateOptionalJenkinsManifestTask::getOutputFile);

        var jpiTask = project.getTasks()
                .register(
                        JPI_TASK,
                        War.class,
                        new ConfigureJpiAction(
                                project, defaultRuntime, runtimeClasspathArtifacts.getBundledLibraries(), extension));
        jpiTask.configure(war -> {
            war.dependsOn(licenseTask);
            war.getInputs().file(optionalManifestFile);
            war.getManifest().from(optionalManifestFile);
            war.getWebInf().from(licenseTask.flatMap(GenerateLicenseInfoTask::getOutputDirectory));
            war.getArchiveVersion().set(extension.getEffectiveVersion());
            // Gradle's own default for these flipped between the 8.x and 9.x lines; pin them so the
            // archive's bytes (and therefore testServer's build-cache key, which fingerprints this
            // file) don't depend on which Gradle version is running.
            war.setPreserveFileTimestamps(false);
            war.setReproducibleFileOrder(true);
        });
        project.getTasks().named("jar", Jar.class).configure(jarTask -> {
            jarTask.manifest(new ManifestAction(project, extension));
            jarTask.getInputs().file(optionalManifestFile);
            jarTask.getManifest().from(optionalManifestFile);
            jarTask.setPreserveFileTimestamps(false);
            jarTask.setReproducibleFileOrder(true);

            // Resolving defaultRuntime must wait until the jar task actually executes: some publishing
            // plugins (e.g. com.jfrog.artifactory) realize this task from a gradle.projectsEvaluated
            // listener, before projects are configured and before Gradle's exclusive project-execution
            // lock is available, and an eager resolution there is rejected as unsafe.
            var pluginDependencies = project.provider(() -> resolvePluginDependencies(defaultRuntime));
            jarTask.getInputs()
                    .property("pluginDependencies", pluginDependencies)
                    .optional(true);
            jarTask.doFirst(task -> {
                var value = pluginDependencies.getOrNull();
                if (value != null) {
                    jarTask.getManifest().getAttributes().put("Plugin-Dependencies", value);
                }
            });
        });
        Provider<Directory> jpiDirectory =
                project.getLayout().getBuildDirectory().dir("jpi");
        project.getTasks().register(EXPLODED_JPI_TASK, Sync.class, sync -> {
            sync.into(jpiDirectory);
            sync.with(jpiTask.get());
        });
        var generateHpl = project.getTasks().register(GenerateHplTask.TASK_NAME, GenerateHplTask.class, task -> {
            task.setGroup("Jenkins Server");
            task.setDescription("Generate hpl (Hudson plugin link) for running locally");
            task.getHpl()
                    .set(project.getLayout()
                            .getBuildDirectory()
                            .file(extension.getPluginId().map(id -> "hpl/" + id + ".hpl")));
            task.getResourcePath().set(project.file("src/main/webapp"));
            task.getLibraries().from(main.getResources().getSrcDirs());
            task.getLibraries().from(main.getOutput().getClassesDirs());
            task.getLibraries().from(project.provider(main.getOutput()::getResourcesDir));
            task.getLibraries().from(runtimeClasspathArtifacts.getBundledLibraries());
            task.getUpstreamManifest().set(jpiDirectory.map(dir -> dir.file("META-INF/MANIFEST.MF")));
            task.getPluginDependencies().set(project.provider(() -> resolvePluginDependencies(defaultRuntime)));
            task.dependsOn(project.getTasks().named("classes"));
            task.dependsOn(project.getTasks().named(EXPLODED_JPI_TASK));
        });
        project.getTasks().named("assemble", task -> task.dependsOn(jpiTask));

        final var projectRoot =
                project.getLayout().getProjectDirectory().getAsFile().getAbsolutePath();
        final var workDir = WorkDirectorySettings.getWorkDir(project, extension, projectRoot);
        final var serverPort = project.getProviders().gradleProperty("server.port");
        final var prepareServer = createPrepareServerTask(project, workDir, defaultRuntime, jpiTask);
        final var prepareRun = createPrepareRunTask(project, workDir, defaultRuntime, generateHpl);

        project.getGradle().projectsEvaluated(gradle -> {
            var projectByPath = project.getRootProject().getAllprojects().stream()
                    .collect(Collectors.toMap(Project::getPath, it -> it));
            var projectDependencies = getProjectDependencies(runtimeClasspath, projectByPath);
            configureProjectDependencyJpis(
                    prepareServer, getProjectDependencyJpis(projectDependencies, extension.getArchiveExtension()));
            configureProjectDependencyTasks(
                    prepareRun, getProjectDependencyTasks(projectDependencies, GenerateHplTask.TASK_NAME));
            wireUpstreamJpiReferencedFiles(project, projectDependencies);
        });

        project.getTasks()
                .register(
                        "server",
                        JavaExec.class,
                        new ServerAction(serverTaskClasspath, projectRoot, workDir, serverPort, prepareServer));
        project.getTasks()
                .register(
                        "hplRun",
                        JavaExec.class,
                        new ServerAction(serverTaskClasspath, projectRoot, workDir, serverPort, prepareRun));
        project.getPlugins().withType(JavaBasePlugin.class, new SezpozJavaAction(project));
        project.getPlugins().withType(GroovyBasePlugin.class, new SezpozGroovyAction(project));
        AccessModifierConfigurer.configure(project);

        /*
         * We want sezpoz to be the last annotation processor.
         * If other annotation processors contribute methods/controllers that sezpoz expects, this will ensure it happens.
         */
        var lastAnnotationProcessor = project.getConfigurations().create("lastAnnotationProcessor");
        project.getDependencies().add("lastAnnotationProcessor", "net.java.sezpoz:sezpoz:1.13");
        project.getDependencies().add("lastAnnotationProcessor", jenkinsCoreCoordinate);
        project.getConfigurations().getByName("annotationProcessor").extendsFrom(lastAnnotationProcessor);
        lastAnnotationProcessor.extendsFrom(jenkinsCoreAlignment);

        dependencies.add("compileOnly", jenkinsCoreCoordinate);
        dependencies.add("compileOnly", "jakarta.servlet:jakarta.servlet-api:5.0.0");
        dependencies.add(serverTaskClasspath.getName(), jenkinsWarCoordinate);

        dependencies.add("testImplementation", jenkinsCoreCoordinate);
        dependencies.add("testImplementation", jenkinsWarCoordinate);
        dependencies.add("testImplementation", jenkinsTestHarnessCoordinate);
        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter");
        dependencies.add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher");

        TestHarnessConfigurer.configure(project, testDefaultRuntime);

        dependencies.add(jenkinsCore.getName(), jenkinsCoreCoordinate);

        dependencies.getComponents().all(HpiMetadataRule.class);
        dependencies
                .getAttributesSchema()
                .attribute(ARTIFACT_TYPE_ATTRIBUTE)
                .getDisambiguationRules()
                .add(ArtifactTypeDisambiguationRule.class);
        var publicationName = PublishingConfigurer.configure(project, jpiTask, defaultRuntime, extension);

        var publishing = project.getExtensions().getByType(PublishingExtension.class);
        var resolveVersion = project.getTasks().register("resolvePluginVersion", task -> {
            task.doFirst(t -> {
                if (extension.getVersionSource().get() != VersionSource.PROJECT) {
                    var v = extension.getEffectiveVersion().get();
                    publishing
                            .getPublications()
                            .withType(MavenPublication.class)
                            .configureEach(pub -> pub.setVersion(v));
                }
            });
        });
        var capitalizedName = publicationName.isEmpty()
                ? publicationName
                : publicationName.substring(0, 1).toUpperCase() + publicationName.substring(1);
        project.getTasks()
                .named("generatePomFileFor" + capitalizedName + "Publication")
                .configure(t -> t.dependsOn(resolveVersion));
        project.getTasks()
                .named("generateMetadataFileFor" + capitalizedName + "Publication")
                .configure(t -> t.dependsOn(resolveVersion));

        ServerVerificationConfigurer.configure(
                project, serverTaskClasspath, prepareServer, prepareRun, main, runtimeClasspathArtifacts);
    }

    @NotNull
    private static TaskProvider<Sync> createPrepareServerTask(
            @NotNull Project project,
            Provider<String> workDir,
            Configuration defaultRuntime,
            TaskProvider<War> jpiTaskProvider) {
        return project.getTasks()
                .register(
                        "prepareServer",
                        Sync.class,
                        new ConfigurePrepareServerAction(
                                jpiTaskProvider,
                                workDir,
                                defaultRuntime,
                                jpiTaskProvider.flatMap(War::getArchiveFileName),
                                project.getExtensions()
                                        .getByType(JenkinsPluginExtension.class)
                                        .getPluginId(),
                                project.getExtensions()
                                        .getByType(JenkinsPluginExtension.class)
                                        .getArchiveExtension()));
    }

    @NotNull
    private static TaskProvider<Sync> createPrepareRunTask(
            @NotNull Project project,
            Provider<String> workDir,
            Configuration defaultRuntime,
            TaskProvider<GenerateHplTask> hplTaskProvider) {
        return project.getTasks()
                .register(
                        "prepareRun",
                        Sync.class,
                        new ConfigurePrepareRunAction(hplTaskProvider, workDir, defaultRuntime));
    }

    private static void configureProjectDependencyTasks(
            TaskProvider<Sync> prepareTask, List<TaskProvider<?>> projectDependencyTasks) {
        prepareTask.configure(sync -> projectDependencyTasks.forEach(sync::from));
    }

    /**
     * Wire each upstream JPI project dependency's {@code main} source set classes / resources
     * into this project's {@code testHplRun} {@code referencedFiles}. Upstream {@code .hpl}
     * manifests only reference paths to those directories, so without this the consumer's
     * {@code testHplRun} cache would not invalidate when an upstream class's content changes.
     */
    private static void wireUpstreamJpiReferencedFiles(
            @NotNull Project project, @NotNull List<Project> projectDependencies) {
        var hplProjectDeps = projectDependencies.stream()
                .filter(dep -> dep.getTasks().getNames().contains(GenerateHplTask.TASK_NAME))
                .toList();
        if (hplProjectDeps.isEmpty()) {
            return;
        }
        project.getTasks().named("testHplRun", TestServerTask.class, task -> {
            for (var dep : hplProjectDeps) {
                task.getReferencedFiles()
                        .from(dep.getTasks()
                                .named(GenerateHplTask.TASK_NAME, GenerateHplTask.class)
                                .map(GenerateHplTask::getLibraries));
            }
        });
    }

    private static void configureProjectDependencyJpis(
            TaskProvider<Sync> prepareTask, List<TaskWithRename> projectDependencyJpis) {
        prepareTask.configure(sync -> projectDependencyJpis.forEach(
                task -> sync.from(task.getTaskProvider(), spec -> spec.rename(task.getRenameTransformer()))));
    }

    @NotNull
    private static List<TaskProvider<?>> getProjectDependencyTasks(List<Project> projectDependencies, String taskName) {
        return projectDependencies.stream()
                .filter(it -> it.getTasks().getNames().contains(taskName))
                .sorted(Comparator.comparing(Project::getName))
                .map(it -> it.getTasks().named(taskName))
                .collect(Collectors.toList());
    }

    @NotNull
    private static List<TaskWithRename> getProjectDependencyJpis(
            List<Project> projectDependencies, Provider<String> targetExtension) {
        return projectDependencies.stream()
                .filter(it -> it.getTasks().getNames().contains(JPI_TASK))
                .sorted(Comparator.comparing(Project::getName))
                .map(it -> new TaskWithRename(
                        it.getTasks().named(JPI_TASK),
                        new PluginArchiveRenamer(
                                it.getTasks().named(JPI_TASK, War.class).flatMap(War::getArchiveFileName),
                                it.getExtensions()
                                        .getByType(JenkinsPluginExtension.class)
                                        .getPluginId(),
                                targetExtension)))
                .collect(Collectors.toList());
    }

    @NotNull
    private static List<Project> getProjectDependencies(
            Configuration runtimeClasspath, Map<String, Project> projectByPath) {
        Map<String, Project> projectsByPath = new LinkedHashMap<>();
        collectProjectDependencies(runtimeClasspath, projectByPath, projectsByPath, new LinkedHashSet<>());
        return List.copyOf(projectsByPath.values());
    }

    private static void collectProjectDependencies(
            Configuration configuration,
            Map<String, Project> projectByPath,
            Map<String, Project> projectsByPath,
            Set<String> visitedProjectPaths) {
        configuration.getAllDependencies().withType(ProjectDependency.class).forEach(projectDependency -> {
            var dependencyProjectPath = getProjectDependencyPath(projectDependency);
            var dependencyProject = dependencyProjectPath == null ? null : projectByPath.get(dependencyProjectPath);
            if (dependencyProject == null || !visitedProjectPaths.add(dependencyProject.getPath())) {
                return;
            }

            projectsByPath.put(dependencyProject.getPath(), dependencyProject);

            var dependencyRuntimeClasspath =
                    dependencyProject.getConfigurations().findByName("runtimeClasspath");
            if (dependencyRuntimeClasspath != null) {
                collectProjectDependencies(
                        dependencyRuntimeClasspath, projectByPath, projectsByPath, visitedProjectPaths);
            }
        });
    }

    private static String getProjectDependencyPath(ProjectDependency projectDependency) {
        try {
            var getPath = projectDependency.getClass().getMethod("getPath");
            var projectPath = getPath.invoke(projectDependency);
            if (projectPath instanceof String path) {
                return path;
            }
        } catch (ReflectiveOperationException ignored) {
            // Fall through to older Gradle APIs.
        }

        try {
            var getDependencyProject = projectDependency.getClass().getMethod("getDependencyProject");
            var dependencyProject = getDependencyProject.invoke(projectDependency);
            if (dependencyProject instanceof Project project) {
                return project.getPath();
            }
        } catch (ReflectiveOperationException ignored) {
            return null;
        }

        return null;
    }

    @NotNull
    private static Configuration createServerTaskClasspathConfiguration(@NotNull Project project) {
        return project.getConfigurations().create("serverTaskClasspath", c -> {
            c.setCanBeConsumed(false);
            c.setTransitive(false);
        });
    }

    @NotNull
    private static Provider<Set<File>> resolvePomFiles(
            @NotNull Project project, RuntimeClasspathArtifacts runtimeClasspathArtifacts) {
        return project.provider(() -> {
            var pomCoordinates = runtimeClasspathArtifacts.getBundledModules().stream()
                    .map(id -> id.getGroup() + ":" + id.getModule() + ":" + id.getVersion() + "@pom")
                    .distinct()
                    .toList();

            var deps = pomCoordinates.stream()
                    .map(coord -> project.getDependencies().create(coord))
                    .toArray(Dependency[]::new);

            var detached = project.getConfigurations().detachedConfiguration(deps);
            detached.getAttributes()
                    .attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));

            var artifacts = detached.getIncoming()
                    .artifactView(view -> view.setLenient(true))
                    .getArtifacts();
            for (var failure : artifacts.getFailures()) {
                project.getLogger().warn("License metadata is incomplete: {}", failure.getMessage());
            }
            return artifacts.getArtifactFiles().getFiles();
        });
    }

    private static String getVersionFromProperties(
            @NotNull Project project, String propertyName, String defaultVersion) {
        Provider<String> myProperty = project.getProviders().gradleProperty(propertyName);
        return myProperty.getOrElse(defaultVersion);
    }

    @Nullable
    static String resolvePluginDependencies(@NotNull Configuration defaultRuntime) {
        var rootDependencies =
                defaultRuntime.getIncoming().getResolutionResult().getRoot().getDependencies();

        var pluginDependencies = rootDependencies.stream()
                .filter(it -> !it.isConstraint())
                .filter(it -> it instanceof ResolvedDependencyResult)
                .map(it -> ((ResolvedDependencyResult) it))
                .filter(it -> it.getResolvedVariant().getDisplayName().equals(HpiMetadataRule.DEFAULT_RUNTIME_VARIANT))
                .map(it -> it.getSelected().getModuleVersion())
                .filter(Objects::nonNull)
                .map(it -> it.getName() + ":" + it.getVersion())
                .toList();

        return pluginDependencies.isEmpty() ? null : String.join(",", pluginDependencies);
    }
}
