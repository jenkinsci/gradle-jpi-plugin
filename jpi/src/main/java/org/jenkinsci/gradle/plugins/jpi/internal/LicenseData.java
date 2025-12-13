package org.jenkinsci.gradle.plugins.jpi.internal;

import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Contains license data extracted from a POM file.
 */
public class LicenseData {
    private final String name;
    private final String description;
    private final String url;
    private final Set<License> licenses;

    /**
     * Creates new license data.
     *
     * @param name the project name
     * @param description the project description
     * @param url the project URL
     * @param licenses the set of licenses
     */
    public LicenseData(String name, String description, String url, Set<License> licenses) {
        this.name = name;
        this.description = description;
        this.url = url;
        this.licenses = licenses;
    }

    /** @return the project name */
    public String getName() {
        return name;
    }

    /** @return the project description */
    public String getDescription() {
        return description;
    }

    /** @return the project URL */
    public String getUrl() {
        return url;
    }

    /** @return the set of licenses */
    public Set<License> getLicenses() {
        return licenses;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LicenseData that = (LicenseData) o;
        return Objects.equals(name, that.name) && Objects.equals(description, that.description) && Objects.equals(url, that.url) && Objects.equals(licenses, that.licenses);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, url, licenses);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", LicenseData.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("description='" + description + "'")
                .add("url='" + url + "'")
                .add("licenses=" + licenses)
                .toString();
    }
}
