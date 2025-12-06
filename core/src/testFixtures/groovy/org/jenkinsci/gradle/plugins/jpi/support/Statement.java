package org.jenkinsci.gradle.plugins.jpi.support;

import java.util.Collections;
import java.util.LinkedList;
import java.util.Objects;
import java.util.Queue;

class Statement implements Emitable {
    private final String template;
    private final String rendered;

    private Statement(String template, String rendered) {
        this.template = template;
        this.rendered = rendered;
    }

    public String getTemplate() {
        return template;
    }

    public static Statement create(String template, Object... args) {
        Queue<Object> q = new LinkedList<>();
        Collections.addAll(q, args);
        StringBuilder replaced = new StringBuilder();
        boolean makingReplacement = false;
        for (char c : template.toCharArray()) {
            if (makingReplacement) {
                makingReplacement = false;
                switch (c) {
                    case 'L':
                        replaced.append(q.poll());
                        break;
                    case 'S':
                        replaced.append("'").append(q.poll()).append("'");
                        break;
                    default:
                        throw new IllegalArgumentException("Cannot replace $" + c);
                }
            } else if (c != '$') {
                replaced.append(c);
            } else {
                makingReplacement = true;
            }
        }
        return new Statement(template, replaced.toString());
    }

    static Statement createPlugin(String id) {
        return create("id $S", id);
    }

    static Statement createPlugin(String id, String version) {
        return create("id $S version $S", id, version);
    }

    static Statement createDependency(String configuration, String notation) {
        return create("$L $S", configuration, notation);
    }

    @Override
    public String toString() {
        return rendered;
    }

    @Override
    public String emit(Indenter indenter) {
        return indenter.indent() + rendered;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Statement statement = (Statement) o;
        return Objects.equals(template, statement.template) &&
                Objects.equals(rendered, statement.rendered);
    }

    @Override
    public int hashCode() {
        return Objects.hash(template, rendered);
    }
}
