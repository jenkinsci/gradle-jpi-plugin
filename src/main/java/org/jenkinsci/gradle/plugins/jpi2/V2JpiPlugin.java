package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.FileCollection;
import org.gradle.api.java.archives.Manifest;
import org.gradle.api.plugins.JavaLibraryPlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.War;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("Convert2Lambda")
public class V2JpiPlugin implements Plugin<Project> {

    public static final String COMPILE_ONLY_CONFIGURATION = "compileOnly";
    public static final String JENKINS_PLUGIN_COMPILE_ONLY_CONFIGURATION = "jenkinsPluginCompileOnly";
    public static final String JENKINS_PLUGIN_CONFIGURATION = "jenkinsPlugin";
    public static final String SERVER_JENKINS_PLUGIN_CONFIGURATION = "serverJenkinsPlugin";
    public static final String SERVER_TASK_CLASSPATH_CONFIGURATION = "serverTaskClasspath";

    public static final String EXPLODED_JPI_TASK = "explodedJpi";
    public static final String JPI_TASK = "war";

    public static final String JENKINS_VERSION_PROPERTY = "jenkins.version";

    @Override
    public void apply(@NotNull Project project) {
        project.getPlugins().apply(JavaLibraryPlugin.class);
        project.getPlugins().apply(WarPlugin.class);

        var jenkinsPlugin = project.getConfigurations().create(JENKINS_PLUGIN_CONFIGURATION);
        var jenkinsPluginCompileOnly = createJavaPluginsCompileOnlyConfiguration(project, jenkinsPlugin);
        project.getConfigurations().getByName(COMPILE_ONLY_CONFIGURATION).extendsFrom(jenkinsPluginCompileOnly);

        var serverJenkinsPlugin = createServerJenkinsPluginConfiguration(project, jenkinsPlugin);
        var serverTaskClasspath = createServerTaskClasspathConfiguration(project);

        var jpiTask = configureJpiTask(project, jenkinsPlugin);

        final var projectRoot = project.getLayout().getProjectDirectory().getAsFile().getAbsolutePath();
        final var prepareServer = createPrepareServerTask(project, projectRoot, serverJenkinsPlugin);
        var serverTask = createServerTask(project, serverTaskClasspath, projectRoot, prepareServer);
    }

    @NotNull
    private static TaskProvider<JavaExec> createServerTask(@NotNull Project project, Configuration serverTaskClasspath, String projectRoot, TaskProvider<Copy> prepareServer) {
        return project.getTasks().register("server", JavaExec.class, new Action<>() {
            @Override
            public void execute(@NotNull JavaExec spec) {
                spec.classpath(serverTaskClasspath);
                spec.setStandardOutput(System.out);
                spec.setErrorOutput(System.err);
                spec.args(List.of(
                        "--webroot=" + projectRoot + "/build/jenkins/war",
                        "--pluginroot=" + projectRoot + "/build/jenkins/plugins",
                        "--extractedFilesFolder=" + projectRoot + "/build/jenkins/extracted",
                        "--commonLibFolder=" + projectRoot + "/work/lib"
                ));
                spec.environment("JENKINS_HOME", projectRoot + "/work");

                spec.dependsOn(prepareServer);

                spec.getOutputs().upToDateWhen(new Spec<>() {
                    @Override
                    public boolean isSatisfiedBy(Task element) {
                        return false;
                    }
                });
            }
        });
    }

    @NotNull
    private static TaskProvider<Copy> createPrepareServerTask(@NotNull Project project, String projectRoot, Configuration serverJenkinsPlugin) {
        return project.getTasks()
                .register("prepareServer", Copy.class, new Action<>() {
                    @Override
                    public void execute(@NotNull Copy copy) {
                        var war = project.getTasks().getByName(JPI_TASK);

                        copy.from(war.getOutputs().getFiles().getSingleFile())
                                .into(projectRoot + "/work/plugins");

                        copy.from(serverJenkinsPlugin)
                                .include("**/*.jpi", "**/*.hpi")
                                .into(projectRoot + "/work/plugins");
                    }
                });
    }

    @NotNull
    private static Configuration createServerTaskClasspathConfiguration(@NotNull Project project) {
        return project.getConfigurations().create(SERVER_TASK_CLASSPATH_CONFIGURATION, new Action<>() {
            @Override
            public void execute(@NotNull Configuration c) {
                c.setCanBeConsumed(false);
                c.setTransitive(false);
                c.withDependencies(new Action<>() {
                    @Override
                    public void execute(@NotNull DependencySet dependencies) {
                        dependencies.add(project.getDependencies()
                                .create("org.jenkins-ci.main:jenkins-war:" + project.getProperties().get(JENKINS_VERSION_PROPERTY)));
                    }
                });
            }
        });
    }

    @NotNull
    private static Configuration createServerJenkinsPluginConfiguration(@NotNull Project project, Configuration jenkinsPlugin) {
        return project.getConfigurations().create(SERVER_JENKINS_PLUGIN_CONFIGURATION, new Action<>() {
            @Override
            public void execute(@NotNull Configuration c) {
                c.extendsFrom(jenkinsPlugin);
                c.withDependencies(new Action<>() {
                    @Override
                    public void execute(@NotNull DependencySet dependencies) {
                        jenkinsPlugin.getDependencies().forEach(it -> {
                            if (it instanceof ExternalModuleDependency) {
                                dependencies.add(project.getDependencies()
                                        .create(it.getGroup() + ":" + it.getName() + ":" + it.getVersion()));
                            } else if (it instanceof ProjectDependency p) {
                                Project rootProject = project.getRootProject();
                                Project pluginProject = rootProject.getChildProjects()
                                        .get(p.getName());
                                Task warTask = pluginProject.getTasks().findByName(JPI_TASK);
                                if (warTask != null) {
                                    dependencies.add(project.getDependencies().create(warTask.getOutputs().getFiles()));
                                    Configuration jenkinsPluginFromDependentProject = pluginProject
                                            .getConfigurations()
                                            .getByName("jenkinsPlugin");
                                    dependencies.add(project.getDependencies().create(jenkinsPluginFromDependentProject));
                                }
                            }
                        });
                    }
                });
            }
        });
    }

    @NotNull
    private Configuration createJavaPluginsCompileOnlyConfiguration(@NotNull Project project, Configuration jenkinsPlugin) {
        return project.getConfigurations().create(JENKINS_PLUGIN_COMPILE_ONLY_CONFIGURATION, new Action<>() {
            @Override
            public void execute(@NotNull Configuration c) {
                c.withDependencies(new Action<>() {
                    @Override
                    public void execute(@NotNull DependencySet dependencies) {
                        addJarDependenciesFromJpis(project, jenkinsPlugin, dependencies);
                        dependencies.add(project.getDependencies()
                                .create("org.jenkins-ci.main:jenkins-core:" + project.getProperties().get(JENKINS_VERSION_PROPERTY)));
                    }
                });
            }
        });
    }

    private void addJarDependenciesFromJpis(@NotNull Project project, Configuration jpiSource, @NotNull DependencySet jarSink) {
        jpiSource.getAllDependencies()
                .forEach(it -> {
                    if (it instanceof ExternalModuleDependency) {
                        jarSink.add(project.getDependencies()
                                .create(it.getGroup() + ":" + it.getName() + ":" + it.getVersion() + "@jar"));
                    } else if (it instanceof ProjectDependency p) {
                        Project rootProject = project.getRootProject();
                        Map<String, Project> childProjects = rootProject.getChildProjects();
                        Task jar = childProjects.get(p.getName()).getTasks().getByName("jar");
                        jarSink.add(project.getDependencies().create(jar.getOutputs().getFiles()));
                    }
                });
    }

    private static TaskProvider<War> configureJpiTask(@NotNull Project project, Configuration jenkinsPlugin/*, Configuration jpi*/) {
        project.getTasks().register(EXPLODED_JPI_TASK, Copy.class, new Action<>() {
            @Override
            public void execute(@NotNull Copy sync) {
                sync.into(project.getLayout().getBuildDirectory().dir("jpi"));
                sync.with((War) project.getTasks().getByName(JPI_TASK));
            }
        });
        var jpiTask = project.getTasks().named(JPI_TASK, War.class);
        jpiTask.configure(new Action<>() {
            @Override
            public void execute(@NotNull War jpi) {
                jpi.getArchiveExtension().set("jpi");
                configureManifest(project, jenkinsPlugin, jpi);
                jpi.from(project.getTasks().named("jar"), new Action<>() {
                    @Override
                    public void execute(@NotNull CopySpec copySpec) {
                        copySpec.into("WEB-INF/lib");
                    }
                });
                var classpath = new HashSet<>(Optional.ofNullable(jpi.getClasspath()).map(FileCollection::getFiles).orElse(Set.of()));
                classpath.removeIf(it -> !it.getName().endsWith(".jar"));
                jpi.setClasspath(classpath);
                jpi.finalizedBy(EXPLODED_JPI_TASK);
            }
        });
        return jpiTask;
    }

    private static void configureManifest(@NotNull Project project, Configuration jenkinsPlugin, War war) {
        war.manifest(new Action<>() {
            @Override
            public void execute(@NotNull Manifest manifest) {
                var pluginDependencies = jenkinsPlugin.getDependencies()
                        .stream()
                        .map(it -> it.getName() + ":" + it.getVersion())
                        .collect(Collectors.joining(","));

                manifest.getAttributes()
                        .put("Implementation-Title", project.getGroup() + "#" + project.getName() + ";" + project.getVersion());
                manifest.getAttributes().put("Implementation-Version", project.getVersion());
                if (!pluginDependencies.isEmpty()) {
                    manifest.getAttributes().put("Plugin-Dependencies", pluginDependencies);
                }
                manifest.getAttributes().put("Plugin-Version", project.getVersion());
                manifest.getAttributes().put("Short-Name", project.getName());
                manifest.getAttributes().put("Long-Name", Optional.ofNullable(project.getDescription()).orElse(project.getName()));

                manifest.getAttributes().put("Jenkins-Version", project.getProperties().get(JENKINS_VERSION_PROPERTY));
            }
        });
    }
}
