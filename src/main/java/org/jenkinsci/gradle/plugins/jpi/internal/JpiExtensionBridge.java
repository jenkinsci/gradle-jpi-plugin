package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.SetProperty;
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper;
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicense;

import java.net.URI;

public interface JpiExtensionBridge {
    Property<String> getPluginId();
    Property<String> getExtension();
    Property<String> getHumanReadableName();
    Property<URI> getHomePage();
    Provider<String> getJenkinsCoreVersion();
    Property<String> getMinimumJenkinsCoreVersion();
    Property<Boolean> getSandboxed();
    Property<Boolean> getUsePluginFirstClassLoader();
    SetProperty<String> getMaskedClassesFromCore();
    ListProperty<PluginDeveloper> getPluginDevelopers();
    ListProperty<PluginLicense> getPluginLicenses();
    ListProperty<String> getTestJvmArguments();

    Property<Boolean> getGenerateTests();
    Property<String> getGeneratedTestClassName();
    Property<Boolean> getRequireEscapeByDefaultInJelly();
    
    Property<String> getScmTag();
    Property<URI> getGitHub();
}
