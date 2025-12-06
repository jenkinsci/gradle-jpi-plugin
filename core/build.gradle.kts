plugins {
    groovy
    `java-test-fixtures`
}

description = "Test fixtures for Jenkins Gradle plugins"

dependencies {
    // Test fixtures dependencies - shared test infrastructure
    testFixturesImplementation(gradleApi())
    testFixturesImplementation(gradleTestKit())
    testFixturesImplementation("org.spockframework:spock-core:2.1-groovy-3.0")
    testFixturesImplementation(libs.commons.text)
    testFixturesImplementation(libs.junit4)
    testFixturesImplementation(libs.javapoet)
}
