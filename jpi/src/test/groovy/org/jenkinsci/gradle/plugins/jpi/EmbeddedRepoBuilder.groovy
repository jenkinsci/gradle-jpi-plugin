package org.jenkinsci.gradle.plugins.jpi

class EmbeddedRepoBuilder {
    static URL makeEmbeddedRepo() {
        File embeddedRepo = File.createTempDir('embedded-repo', 'ivy')
        String versionDir = [embeddedRepo.path, 'org', 'example', 'myclient', '1.0'].join(File.separator)
        def storage = new File(versionDir)
        storage.mkdirs()
        new File(storage, 'ivy-1.0.xml') << EmbeddedRepoBuilder.getResource('/repo/org/example/myclient/1.0/ivy-1.0.xml').bytes
        new File(storage, 'myclient-1.0.jar') << EmbeddedRepoBuilder.getResource('/repo/org/example/myclient/1.0/myclient-1.0.jar').bytes

        embeddedRepo.toURI().toURL()
    }
}
