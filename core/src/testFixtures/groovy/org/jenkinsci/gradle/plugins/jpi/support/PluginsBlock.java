package org.jenkinsci.gradle.plugins.jpi.support;

import java.util.LinkedList;
import java.util.List;

public class PluginsBlock extends CodeBlock {
    public static final String NAME = "plugins";

    public PluginsBlock(List<Statement> statements) {
        super(NAME, statements);
    }

    @Override
    public int filePosition() {
        return -100;
    }

    public static PluginsBlock.Builder newBuilder() {
        return new Builder();
    }

    public static class Builder extends CodeBlock.Builder {
        private final List<Statement> statements = new LinkedList<>();

        public Builder withPlugin(String id) {
            statements.add(Statement.createPlugin(id));
            return this;
        }

        public Builder withPlugin(String id, String version) {
            statements.add(Statement.createPlugin(id, version));
            return this;
        }

        @Override
        public PluginsBlock build() {
            return new PluginsBlock(statements);
        }
    }
}
