package org.jenkinsci.gradle.plugins.jpi.support;

import java.util.LinkedList;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.stream.Collectors;

public class ProjectFile implements Emitable {
    private final String name;
    private final List<CodeBlock> blocks;
    private final List<Statement> statements;

    public ProjectFile(String name, List<CodeBlock> blocks, List<Statement> statements) {
        this.name = name;
        this.blocks = blocks;
        this.statements = statements;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    @Override
    public String emit(Indenter indenter) {
        List<String> chunks = new LinkedList<>();
        blocks.stream()
                .filter(b -> b.filePosition() < 0)
                .map(b -> b.emit(indenter))
                .reduce((a, b) -> String.join("\n", a, b))
                .ifPresent(chunks::add);

        statements.stream()
                .map(s -> s.emit(indenter) + '\n')
                .reduce((a, b) -> String.join("", a, b))
                .ifPresent(chunks::add);

        blocks.stream()
                .filter(b -> b.filePosition() >= 0)
                .map(b -> b.emit(indenter))
                .reduce((s, s2) -> String.join("\n", s, s2))
                .ifPresent(chunks::add);
        return String.join("\n", chunks);
    }

    public static class Builder {
        private String name;
        private final List<CodeBlock> blocks = new LinkedList<>();
        private final List<Statement> statements = new LinkedList<>();

        public ProjectFile build() {
            return new ProjectFile(name, new LinkedList<>(blocks), new LinkedList<>(statements));
        }

        public Builder withPlugins(PluginsBlock block) {
            return withBlock(block);
        }

        public Builder withDependencies(DependenciesBlock block) {
            return withBlock(block);
        }

        public Builder withBlock(CodeBlock block) {
            blocks.add(block);
            return this;
        }

        public Builder withName(String name) {
            this.name = name;
            return this;
        }

        public Builder clearDependencies() {
            blocks.removeIf(block -> block instanceof DependenciesBlock);
            return this;
        }

        public Builder setStatement(String template, Object... args) {
            statements.removeIf(s -> s.getTemplate().equals(template));
            statements.add(Statement.create(template, args));
            return this;
        }
    }
}
