plugins {
    id("com.gradle.develocity").version("3.19.2")
}

develocity {
    buildScan {
        termsOfUseUrl.set("https://gradle.com/terms-of-service")
        termsOfUseAgree.set("yes")
    }
}

rootProject.name = "gradle-jpi-plugin"

