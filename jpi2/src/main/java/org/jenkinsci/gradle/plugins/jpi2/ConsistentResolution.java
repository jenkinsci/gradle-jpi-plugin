package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.result.ResolvedComponentResult;
import org.gradle.api.artifacts.result.ResolvedVariantResult;
import org.gradle.api.attributes.Category;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Pins configurations to the module versions Jenkins core resolves, so that a plugin is built and
 * tested against the libraries it will actually get at runtime.
 *
 * <p>This is {@link Configuration#shouldResolveConsistentlyWith(Configuration)} minus platforms.
 * Gradle's own consistent resolution pins every node in the source graph, including artifact-less
 * platform (BOM) nodes. Third-party libraries leak BOMs into their published runtime metadata -
 * {@code jenkins-core} pulls in {@code com.github.spotbugs:spotbugs-annotations}, which drags in
 * {@code org.junit:junit-bom} - and a strict pin on such a BOM makes it impossible to upgrade any
 * module that BOM governs, because the upgraded modules bring the newer BOM along with them.
 * Skipping platform nodes costs nothing: they carry no artifacts, and every real module core
 * resolves is still pinned strictly.
 *
 * @see <a href="https://github.com/jenkinsci/gradle-jpi-plugin/issues/429">#429</a>
 */
final class ConsistentResolution {

    private ConsistentResolution() {
    }

    /**
     * Creates the configuration that carries Jenkins core's resolved versions as strict
     * constraints. Configurations that should follow core's versions extend from it.
     *
     * @param project the project to create the configuration in
     * @param source  the configuration whose resolved versions win
     * @return the alignment configuration
     */
    @NotNull
    static Configuration createAlignment(@NotNull Project project, @NotNull Configuration source) {
        var constraints = project.getDependencies().getConstraints();
        var reason = "version resolved in configuration ':" + source.getName() + "' by consistent resolution";
        var alignment = project.getConfigurations().create("jenkinsCoreAlignment");
        alignment.setCanBeConsumed(false);
        alignment.setCanBeResolved(false);
        alignment.setDescription("Strict version constraints matching what Jenkins core resolves.");
        // Deferred to resolution time: resolving `source` while declaring the alignment would force
        // every consumer to know the Jenkins version before the build script has finished configuring.
        alignment.withDependencies(new Action<>() {
            @Override
            public void execute(@NotNull DependencySet dependencies) {
                if (!alignment.getDependencyConstraints().isEmpty()) {
                    return; // withDependencies runs once per resolution of each extending configuration
                }
                source.getIncoming().getResolutionResult().getAllComponents().stream()
                        .filter(component -> component.getId() instanceof ModuleComponentIdentifier)
                        .filter(component -> !isPlatform(component))
                        .map(ResolvedComponentResult::getModuleVersion)
                        .filter(Objects::nonNull)
                        .forEach(module -> alignment.getDependencyConstraints().add(
                                constraints.create(module.getGroup() + ":" + module.getName(), constraint -> {
                                    constraint.version(version -> version.strictly(module.getVersion()));
                                    constraint.because(reason);
                                })));
            }
        });
        return alignment;
    }

    private static boolean isPlatform(@NotNull ResolvedComponentResult component) {
        return component.getVariants().stream().allMatch(ConsistentResolution::isPlatform);
    }

    private static boolean isPlatform(@NotNull ResolvedVariantResult variant) {
        var attributes = variant.getAttributes();
        // Attributes of published components come back desugared to String, so match by name.
        return attributes.keySet().stream()
                .filter(key -> Category.CATEGORY_ATTRIBUTE.getName().equals(key.getName()))
                .map(key -> String.valueOf(attributes.getAttribute(key)))
                .anyMatch(category -> Category.REGULAR_PLATFORM.equals(category)
                        || Category.ENFORCED_PLATFORM.equals(category));
    }
}
