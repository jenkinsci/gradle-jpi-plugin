package org.jenkinsci.gradle.plugins.accmod

import java.lang.RuntimeException

class RestrictedApiException : RuntimeException("Restricted APIs were detected - see https://tiny.cc/jenkins-restricted") 
