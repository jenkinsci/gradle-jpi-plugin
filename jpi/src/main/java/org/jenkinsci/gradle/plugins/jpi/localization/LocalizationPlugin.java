package org.jenkinsci.gradle.plugins.jpi.localization;

import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.SourceDirectorySet;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.BasePlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.compile.JavaCompile;

import static org.gradle.api.attributes.Usage.JAVA_RUNTIME;
import static org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE;

public class LocalizationPlugin implements Plugin<Project> {

    private static final String CONFIGURATION_NAME = "localizeMessagesRuntimeClasspath";

    @Override
    public void apply(Project target) {
        JavaPluginExtension ext = target.getExtensions().getByType(JavaPluginExtension.class);
        SourceSetContainer sourceSets = ext.getSourceSets();
        SourceSet main = sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
        SourceDirectorySet mainResources = main.getResources();
        TaskContainer tasks = target.getTasks();
        ObjectFactory objects = target.getObjects();

        Dependency localizer = target.getDependencies().create("org.jvnet.localizer:localizer-maven-plugin:1.31");
        Configuration localizeMessagesRuntimeClasspath = target.getConfigurations().create(CONFIGURATION_NAME, c -> {
            c.attributes(container -> container.attribute(USAGE_ATTRIBUTE, objects.named(Usage.class, JAVA_RUNTIME)));
            c.setVisible(false);
            c.setCanBeConsumed(false);
            c.setCanBeResolved(true);
            c.withDependencies(dependencies -> dependencies.add(localizer));
        });

        TaskProvider<LocalizationTask> localizeMessages = tasks.register("localizeMessages", 
                LocalizationTask.class, 
                new RegistrationActions(mainResources, localizeMessagesRuntimeClasspath));

        tasks.named(main.getCompileJavaTaskName(), JavaCompile.class)
                .configure(new IncludeAdditionalSource(localizeMessages));
    }
    
    private static class RegistrationActions implements Action<LocalizationTask> {
        private static final String DESCRIPTION = "Generates Java source files for **/Messages.properties";
        private final SourceDirectorySet source;
        private final Configuration runtimeClasspath;

        private RegistrationActions(SourceDirectorySet source, Configuration runtimeClasspath) {
            this.source = source;
            this.runtimeClasspath = runtimeClasspath;
        }

        @Override
        public void execute(LocalizationTask t) {
            t.setDescription(DESCRIPTION);
            t.setGroup(BasePlugin.BUILD_GROUP);
            t.setSource(source);
            t.getSourceRoots().set(source.getSrcDirs());
            t.getLocalizerClasspath().from(runtimeClasspath);
        }
    }
    
    private static class IncludeAdditionalSource implements Action<JavaCompile> {
        private final TaskProvider<?> provider;

        private IncludeAdditionalSource(TaskProvider<?> provider) {
            this.provider = provider;
        }

        @Override
        public void execute(JavaCompile javaCompile) {
            javaCompile.source(provider);
        }
    }
}
