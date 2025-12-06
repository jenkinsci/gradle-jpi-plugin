package org.jenkinsci.gradle.plugins.jpi.support;

public interface Indenter {
    Indenter increase();
    String indent();
}
