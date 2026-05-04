package org.jenkinsci.gradle.plugins.jpi2.accmod;

/** Thrown when the access-modifier checker finds usages of {@code @Restricted} APIs. */
public class RestrictedApiException extends RuntimeException {
    /** Constructs the exception with a standard message pointing to Jenkins' restricted-API documentation. */
    public RestrictedApiException() {
        super("Restricted APIs were detected - see https://tiny.cc/jenkins-restricted");
    }
}
