package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.Project;

import java.util.HashMap;
import java.util.Map;
import java.util.jar.Attributes;

/**
 * Encapsulates the Jenkins plugin manifest and its generation.
 * This manifest is now created by the generateJenkinsManifest task.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated To be removed in 1.0.0
 * @see org.jenkinsci.gradle.plugins.manifest.GenerateJenkinsManifestTask
 */
@Deprecated
public class JpiManifest {
    JpiManifest(Project project) {
    }

    static Map<String, ?> attributesToMap(Attributes attributes) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<Object, Object> entry : attributes.entrySet()) {
            map.put(entry.getKey().toString(), entry.getValue());
        }
        return map;
    }
}
