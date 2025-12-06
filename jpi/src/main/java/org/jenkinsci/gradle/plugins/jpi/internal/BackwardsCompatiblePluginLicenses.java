package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper;
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicense;
import org.jenkinsci.gradle.plugins.jpi.core.PluginLicenseSpec;

import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of PluginLicenseSpec that provides backward compatibility
 * for configuring plugin licenses.
 * <p>
 * This class manages a list of plugin licenses and provides methods for setting
 * license properties in a way that's compatible with older Gradle versions.
 */
public class BackwardsCompatiblePluginLicenses implements PluginLicenseSpec {
    private final List<PluginLicense> licenses = new LinkedList<>();
    private final ObjectFactory objects;
    private PluginLicense current;

    /**
     * Constructs a new instance with the given object factory.
     *
     * @param objects The object factory used to create new PluginLicense instances
     */
    public BackwardsCompatiblePluginLicenses(ObjectFactory objects) {
        this.objects = objects;
    }

    @Override
    public void license(Action<? super PluginLicense> action) {
        current = objects.newInstance(PluginLicense.class);
        action.execute(current);
        licenses.add(current);
        current = null;
    }

    /**
     * Gets the list of configured licenses.
     *
     * @return The list of plugin licenses
     */
    public List<PluginLicense> getLicenses() {
        return licenses;
    }

    /**
     * Sets the name of the current license.
     *
     * @param s The license name
     */
    void setName(String s) {
        current.getName().set(s);
    }

    void name(String s) {
        setName(s);
    }

    void setUrl(String s) {
        current.getUrl().set(s);
    }

    void url(String s) {
        setUrl(s);
    }

    void setDistribution(String s) {
        current.getDistribution().set(s);
    }

    void distribution(String s) {
        setDistribution(s);
    }

    void setComments(String s) {
        current.getComments().set(s);
    }

    void comments(String s) {
        setComments(s);
    }
}
