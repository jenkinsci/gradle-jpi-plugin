package org.jenkinsci.gradle.plugins.jpi.server;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.CopySpec;
import org.gradle.api.file.Directory;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;

import javax.inject.Inject;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.jenkinsci.gradle.plugins.jpi.core.ArchiveExtensions.allExtensions;
import static org.jenkinsci.gradle.plugins.jpi.core.ArchiveExtensions.allPathPatterns;
import static org.jenkinsci.gradle.plugins.jpi.core.ArchiveExtensions.nameWithJpi;

public abstract class InstallJenkinsServerPluginsTask extends DefaultTask {
    public static final String TASK_NAME = "installJenkinsServerPlugins";
    private static final String PLUGINS_DIR = "plugins";

    @Internal
    public abstract Property<Configuration> getPluginsConfiguration();

    @InputFile
    public abstract RegularFileProperty getHpl();

    @Input
    public abstract Property<File> getJenkinsHome();

    @Input
    public abstract SetProperty<String> getPluginExtensions();

    @Classpath
    public FileCollection getPlugins() {
        return getPluginsConfiguration().map(new Transformer<FileCollection, Configuration>() {
            @Override
            public FileCollection transform(Configuration configuration) {
                return configuration.filter(new Spec<File>() {
                    @Override
                    public boolean isSatisfiedBy(File element) {
                        for (String extension : allExtensions()) {
                            if (element.getName().endsWith(extension)) {
                                return true;
                            }
                        }
                        return false;
                    }
                });
            }
        }).get();
    }

    @Internal
    public Provider<Map<String, String>> getLookup() {
        return getPluginsConfiguration().map(new Transformer<Map<String, String>, Configuration>() {
            @Override
            public Map<String, String> transform(Configuration configuration) {
                Map<String, String> map = new HashMap<>();
                ResolvedConfiguration resolved = configuration.getResolvedConfiguration();
                Set<ResolvedArtifact> artifacts = resolved.getResolvedArtifacts();
                for (ResolvedArtifact artifact : artifacts) {
                    if (allExtensions().contains(artifact.getExtension())) {
                        File file = artifact.getFile();
                        String withVersion = file.getName();
                        String withoutVersion = nameWithJpi(artifact);
                        map.put(withVersion, withoutVersion);
                    }
                }
                return map;
            }
        });
    }

    @OutputDirectory
    public Provider<Directory> getPluginsDir() {
        return getJenkinsHome().map(new Transformer<Directory, File>() {
            @Override
            public Directory transform(File jenkinsHome) {
                File pluginsDir = new File(jenkinsHome, PLUGINS_DIR);
                return getProjectLayout().getProjectDirectory().dir(pluginsDir.getPath());
            }
        });
    }

    @Inject
    public abstract FileSystemOperations getFileSystemOperations();

    @Inject
    public abstract ProjectLayout getProjectLayout();

    @TaskAction
    public void run() {
        Map<String, String> lookup = getLookup().get();
        getFileSystemOperations().sync(new Action<CopySpec>() {
            @Override
            public void execute(CopySpec s) {
                s.into(getPluginsDir());
                s.from(getPluginsConfiguration(), new Action<CopySpec>() {
                    @Override
                    public void execute(CopySpec p) {
                        p.include(allPathPatterns());
                        p.rename(new Transformer<String, String>() {
                            @Override
                            public String transform(String filename) {
                                return lookup.get(filename);
                            }
                        });
                    }
                });
                s.from(getHpl());
            }
        });
    }
}
