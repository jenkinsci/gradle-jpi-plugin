package org.jenkinsci.gradle.plugins.jpi.internal;

import java.util.Objects;
import java.util.StringJoiner;

/**
 * Represents a software license with a name and URL.
 */
public class License {
    private final String name;
    private final String url;

    /**
     * Creates a new license.
     *
     * @param name the license name
     * @param url the URL to the license text
     */
    public License(String name, String url) {
        this.name = name;
        this.url = url;
    }

    /** @return the license name */
    public String getName() {
        return name;
    }

    /** @return the URL to the license text */
    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        License license = (License) o;
        return Objects.equals(name, license.name) && Objects.equals(url, license.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, url);
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", License.class.getSimpleName() + "[", "]")
                .add("name='" + name + "'")
                .add("url='" + url + "'")
                .toString();
    }
}
