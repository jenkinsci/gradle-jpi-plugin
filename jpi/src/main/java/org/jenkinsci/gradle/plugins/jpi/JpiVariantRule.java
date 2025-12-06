package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.Action;
import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataDetails;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.DirectDependenciesMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVariantFilesMetadata;
import org.gradle.api.artifacts.VariantMetadata;
import org.gradle.api.artifacts.ivy.IvyModuleDescriptor;
import org.gradle.api.artifacts.maven.PomModuleDescriptor;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.attributes.Bundling;
import org.gradle.api.attributes.Category;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.Usage;
import org.gradle.api.model.ObjectFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import static org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE;

@CacheableRule
public abstract class JpiVariantRule implements ComponentMetadataRule {
    private static final Logger LOGGER = LoggerFactory.getLogger(JpiVariantRule.class);
    public static final Attribute<Boolean> EMPTY_VARIANT = Attribute.of("empty-jpi", Boolean.class);
    private static final Attribute<String> DESUGARED_LIBRARY_ELEMENTS_ATTRIBUTE = Attribute.of(
            LIBRARY_ELEMENTS_ATTRIBUTE.getName(), 
            String.class);

    @Inject
    public abstract ObjectFactory getObjects();

    @Override
    public void execute(ComponentMetadataContext ctx) {
        ComponentMetadataDetails details = ctx.getDetails();
        ModuleVersionIdentifier id = details.getId();
        if ("org.jenkins-ci.main:jenkins-war".equals(id.getModule().toString())) {
            skip(id, "only resolved standalone");
            return;
        }
        if (isMissingMetadata(ctx)) {
            skip(id, "missing metadata");
            return;
        }
        if (isIvyResolvedDependency(ctx)) {
            skip(id, "ivy metadata");
            return;
        }
        if (isJenkinsPackaging(ctx)) {
            details.withVariant("runtime", new Action<VariantMetadata>() {
                @Override
                public void execute(VariantMetadata v) {
                    v.attributes(new Action<AttributeContainer>() {
                        @Override
                        public void execute(AttributeContainer c) {
                            c.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.class, "jpi"));
                        }
                    });
                    v.withDependencies(new Action<DirectDependenciesMetadata>() {
                        @Override
                        public void execute(DirectDependenciesMetadata d) {
                            d.removeIf(new Predicate<DirectDependencyMetadata>() {
                                @Override
                                public boolean test(DirectDependencyMetadata m) {
                                    return m.getArtifactSelectors().stream().anyMatch(new Predicate<DependencyArtifact>() {
                                        @Override
                                        public boolean test(DependencyArtifact art) {
                                            return art.getClassifier().isEmpty();
                                        }
                                    });
                                }
                            });
                        }
                    });
                }
            });
            details.withVariant("compile", new Action<VariantMetadata>() {
                @Override
                public void execute(VariantMetadata v) {
                    v.withFiles(new Action<MutableVariantFilesMetadata>() {
                        @Override
                        public void execute(MutableVariantFilesMetadata f) {
                            f.removeAllFiles();
                            f.addFile(id.getName() + "-" + id.getVersion() + ".jar");
                        }
                    });
                }
            });
            details.addVariant("jarRuntimeElements", "runtime", new Action<VariantMetadata>() {
                @Override
                public void execute(VariantMetadata v) {
                    v.attributes(new Action<AttributeContainer>() {
                        @Override
                        public void execute(AttributeContainer c) {
                            c.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.class, LibraryElements.JAR));
                        }
                    });
                    v.withFiles(new Action<MutableVariantFilesMetadata>() {
                        @Override
                        public void execute(MutableVariantFilesMetadata f) {
                            f.removeAllFiles();
                            f.addFile(id.getName() + "-" + id.getVersion() + ".jar");
                        }
                    });
                }
            });
        } else if (!hasJpiVariant(ctx)) {
            addEmptyJpiVariant(ctx);
        }
    }

    /**
     * Add a variant that we can match if a JPI variant depends on something that
     * does not have a JPI variant. This is the case for all POM-based JPI modules
     * that declare dependencies to JAR modules, because the metadata does not
     * have sufficient separation of JAR/JPI variants.
     */
    private void addEmptyJpiVariant(ComponentMetadataContext ctx) {
        ctx.getDetails().addVariant("jpiEmpty", new Action<VariantMetadata>() {
            @Override
            public void execute(VariantMetadata v) {
                v.attributes(new Action<AttributeContainer>() {
                    @Override
                    public void execute(AttributeContainer c) {
                        c.attribute(EMPTY_VARIANT, Boolean.TRUE);
                        c.attribute(Usage.USAGE_ATTRIBUTE, getObjects().named(Usage.class, Usage.JAVA_RUNTIME));
                        c.attribute(Category.CATEGORY_ATTRIBUTE, getObjects().named(Category.class, Category.LIBRARY));
                        c.attribute(Bundling.BUNDLING_ATTRIBUTE, getObjects().named(Bundling.class, Bundling.EXTERNAL));
                        c.attribute(LIBRARY_ELEMENTS_ATTRIBUTE, getObjects().named(LibraryElements.class, "jpi"));
                    }
                });
            }
        });
        // This variant might still resolve to a jar file - https://github.com/gradle/gradle/issues/11974
    }

    private boolean isMissingMetadata(ComponentMetadataContext ctx) {
        return getMetadata(ctx) == null;
    }

    private boolean isIvyResolvedDependency(ComponentMetadataContext ctx) {
        return ctx.getDescriptor(IvyModuleDescriptor.class) != null;
    }

    private boolean isJenkinsPackaging(ComponentMetadataContext ctx) {
        PomModuleDescriptor pom = ctx.getDescriptor(PomModuleDescriptor.class);
        if (pom == null) {
            return false;
        }
        String packaging = pom.getPackaging();
        return "jpi".equals(packaging) || "hpi".equals(packaging);
    }

    private boolean hasJpiVariant(ComponentMetadataContext ctx) {
        // TODO this needs public API - https://github.com/gradle/gradle/issues/12349
        Object metadata = getMetadata(ctx);
        try {
            Method getVariants = metadata.getClass().getMethod("getVariants");
            List<?> variants = (List<?>) getVariants.invoke(metadata);
            Method getAttributes = null;
            if (!variants.isEmpty()) {
                getAttributes = variants.get(0).getClass().getDeclaredMethod("getAttributes");
            }
            for (Object variant : variants) {
                AttributeContainer attributes = (AttributeContainer) getAttributes.invoke(variant);
                if ("jpi".equals(attributes.getAttribute(DESUGARED_LIBRARY_ELEMENTS_ATTRIBUTE))) {
                    return true;
                }
            }
            return false;
        } catch (NoSuchMethodException e) {
            LOGGER.error("`getVariants` method does not exist on `metadata` ({}) of ComponentMetadataContext",
                    metadata.getClass().getName(), e);
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            LOGGER.error("Failed to invoke `getVariants` method on `metadata` of ComponentMetadataContext", e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            LOGGER.error("`getVariants` method on `metadata` of ComponentMetadataContext is not accessible", e);
            throw new RuntimeException(e);
        }
    }

    private static void skip(ModuleVersionIdentifier id, String reason) {
        LOGGER.debug("Skipping {} due to {}", id, reason);
    }
    
    private static Object getMetadata(ComponentMetadataContext ctx) {
        try {
            Field metadata = ctx.getClass().getDeclaredField("metadata");
            metadata.setAccessible(true);
            return metadata.get(ctx);
        } catch (NoSuchFieldException e) {
            LOGGER.error("`metadata` field does not exist on ComponentMetadataContext", e);
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            LOGGER.error("Attempt to make `metadata` field on ComponentMetadataContext accessible failed", e);
            throw new RuntimeException(e);
        }
    }
}
