package org.jenkinsci.gradle.plugins.jpi.internal

import groovy.transform.CompileDynamic
import org.codehaus.groovy.runtime.GeneratedClosure
import org.gradle.internal.metaobject.ConfigureDelegate
import org.gradle.util.Configurable
import org.gradle.util.internal.ClosureBackedAction
import javax.annotation.Nullable

@CompileDynamic
class ConfigureUtil {
    static <T> T configure(@Nullable Closure configureClosure, T target) {
        if (configureClosure == null) {
            target
        } else {
            if (target instanceof Configurable) {
                ((Configurable)target).configure(configureClosure)
            } else {
                configureTarget(configureClosure, target, new ConfigureDelegate(configureClosure, target))
            }

            target
        }
    }

    private static <T> void configureTarget(Closure configureClosure, T target, ConfigureDelegate closureDelegate) {
        if (configureClosure instanceof GeneratedClosure) {
            Closure withNewOwner = configureClosure.rehydrate(target, closureDelegate, configureClosure.thisObject)
            (new ClosureBackedAction(withNewOwner, 2, false)).execute(target)
        } else {
            (new ClosureBackedAction(configureClosure, 1, false)).execute(target)
        }
    }
}
