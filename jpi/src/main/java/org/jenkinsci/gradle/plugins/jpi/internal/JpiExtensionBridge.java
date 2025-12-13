package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper;
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicense;

import java.net.URI;

/**
 * Bridge interface for accessing JPI extension properties.
 */
public interface JpiExtensionBridge {
    /** @return the plugin ID */
    Property<String> getPluginId();
    /** @return the file extension for the plugin archive */
    Property<String> getExtension();
    /** @return the human-readable name of the plugin */
    Property<String> getHumanReadableName();
    /** @return the home page URL of the plugin */
    Property<URI> getHomePage();
    /** @return the Jenkins core version */
    Provider<String> getJenkinsCoreVersion();
    /** @return the minimum required Jenkins core version */
    Property<String> getMinimumJenkinsCoreVersion();
    /** @return whether the plugin is sandboxed */
    Property<Boolean> getSandboxed();
    /** @return whether to use plugin-first class loader */
    Property<Boolean> getUsePluginFirstClassLoader();
    /** @return the set of classes masked from the core */
    SetProperty<String> getMaskedClassesFromCore();
    /** @return the list of plugin developers */
    ListProperty<PluginDeveloper> getPluginDevelopers();
    /** @return the list of plugin licenses */
    ListProperty<PluginLicense> getPluginLicenses();
    /** @return the JVM arguments for tests */
    ListProperty<String> getTestJvmArguments();

    /** @return whether to generate tests */
    Property<Boolean> getGenerateTests();
    /** @return the generated test class name */
    Property<String> getGeneratedTestClassName();
    /** @return whether to require escape by default in Jelly */
    Property<Boolean> getRequireEscapeByDefaultInJelly();

    /** @return the SCM tag */
    Property<String> getScmTag();
    /** @return the GitHub URL */
    Property<URI> getGitHub();
}
