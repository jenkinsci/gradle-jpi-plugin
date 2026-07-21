plugins {
    id("com.gradle.develocity").version("4.5.0")
}

val isDevelocityConfigEnabled =
    providers
        .gradleProperty("develocity.config.enabled")
        .map { it.toBoolean() }
        .orElse(true)

if (isDevelocityConfigEnabled.get()) {
    develocity {
        buildScan {
            termsOfUseUrl.set("https://gradle.com/terms-of-service")
            termsOfUseAgree.set("yes")
        }
    }
}

rootProject.name = "gradle-jpi-plugin"

val jpiMode = providers.gradleProperty("gradleJpiPlugin.mode")
    .orElse("all")
    .get()
include("core")
if (jpiMode == "all" || jpiMode == "jpi2") {
    include("jpi2")
}