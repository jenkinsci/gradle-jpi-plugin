package org.jenkinsci.gradle.plugins.jpi.support;

public class FourSpaceIndenter implements Indenter {
    private final int level;
    private final String indent;

    private FourSpaceIndenter(int level) {
        this.level = level;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4 * level; i++) {
            sb.append(' ');
        }
        this.indent = sb.toString();
    }

    public FourSpaceIndenter increase() {
        return new FourSpaceIndenter(level + 1);
    }

    @Override
    public String indent() {
        return indent;
    }

    public static FourSpaceIndenter create() {
        return new FourSpaceIndenter(0);
    }
}
