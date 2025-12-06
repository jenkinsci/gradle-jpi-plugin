package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.util.Objects;

class MavenDependency implements DependencyFactory {
    private final String notation;

    MavenDependency(String notation) {
        this.notation = notation;
    }

    @Override
    public Dependency create(DependencyHandler handler) {
        Dependency dependency = handler.create(notation);
        dependency.because(AddedByPlugin.REASON);
        return dependency;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MavenDependency that = (MavenDependency) o;
        return Objects.equals(notation, that.notation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notation);
    }

    @Override
    public String toString() {
        return "'" + notation + "'";
    }
}
