package org.jenkinsci.gradle.plugins.jpi2.localization;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;

import java.lang.reflect.InvocationTargetException;

import static org.gradle.api.attributes.Usage.JAVA_RUNTIME;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;
import static org.jenkinsci.gradle.plugins.jpi2.JenkinsPluginExtension.DEFAULT_LOCALIZER_VERSION;

/**
 * Plugin that generates Java classes from Messages.properties files.
 */
public class LocalizationPlugin implements Plugin<Project> {

    static final String TASK_NAME = "localizeMessages";
    private static final String CONFIGURATION_NAME = "localizeMessagesRuntimeClasspath";
    private static final String LOCALIZER_MAVEN_PLUGIN = "org.jvnet.localizer:localizer-maven-plugin:";

    @Override
    public void apply(Project target) {
        target.getPluginManager().apply(JavaPlugin.class);
        var jenkinsPlugin = target.getExtensions().findByType(org.jenkinsci.gradle.plugins.jpi2.JenkinsPluginExtension.class);
        JavaPluginExtension extension = target.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = extension.getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceDirectorySet mainResources = main.getResources();
        TaskContainer tasks = target.getTasks();
        ObjectFactory objects = target.getObjects();

        var localizerVersion = jenkinsPlugin != null
                ? jenkinsPlugin.getLocalizerVersion()
                : target.getProviders().provider(() -> DEFAULT_LOCALIZER_VERSION);
        Object localizeMessagesRuntimeClasspath = createRuntimeClasspath(target, objects);
        target.getDependencies().addProvider(CONFIGURATION_NAME,
                localizerVersion.map(version -> LOCALIZER_MAVEN_PLUGIN + version));

        TaskProvider<LocalizationTask> localizeMessages = tasks.register(
                TASK_NAME,
                LocalizationTask.class,
                new RegistrationActions(target, mainResources, localizeMessagesRuntimeClasspath));

        main.getJava().srcDir(localizeMessages);
    }

    private static class RegistrationActions implements Action<LocalizationTask> {
        private static final String DESCRIPTION = "Generates Java source files for **/Messages.properties";

        private final Project project;
        private final SourceDirectorySet source;
        private final Object runtimeClasspath;

        private RegistrationActions(Project project, SourceDirectorySet source, Object runtimeClasspath) {
            this.project = project;
            this.source = source;
            this.runtimeClasspath = runtimeClasspath;
        }

        @Override
        public void execute(LocalizationTask task) {
            task.setDescription(DESCRIPTION);
            task.setGroup(BasePlugin.BUILD_GROUP);
            task.setSource(source);
            task.getSourceRoots().from(source.getSrcDirs());
            task.getLocalizerClasspath().from(runtimeClasspath);
            task.getOutputDir().convention(project.getLayout().getBuildDirectory().dir("generated-src/localizer"));
        }
    }

    private static Object createRuntimeClasspath(Project target, ObjectFactory objects) {
        Action<Configuration> configure = c ->
                c.getAttributes().attribute(USAGE_ATTRIBUTE, objects.named(Usage.class, JAVA_RUNTIME));

        try {
            var resolvable = target.getConfigurations().getClass()
                    .getMethod("resolvable", String.class, Action.class);
            return resolvable.invoke(target.getConfigurations(), CONFIGURATION_NAME, configure);
        } catch (NoSuchMethodException ignored) {
            return target.getConfigurations().create(CONFIGURATION_NAME, c -> {
                configure.execute(c);
                c.setCanBeConsumed(false);
                c.setCanBeResolved(true);
            });
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new IllegalStateException("Failed to create " + CONFIGURATION_NAME, e);
        }
    }
}
