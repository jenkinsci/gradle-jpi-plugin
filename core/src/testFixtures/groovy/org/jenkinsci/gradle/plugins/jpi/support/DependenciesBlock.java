package org.jenkinsci.gradle.plugins.jpi.support;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class DependenciesBlock extends CodeBlock {
    public static final String NAME = "dependencies";

    public DependenciesBlock(List<Statement> statements) {
        super(NAME, statements);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder(statements);
    }

    static class Builder extends CodeBlock.Builder {
        private List<Statement> statements;

        public Builder() {
            this(new LinkedList<>());
        }

        public Builder(List<Statement> statements) {
            this.statements = statements;
        }

        public Builder add(String configuration, String notation) {
            statements.add(Statement.createDependency(configuration, notation));
            return this;
        }

        public Builder addAllTo(String configuration, Collection<String> notations) {
            notations.stream()
                    .map(n -> Statement.createDependency(configuration, n))
                    .forEach(statements::add);
            return this;
        }

        public Builder addImplementation(String notation) {
            return add("implementation", notation);
        }

        public Builder addAllToImplementation(Collection<String> notations) {
            return addAllTo("implementation", notations);
        }

        public Builder reset() {
            statements.clear();
            return this;
        }

        @Override
        public DependenciesBlock build() {
            return new DependenciesBlock(statements);
        }
    }
}
