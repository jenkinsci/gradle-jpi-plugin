package org.jenkinsci.gradle.plugins.jpi2.accmod;

public class RestrictedApiException extends RuntimeException {
    public RestrictedApiException() {
        super("Restricted APIs were detected - see https://tiny.cc/jenkins-restricted");
    }
}
