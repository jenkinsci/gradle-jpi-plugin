package org.jenkinsci.gradle.plugins.jpi.support;

import java.util.*;

public class SettingsFile implements Emitable {
    private final List<Statement> statements;

    public SettingsFile(List<Statement> statements) {
        this.statements = statements;
    }

    @Override
    public String emit(Indenter indenter) {
        StringBuilder sb = new StringBuilder();
        for (Statement statement : statements) {
            sb.append(indenter.indent()).append(statement.emit(indenter)).append('\n');
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private final Set<String> includes = new HashSet<>();

        public Builder withRootProjectName(String name) {
            this.name = name;
            return this;
        }

        public SettingsFile build() {
            List<Statement> statements = new LinkedList<>();
            if (name != null) {
                statements.add(Statement.create("rootProject.name = $S", name));
            }
            for (String include : includes) {
                statements.add(Statement.create("include $S", include));
            }
            return new SettingsFile(statements);
        }

        public Builder addSubprojects(Collection<String> projectNames) {
            includes.addAll(projectNames);
            return this;
        }
    }
}
