package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;

import java.util.Objects;

class BomDependency implements DependencyFactory {
    private final String notation;

    BomDependency(String notation) {
        this.notation = notation;
    }

    @Override
    public Dependency create(DependencyHandler handler) {
        return handler.platform(notation, new AddedByPlugin());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BomDependency that = (BomDependency) o;
        return Objects.equals(notation, that.notation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(notation);
    }

    @Override
    public String toString() {
        return "platform('" + notation + "')";
    }
}
