package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.Action;
import org.gradle.api.artifacts.Dependency;

class AddedByPlugin implements Action<Dependency> {
    public static final String REASON = "Added by org.jenkins-ci.jpi plugin";

    @Override
    public void execute(Dependency dependency) {
        dependency.because(REASON);
    }
}
