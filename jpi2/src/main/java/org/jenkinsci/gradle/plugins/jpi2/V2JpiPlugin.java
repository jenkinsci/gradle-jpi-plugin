package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
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
import org.gradle.api.services.BuildServiceRegistry;
import org.gradle.StartParameter;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.api.tasks.bundling.War;
import org.jenkinsci.gradle.plugins.jpi2.localization.LocalizationPlugin;
import org.jenkinsci.gradle.plugins.jpi2.accmod.CheckAccessModifierTask;
import org.jenkinsci.gradle.plugins.jpi2.accmod.PrefixedPropertiesProvider;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
        RepositoryShortcuts.registerRepositoryShortcuts(project.getRepositories());
        var publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
        RepositoryShortcuts.registerRepositoryShortcuts(publishingExtension.getRepositories(), project);

        var extension = project.getExtensions().create("jenkinsPlugin", JenkinsPluginExtension.class, project);
        ((ExtensionAware) extension).getExtensions().create("gitVersion", GitVersionExtension.class,
                project.getObjects(), project.getLayout(), project.getProviders());

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

        var pomFiles = resolvePomFiles(project, defaultRuntime);
        var licenseTask = project.getTasks().register(GenerateLicenseInfoTask.NAME, GenerateLicenseInfoTask.class, new Action<>() {
            @Override
            public void execute(@NotNull GenerateLicenseInfoTask task) {
                task.setGroup(BasePlugin.BUILD_GROUP);
                task.setDescription("Generates license information.");
                task.getOutputDirectory().set(project.getLayout().getBuildDirectory().dir("licenses"));
                task.getPomFiles().from(pomFiles);
                task.getProjectVersion().set(project.provider(() -> project.getVersion().toString()));
                task.getProjectName().set(project.getName());
                task.getProjectGroup().set(project.provider(() -> project.getGroup().toString()));
                task.getProjectDescription().set(project.provider(project::getDescription));
                task.getProjectUrl().set(project.getProviders().gradleProperty("url"));
            }
        });

        var testCompileClasspath = configurations.getByName("testCompileClasspath");
        testCompileClasspath.shouldResolveConsistentlyWith(jenkinsCore);

        JavaPluginExtension ext = project.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = ext.getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        main.getResources().getSrcDirs().add(project.file("src/main/webapp"));
        project.getPlugins().apply(LocalizationPlugin.class);
        var checkOverlappingSources = project.getTasks().register(
                CheckOverlappingSourcesTask.NAME,
                CheckOverlappingSourcesTask.class,
                task -> {
                    task.setGroup("Verification");
                    task.setDescription("Checks for overlapping generated Jenkins metadata across main source outputs.");
                    task.getClassesDirs().from(main.getOutput().getClassesDirs());
                    task.getOutputFile().set(project.getLayout().getBuildDirectory().file("check-overlap/discovered.txt"));
                    task.dependsOn(project.getTasks().named("classes"));
                });
        project.getTasks().named("check", task -> task.dependsOn(checkOverlappingSources));

        var optionalManifest = project.getTasks().register(
                GenerateOptionalJenkinsManifestTask.NAME,
                GenerateOptionalJenkinsManifestTask.class,
                task -> {
                    task.setGroup(BasePlugin.BUILD_GROUP);
                    task.setDescription("Generates optional Jenkins manifest attributes.");
                    task.getInspectionDirectories().from(main.getOutput());
                    task.getOutputFile().set(project.getLayout().getBuildDirectory().file("jenkins-manifests/optional.mf"));
                });
        var optionalManifestFile = optionalManifest.flatMap(GenerateOptionalJenkinsManifestTask::getOutputFile);

        var jpiTask = project.getTasks().register(JPI_TASK, War.class, new ConfigureJpiAction(project, defaultRuntime, jenkinsCore, extension));
        jpiTask.configure(new Action<>() {
            @Override
            public void execute(@NotNull War war) {
                war.dependsOn(licenseTask);
                war.getInputs().file(optionalManifestFile);
                war.getManifest().from(optionalManifestFile);
                war.getWebInf().from(licenseTask.flatMap(GenerateLicenseInfoTask::getOutputDirectory));
                war.getArchiveVersion().set(extension.getEffectiveVersion());
            }
        });
        project.getTasks().named("jar", Jar.class).configure(new Action<>() {
            @Override
            public void execute(@NotNull Jar jarTask) {
                jarTask.manifest(new ManifestAction(project, defaultRuntime, extension));
                jarTask.getInputs().file(optionalManifestFile);
                jarTask.getManifest().from(optionalManifestFile);
            }
        });
        Provider<Directory> jpiDirectory = project.getLayout().getBuildDirectory().dir("jpi");
        var runtimeClasspathArtifacts = new RuntimeClasspathArtifacts(project, defaultRuntime, jenkinsCore);
        project.getTasks().register(EXPLODED_JPI_TASK, Sync.class, new Action<>() {
            @Override
            public void execute(@NotNull Sync sync) {
                sync.into(jpiDirectory);
                sync.with(jpiTask.get());
            }
        });
        var generateHpl = project.getTasks().register(GenerateHplTask.TASK_NAME, GenerateHplTask.class, new Action<>() {
            @Override
            public void execute(@NotNull GenerateHplTask task) {
                task.setGroup("Jenkins Server");
                task.setDescription("Generate hpl (Hudson plugin link) for running locally");
                task.getHpl().set(project.getLayout().getBuildDirectory()
                        .file(extension.getPluginId().map(id -> "hpl/" + id + ".hpl")));
                task.getResourcePath().set(project.file("src/main/webapp"));
                task.getLibraries().from(main.getResources().getSrcDirs());
                task.getLibraries().from(main.getOutput().getClassesDirs());
                task.getLibraries().from(project.provider(main.getOutput()::getResourcesDir));
                task.getLibraries().from(runtimeClasspathArtifacts.getBundledLibraries());
                task.getUpstreamManifest().set(jpiDirectory.map(dir -> dir.file("META-INF/MANIFEST.MF")));
                task.dependsOn(project.getTasks().named("classes"));
                task.dependsOn(project.getTasks().named(EXPLODED_JPI_TASK));
            }
        });
        project.getTasks().named("assemble", new Action<>() {
            @Override
            public void execute(@NotNull Task task) {
                task.dependsOn(jpiTask);
            }
        });

        final var projectRoot = project.getLayout().getProjectDirectory().getAsFile().getAbsolutePath();
        final var workDir = WorkDirectorySettings.getWorkDir(project, extension, projectRoot);
        final var prepareServer = createPrepareServerTask(project, workDir, defaultRuntime, jpiTask);
        final var prepareRun = createPrepareRunTask(project, workDir, defaultRuntime, generateHpl);

        project.getGradle().projectsEvaluated(gradle -> {
            var projectByPath = project.getRootProject().getAllprojects().stream()
                    .collect(Collectors.toMap(Project::getPath, it -> it));
            var projectDependencies = getProjectDependencies(runtimeClasspath, projectByPath);
            configureProjectDependencyJpis(prepareServer, getProjectDependencyJpis(projectDependencies, extension.getArchiveExtension().get()));
            configureProjectDependencyTasks(prepareRun, getProjectDependencyTasks(projectDependencies, GenerateHplTask.TASK_NAME));
        });

        project.getTasks().register("server", JavaExec.class, new ServerAction(serverTaskClasspath, projectRoot, workDir, prepareServer));
        project.getTasks().register("hplRun", JavaExec.class, new ServerAction(serverTaskClasspath, projectRoot, workDir, prepareRun));
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
        var publicationName = configurePublishing(project, jpiTask, defaultRuntime, extension);

        var publishing = project.getExtensions().getByType(PublishingExtension.class);
        var resolveVersion = project.getTasks().register("resolvePluginVersion", task -> {
            task.doFirst(t -> {
                if (extension.getVersionSource().get() != VersionSource.PROJECT) {
                    var v = extension.getEffectiveVersion().get();
                    publishing.getPublications().withType(MavenPublication.class).configureEach(pub -> pub.setVersion(v));
                }
            });
        });
        var capitalizedName = publicationName.isEmpty() ? publicationName : publicationName.substring(0, 1).toUpperCase() + publicationName.substring(1);
        project.getTasks().named("generatePomFileFor" + capitalizedName + "Publication").configure(t -> t.dependsOn(resolveVersion));
        project.getTasks().named("generateMetadataFileFor" + capitalizedName + "Publication").configure(t -> t.dependsOn(resolveVersion));

        BuildServiceRegistry buildServices = project.getGradle().getSharedServices();
        var portAllocationService = buildServices.registerIfAbsent("portAllocation", PortAllocationService.class, spec -> {
        });

        var gradle = project.getGradle();
        var startParameter = gradle.getStartParameter();
        var gradleHome = gradle.getGradleHomeDir();
        var gradleExecutable = gradleHome != null ? new File(gradleHome, "bin/gradle").getAbsolutePath() : "gradle";
        var isRootProject = project == project.getRootProject();
        var projectPath = project.getPath();

        registerTestTask(project, portAllocationService, gradleExecutable, startParameter, isRootProject, projectPath,
                "testServer", "Launch Jenkins server and terminate after success or first error", ":server");
        registerTestTask(project, portAllocationService, gradleExecutable, startParameter, isRootProject, projectPath,
                "testHplRun", "Launch Jenkins hplRun task and terminate after success or first error", ":hplRun");
    }

    private static void registerTestTask(@NotNull Project project, @NotNull Provider<PortAllocationService> portAllocationService,
                                         @NotNull String gradleExecutable, @NotNull StartParameter startParameter,
                                         boolean isRootProject, @NotNull String projectPath, @NotNull String taskName,
                                         @NotNull String description, @NotNull String taskSuffix) {
        project.getTasks().register(taskName, TestServerTask.class, new Action<>() {
            @Override
            public void execute(@NotNull TestServerTask task) {
                task.setGroup("verification");
                task.setDescription(description);
                task.getRootDir().set(project.getRootDir().getAbsolutePath());
                task.getGradleExecutable().set(gradleExecutable);
                task.getJavaHome().set(System.getProperty("java.home"));
                task.getInitScripts().set(startParameter.getAllInitScripts().stream()
                        .map(File::getAbsolutePath).toList());
                task.getIncludedBuilds().set(startParameter.getIncludedBuilds().stream()
                        .map(File::getPath).toList());
                task.getOffline().set(startParameter.isOffline());
                task.getBuildCacheEnabled().set(startParameter.isBuildCacheEnabled());
                task.getRefreshDependencies().set(startParameter.isRefreshDependencies());
                task.getContinueOnFailure().set(startParameter.isContinueOnFailure());
                task.getParallelExecution().set(startParameter.isParallelProjectExecutionEnabled());
                task.getProfile().set(startParameter.isProfile());
                task.getRerunTasks().set(startParameter.isRerunTasks());
                task.getDryRun().set(startParameter.isDryRun());
                task.getSystemProperties().set(startParameter.getSystemPropertiesArgs());
                task.getProjectProperties().set(startParameter.getProjectProperties());
                task.getServerTaskPath().set(isRootProject ? taskSuffix : projectPath + taskSuffix);
                task.getPortAllocationService().set(portAllocationService);
                task.usesService(portAllocationService);
            }
        });
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

    /** Returns the name of the publication that was configured (for task name resolution). */
    @NotNull
    private static String configurePublishing(@NotNull Project project, TaskProvider<?> jpiTask, Configuration runtimeClasspath, JenkinsPluginExtension extension) {
        var publishingExtension = project.getExtensions().getByType(PublishingExtension.class);
        var existingPublication = !publishingExtension.getPublications().isEmpty() ? publishingExtension.getPublications().iterator().next() : null;
        var javaPlugin = project.getExtensions().getByType(JavaPluginExtension.class);
        javaPlugin.withJavadocJar();
        javaPlugin.withSourcesJar();
        if (existingPublication instanceof MavenPublication publication) {
            configurePublication(publication, jpiTask, runtimeClasspath, project, extension);
            return publication.getName();
        } else {
            publishingExtension.getPublications().create("mavenJpi", MavenPublication.class, new Action<>() {
                @Override
                public void execute(@NotNull MavenPublication publication) {
                    publication.from(project.getComponents().getByName("java"));
                    configurePublication(publication, jpiTask, runtimeClasspath, project, extension);
                }
            });
            return "mavenJpi";
        }
    }

    private static void configurePublication(@NotNull MavenPublication publication, TaskProvider<?> jpiTask, Configuration runtimeClasspath, Project project, JenkinsPluginExtension extension) {
        publication.artifact(jpiTask);
        publication.getPom().setPackaging(extension.getArchiveExtension().get());
        publication.getPom().getName().set(extension.getDisplayName());
        publication.getPom().getUrl().set(extension.getHomePage().map(URI::toASCIIString));
        publication.getPom().getDescription().set(project.provider(project::getDescription));
        publication.getPom().withXml(new PomBuilder(runtimeClasspath, project, extension));
    }

    @NotNull
    private static TaskProvider<Sync> createPrepareServerTask(@NotNull Project project, Provider<String> workDir, Configuration defaultRuntime,
                                                              TaskProvider<?> jpiTaskProvider) {
        return project.getTasks().register("prepareServer", Sync.class, new ConfigurePrepareServerAction(
                jpiTaskProvider,
                workDir,
                defaultRuntime,
                project.provider(project::getName),
                project.provider(() -> project.getVersion().toString()),
                project.getExtensions().getByType(JenkinsPluginExtension.class).getArchiveExtension()
        ));
    }

    @NotNull
    private static TaskProvider<Sync> createPrepareRunTask(@NotNull Project project, Provider<String> workDir, Configuration defaultRuntime,
                                                           TaskProvider<GenerateHplTask> hplTaskProvider) {
        return project.getTasks().register("prepareRun", Sync.class, new ConfigurePrepareRunAction(
                hplTaskProvider,
                workDir,
                defaultRuntime
        ));
    }

    private static void configureProjectDependencyTasks(TaskProvider<Sync> prepareTask, List<TaskProvider<?>> projectDependencyTasks) {
        prepareTask.configure(sync -> projectDependencyTasks.forEach(sync::from));
    }

    private static void configureProjectDependencyJpis(TaskProvider<Sync> prepareTask, List<TaskWithRename> projectDependencyJpis) {
        prepareTask.configure(sync -> projectDependencyJpis.forEach(task ->
                sync.from(task.getTaskProvider()).rename(task.getRenameTransformer())
        ));
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
    private static List<TaskWithRename> getProjectDependencyJpis(List<Project> projectDependencies, String targetExtension) {
        return projectDependencies.stream()
                .filter(it -> it.getTasks().getNames().contains(JPI_TASK))
                .sorted(Comparator.comparing(Project::getName))
                .map(it -> new TaskWithRename(
                        it.getTasks().named(JPI_TASK),
                        new DropVersionTransformer(
                                it.getName(),
                                it.getVersion().toString(),
                                targetExtension
                        )
                ))
                .collect(Collectors.toList());
    }

    @NotNull
    private static List<Project> getProjectDependencies(Configuration runtimeClasspath, Map<String, Project> projectByPath) {
        Map<String, Project> projectsByPath = new LinkedHashMap<>();
        collectProjectDependencies(runtimeClasspath, projectByPath, projectsByPath, new LinkedHashSet<>());
        return List.copyOf(projectsByPath.values());
    }

    private static void collectProjectDependencies(Configuration configuration, Map<String, Project> projectByPath,
                                                   Map<String, Project> projectsByPath,
                                                   Set<String> visitedProjectPaths) {
        configuration.getAllDependencies().withType(ProjectDependency.class).forEach(projectDependency -> {
            var dependencyProjectPath = getProjectDependencyPath(projectDependency);
            var dependencyProject = dependencyProjectPath == null ? null : projectByPath.get(dependencyProjectPath);
            if (dependencyProject == null || !visitedProjectPaths.add(dependencyProject.getPath())) {
                return;
            }

            projectsByPath.put(dependencyProject.getPath(), dependencyProject);

            var dependencyRuntimeClasspath = dependencyProject.getConfigurations().findByName("runtimeClasspath");
            if (dependencyRuntimeClasspath != null) {
                collectProjectDependencies(dependencyRuntimeClasspath, projectByPath, projectsByPath, visitedProjectPaths);
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
        return project.getConfigurations().create("serverTaskClasspath", new Action<>() {
            @Override
            public void execute(@NotNull Configuration c) {
                c.setCanBeConsumed(false);
                c.setTransitive(false);
            }
        });
    }

    @NotNull
    private static Provider<Set<File>> resolvePomFiles(@NotNull Project project, Configuration libraryConfiguration) {
        return project.provider(() -> {
            var pomCoordinates = libraryConfiguration.getResolvedConfiguration().getResolvedArtifacts().stream()
                    .filter(artifact -> "jar".equals(artifact.getExtension()))
                    .filter(artifact -> artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)
                    .map(artifact -> {
                        var id = artifact.getModuleVersion().getId();
                        return id.getGroup() + ":" + id.getName() + ":" + id.getVersion() + "@pom";
                    })
                    .toList();

            var deps = pomCoordinates.stream()
                    .map(coord -> project.getDependencies().create(coord))
                    .toArray(Dependency[]::new);

            var detached = project.getConfigurations().detachedConfiguration(deps);
            detached.getAttributes().attribute(Usage.USAGE_ATTRIBUTE, project.getObjects().named(Usage.class, Usage.JAVA_RUNTIME));

            var lenient = detached.getResolvedConfiguration().getLenientConfiguration();
            return lenient.getArtifacts().stream()
                    .map(ResolvedArtifact::getFile)
                    .collect(Collectors.toSet());
        });
    }

    private static String getVersionFromProperties(@NotNull Project project, String propertyName, String defaultVersion) {
        Provider<String> myProperty = project.getProviders().gradleProperty(propertyName);
        return myProperty.getOrElse(defaultVersion);
    }

}
