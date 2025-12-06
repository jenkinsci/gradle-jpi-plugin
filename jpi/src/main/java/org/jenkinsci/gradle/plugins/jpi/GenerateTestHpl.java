package org.jenkinsci.gradle.plugins.jpi;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @see org.jenkinsci.gradle.plugins.jpi.server.GenerateHplTask
 * @deprecated To be removed in 1.0.0
 */
@Deprecated
public abstract class GenerateTestHpl extends DefaultTask {
    public static final String TASK_NAME = "generate-test-hpl";
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateTestHpl.class);
    
    @OutputDirectory
    public abstract DirectoryProperty getHplDir();
    
    @TaskAction
    public void generateTestHpl() {
        LOGGER.warn("{} has been replaced by generateTestHpl - please remove references to {}", TASK_NAME, TASK_NAME);
    }
}
