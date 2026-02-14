rootProject.name = "gradle-jpi-plugin"

val jpiMode = providers.gradleProperty("gradleJpiPlugin.mode")
    .orElse("all")
    .get()
include("core")
if (jpiMode == "all" || jpiMode == "jpi") {
    include("jpi")
}
if (jpiMode == "all" || jpiMode == "jpi2") {
    include("jpi2")
}