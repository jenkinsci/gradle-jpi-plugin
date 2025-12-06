package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.Project;

/**
 * @author Kohsuke Kawaguchi
 * The task now directly creates the manifest. The task also models the inputs,
 * allowing up-to-date checks to work on manifest generation.
 *
 * @see org.jenkinsci.gradle.plugins.jpi.server.GenerateHplTask
 * @deprecated To be removed in 1.0.0
 */
@Deprecated
class JpiHplManifest extends JpiManifest {
    JpiHplManifest(Project project) {
        super(project);
    }
}
