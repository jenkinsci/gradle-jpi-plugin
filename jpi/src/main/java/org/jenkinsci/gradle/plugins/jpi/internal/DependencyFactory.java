package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

interface DependencyFactory {
    Dependency create(DependencyHandler handler);
}
