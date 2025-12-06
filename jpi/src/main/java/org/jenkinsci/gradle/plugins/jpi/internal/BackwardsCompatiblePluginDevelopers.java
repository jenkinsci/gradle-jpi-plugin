package org.jenkinsci.gradle.plugins.jpi.internal;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloper;
import org.jenkinsci.gradle.plugins.jpi.core.PluginDeveloperSpec;

import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of PluginDeveloperSpec that provides backward compatibility
 * for configuring plugin developers.
 * <p>
 * This class manages a list of plugin developers and provides methods for setting
 * developer properties in a way that's compatible with older Gradle versions.
 */
public class BackwardsCompatiblePluginDevelopers implements PluginDeveloperSpec {
    private final List<PluginDeveloper> developers = new LinkedList<>();
    private final ObjectFactory objects;
    private PluginDeveloper current;

    /**
     * Constructs a new instance with the given object factory.
     *
     * @param objects The object factory used to create new PluginDeveloper instances
     */
    public BackwardsCompatiblePluginDevelopers(ObjectFactory objects) {
        this.objects = objects;
    }

    @Override
    public void developer(Action<? super PluginDeveloper> action) {
        current = objects.newInstance(PluginDeveloper.class);
        action.execute(current);
        developers.add(current);
        current = null;
    }

    /**
     * Gets the list of configured developers.
     *
     * @return The list of plugin developers
     */
    public List<PluginDeveloper> getDevelopers() {
        return developers;
    }

    /**
     * Sets the ID of the current developer.
     * This method is used to support nested closures in Gradle build scripts.
     *
     * @param s The developer ID
     */
    // this is because of nested closures?
    void setId(String s) {
        current.getId().set(s);
    }

    void id(String s) {
        setId(s);
    }

    void setName(String s) {
        current.getName().set(s);
    }

    void name(String s) {
        setName(s);
    }

    void setEmail(String s) {
        current.getEmail().set(s);
    }

    void email(String s) {
        setEmail(s);
    }
}
