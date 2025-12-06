package org.jenkinsci.gradle.plugins.jpi.support;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class CodeBlock implements Comparable<CodeBlock>, Emitable {
    private final String name;
    protected final List<Statement> statements;

    public CodeBlock(String name, List<Statement> statements) {
        this.name = name;
        this.statements = statements;
    }

    public static Builder newBuilder(String name) {
        return new Builder(name);
    }

    public String getName() {
        return name;
    }

    public int filePosition() {
        return 0;
    }

    @Override
    public String emit(Indenter indenter) {
        StringBuilder sb = new StringBuilder(indenter.indent()).append(name).append(" {\n");
        Indenter increased = indenter.increase();
        for (Statement statement : statements) {
            sb.append(statement.emit(increased)).append("\n");
        }
        return sb.append(indenter.indent()).append("}\n").toString();
    }

    public Builder toBuilder() {
        Builder b = new Builder(name);
        b.statements.addAll(statements);
        return b;
    }

    @Override
    public int compareTo(CodeBlock o) {
        return Comparator.comparing(CodeBlock::filePosition)
                .compare(this, o);
    }

    public static class Builder {
        private String name;
        private final List<Statement> statements = new LinkedList<>();

        public Builder() {
        }

        public Builder(String name) {
            this.name = name;
        }

        public CodeBlock build() {
            return new CodeBlock(name, statements);
        }

        public Builder addStatement(String statement, Object... args) {
            return addStatement(Statement.create(statement, args));
        }

        public Builder addStatement(Statement statement) {
            statements.add(statement);
            return this;
        }

        public Builder setStatement(String template, Object... args) {
            statements.removeIf(s -> s.getTemplate().equals(template));
            statements.add(Statement.create(template, args));
            return this;
        }
    }
}
