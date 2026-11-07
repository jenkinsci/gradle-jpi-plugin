package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.Transformer;
import org.gradle.api.tasks.TaskProvider;

/**
 * Associates a task provider with the rename to apply when syncing its output.
 */
class TaskWithRename {
    private final TaskProvider<?> taskProvider;
    private final Transformer<String, String> renameTransformer;

    TaskWithRename(TaskProvider<?> taskProvider, Transformer<String, String> renameTransformer) {
        this.taskProvider = taskProvider;
        this.renameTransformer = renameTransformer;
    }

    TaskProvider<?> getTaskProvider() {
        return taskProvider;
    }

    Transformer<String, String> getRenameTransformer() {
        return renameTransformer;
    }
}
