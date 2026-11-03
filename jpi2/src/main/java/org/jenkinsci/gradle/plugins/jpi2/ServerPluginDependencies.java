package org.jenkinsci.gradle.plugins.jpi2;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Sync;

/** Wires server plugin archives without resolving them during task configuration. */
final class ServerPluginDependencies {
    private ServerPluginDependencies() {}

    static void configure(Sync sync, Configuration configuration, Provider<String> targetExtension) {
        var project = sync.getProject();
        // Older configuration-cache codecs cannot restore providers holding resolved-artifact objects.
        var files = project.files(project.provider(() -> pluginArtifacts(configuration).stream()
                .map(ResolvedArtifact::getFile)
                .collect(Collectors.toList())));
        // Plain artifact files lose the producer tasks of project dependencies.
        files.builtBy(configuration);
        var names = project.provider(() -> pluginArtifacts(configuration).stream()
                .collect(Collectors.toMap(
                        artifact -> withoutExtension(artifact.getFile().getName()),
                        artifact -> withoutExtension(new DropVersionTransformer(
                                        artifact.getName(),
                                        artifact.getModuleVersion().getId().getVersion(),
                                        "jpi")
                                .transform(artifact.getFile().getName())))));
        sync.from(files, spec -> spec.rename(new PluginRenamer(names, targetExtension)));
    }

    private static List<ResolvedArtifact> pluginArtifacts(Configuration configuration) {
        return configuration.getResolvedConfiguration().getResolvedArtifacts().stream()
                .filter(artifact -> HpiMetadataRule.PLUGIN_PACKAGINGS.contains(artifact.getExtension()))
                .sorted(Comparator.comparing(ResolvedArtifact::getName))
                .toList();
    }

    private static String withoutExtension(String name) {
        return name.substring(0, name.lastIndexOf('.'));
    }

    // Earlier Gradle versions cannot invoke a private record constructor when restoring this action.
    private static final class PluginRenamer implements Transformer<String, String> {
        private final Provider<Map<String, String>> names;
        private final Provider<String> targetExtension;

        private PluginRenamer(Provider<Map<String, String>> names, Provider<String> targetExtension) {
            this.names = names;
            this.targetExtension = targetExtension;
        }

        @Override
        public String transform(String name) {
            // Parent copy specs may already have normalized extensions or project dependency names.
            var baseName = withoutExtension(name);
            return names.get().getOrDefault(baseName, baseName) + "." + targetExtension.get();
        }
    }
}
