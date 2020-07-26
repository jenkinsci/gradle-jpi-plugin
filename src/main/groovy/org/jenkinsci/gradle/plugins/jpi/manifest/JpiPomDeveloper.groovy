package org.jenkinsci.gradle.plugins.jpi.manifest

import groovy.transform.CompileStatic
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.publish.maven.MavenPomDeveloper

@CompileStatic
class JpiPomDeveloper implements MavenPomDeveloper {
    final Property<String> id
    final Property<String> name
    final Property<String> email
    final Property<String> url
    final Property<String> organization
    final Property<String> organizationUrl
    final SetProperty<String> roles
    final Property<String> timezone
    final MapProperty<String, String> properties

    @SuppressWarnings('UnnecessarySetter')
    JpiPomDeveloper(ObjectFactory objects) {
        this.id = objects.property(String)
        this.name = objects.property(String)
        this.email = objects.property(String)
        this.url = objects.property(String)
        this.organization = objects.property(String)
        this.organizationUrl = objects.property(String)
        this.roles = objects.setProperty(String)
        this.timezone = objects.property(String)
        this.properties = objects.mapProperty(String, String)
    }

    String toManifestFormat() {
        [name, id, email]*.getOrElse('').join(':')
    }

    void setId(String id) {
        this.id.set(id)
    }

    void setName(String name) {
        this.name.set(name)
    }

    void setEmail(String email) {
        this.email.set(email)
    }

    void setUrl(String url) {
        this.url.set(url)
    }

    void setOrganization(String organization) {
        this.organization.set(organization)
    }

    void setOrganizationUrl(String organizationUrl) {
        this.organizationUrl.set(organizationUrl)
    }

    void setRoles(Collection<String> roles) {
        this.roles.set(roles)
    }

    void setTimezone(String timezone) {
        this.timezone.set(timezone)
    }

    void setProperties(Map<String, String> properties) {
        this.properties.set(properties)
    }
}
