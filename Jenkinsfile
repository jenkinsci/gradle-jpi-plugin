#!/usr/bin/env groovy

def platforms = ['linux', 'windows']
def jdkVersions = [8]
Map tasks = [:]
for (int i = 0; i < platforms.size(); ++i) {
    for (int j = 0; j < jdkVersions.size(); ++j) {
        String label = platforms[i]
        String jdk = jdkVersions[j]
        String stageIdentifier = "${label}-${jdk}"

        tasks[stageIdentifier] = {
            node(label) {
                stage("Checkout (${stageIdentifier})") {
                    timestamps {
                        checkout scm
                    }
                }

                stage("Build (${stageIdentifier})") {
                    String jdkTool = "jdk${jdk}"
                    List<String> env = [
                            "JAVA_HOME=${tool jdkTool}",
                            'PATH+JAVA=${JAVA_HOME}/bin',
                    ]
                    String command = "gradlew clean build"

                    withEnv(env) {
                        retry(3) {
                            if (isUnix()) {
                                timestamps {
                                    sh "./" + command
                                }
                            } else {
                                timestamps {
                                    bat command
                                }
                            }
                        }
                    }
                }

                stage("Archive (${stageIdentifier})") {
                    timestamps {
                        junit 'build/test-results/**/*.xml'
                        archiveArtifacts artifacts: 'build/libs/**,build/reports/**', fingerprint: true
                    }
                }
            }
        }
    }
}

timeout(60) {
    parallel(tasks)
}
