package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.LenientConfiguration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedDependency;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.attributes.Usage;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates {@code licenses.xml} for libraries bundled into the plugin package.
 */
public class GenerateLicenseInfoTask extends DefaultTask {
    public static final String NAME = "generateLicenseInfo";
    private static final String LICENSE_NAMESPACE = "licenses";

    private final DirectoryProperty outputDirectory;
    private Configuration libraryConfiguration;

    public GenerateLicenseInfoTask() {
        this.outputDirectory = getProject().getObjects().directoryProperty();
    }

    @OutputDirectory
    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    @Classpath
    public Configuration getLibraryConfiguration() {
        return libraryConfiguration;
    }

    public void setLibraryConfiguration(Configuration libraryConfiguration) {
        this.libraryConfiguration = libraryConfiguration;
    }

    @TaskAction
    public void generateLicenseInfo() {
        var outputDir = outputDirectory.get().getAsFile();
        var outputFile = new File(outputDir, "licenses.xml");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Could not create output directory: " + outputDir);
        }

        var pomArtifacts = collectPomArtifacts(outputFile.toPath());
        writeLicensesFile(outputFile, pomArtifacts);
    }

    private Set<ResolvedArtifact> collectPomArtifacts(Path destination) {
        var deps = collectPomDependencies();
        var detached = getProject().getConfigurations().detachedConfiguration(deps.toArray(new Dependency[0]));
        detached.getAttributes().attribute(Usage.USAGE_ATTRIBUTE,
                getProject().getObjects().named(Usage.class, Usage.JAVA_RUNTIME));

        LenientConfiguration lenient = detached.getResolvedConfiguration().getLenientConfiguration();

        var requested = new HashSet<String>();
        for (ResolvedDependency dependency : lenient.getAllModuleDependencies()) {
            requested.add(dependency.getModule().toString());
        }
        var resolved = new HashSet<String>();
        for (ResolvedArtifact artifact : lenient.getArtifacts()) {
            resolved.add(artifact.getModuleVersion().getId().toString());
        }
        var unresolved = requested.stream()
                .filter(it -> !resolved.contains(it))
                .sorted()
                .toList();
        if (!unresolved.isEmpty()) {
            var pluralized = unresolved.size() == 1 ? "dependency" : "dependencies";
            var message = new StringBuilder(String.format("Could not resolve license(s) via POM for %d %s:%n",
                    unresolved.size(), pluralized));
            for (String coordinate : unresolved) {
                message.append(String.format("\t- %s%n", coordinate));
            }
            message.append(String.format("The above will be missing from %s%n", destination));
            getLogger().warn(message.toString());
        }

        return lenient.getArtifacts();
    }

    private List<Dependency> collectPomDependencies() {
        if (libraryConfiguration == null) {
            throw new IllegalStateException("libraryConfiguration is not configured for " + getPath());
        }
        return libraryConfiguration.getResolvedConfiguration().getResolvedArtifacts().stream()
                .filter(artifact -> "jar".equals(artifact.getExtension()))
                .filter(artifact -> artifact.getId().getComponentIdentifier() instanceof ModuleComponentIdentifier)
                .map(artifact -> {
                    ModuleVersionIdentifier id = artifact.getModuleVersion().getId();
                    return getProject().getDependencies().create(id.getGroup() + ":" + id.getName() + ":" + id.getVersion() + "@pom");
                })
                .toList();
    }

    private void writeLicensesFile(File outputFile, Set<ResolvedArtifact> pomArtifacts) {
        var project = getProject();
        var extractor = new PomLicenseDataExtractor();
        var document = createDocument();

        var root = document.createElementNS(LICENSE_NAMESPACE, "l:dependencies");
        root.setAttribute("xmlns:l", LICENSE_NAMESPACE);
        root.setAttribute("version", project.getVersion().toString());
        root.setAttribute("artifactId", project.getName());
        root.setAttribute("groupId", project.getGroup().toString());
        document.appendChild(root);

        var projectDependency = appendDependency(document, root,
                project.getVersion().toString(),
                project.getName(),
                project.getGroup().toString(),
                project.getDescription() != null ? project.getDescription() : project.getName(),
                project.getProviders().gradleProperty("url").getOrNull());
        appendDescription(document, projectDependency, project.getDescription() != null ? project.getDescription() : "");

        pomArtifacts.stream()
                .sorted(Comparator.comparing(artifact -> artifact.getModuleVersion().getId().toString()))
                .forEach(pomArtifact -> {
                    var data = extractor.extractFrom(pomArtifact.getFile());
                    var gav = pomArtifact.getModuleVersion().getId();
                    var dependency = appendDependency(document, root,
                            gav.getVersion(),
                            gav.getName(),
                            gav.getGroup(),
                            data.name(),
                            data.url());
                    appendDescription(document, dependency, data.description());
                    for (var license : data.licenses()) {
                        appendLicense(document, dependency, license);
                    }
                });

        writeDocument(document, outputFile);
    }

    private static Element appendDependency(
            Document document,
            Element root,
            String version,
            String artifactId,
            String groupId,
            String name,
            String url) {
        var dependency = document.createElementNS(LICENSE_NAMESPACE, "l:dependency");
        dependency.setAttribute("version", valueOrEmpty(version));
        dependency.setAttribute("artifactId", valueOrEmpty(artifactId));
        dependency.setAttribute("groupId", valueOrEmpty(groupId));
        if (!valueOrEmpty(name).isBlank()) {
            dependency.setAttribute("name", name);
        }
        if (!valueOrEmpty(url).isBlank()) {
            dependency.setAttribute("url", url);
        }
        root.appendChild(dependency);
        return dependency;
    }

    private static void appendDescription(Document document, Element dependency, String description) {
        var descriptionElement = document.createElementNS(LICENSE_NAMESPACE, "l:description");
        descriptionElement.setTextContent(valueOrEmpty(description));
        dependency.appendChild(descriptionElement);
    }

    private static void appendLicense(Document document, Element dependency, LicenseInfo license) {
        var licenseElement = document.createElementNS(LICENSE_NAMESPACE, "l:license");
        if (!valueOrEmpty(license.url()).isBlank()) {
            licenseElement.setAttribute("url", license.url());
        }
        if (!valueOrEmpty(license.name()).isBlank()) {
            licenseElement.setAttribute("name", license.name());
        }
        dependency.appendChild(licenseElement);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }

    private static Document createDocument() {
        var factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        } catch (ParserConfigurationException ignored) {
            // Keep defaults when parser implementation does not support this.
        }
        try {
            return factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException("Unable to create XML document builder", e);
        }
    }

    private static void writeDocument(Document document, File outputFile) {
        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            try {
                transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            } catch (TransformerException ignored) {
                // Keep defaults when transformer implementation does not support this.
            }
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            transformer.transform(new DOMSource(document), new StreamResult(outputFile));
        } catch (TransformerException e) {
            throw new RuntimeException("Unable to write license XML to " + outputFile, e);
        }
    }

    private record PomLicenseData(String name, String description, String url, List<LicenseInfo> licenses) {
    }

    private record LicenseInfo(String name, String url) {
    }

    private static final class PomLicenseDataExtractor {
        private final DocumentBuilder builder;

        private PomLicenseDataExtractor() {
            this.builder = createDocumentBuilder();
        }

        private PomLicenseData extractFrom(File pomFile) {
            try {
                var document = builder.parse(pomFile);
                var project = document.getDocumentElement();

                var name = directChildText(project, "name");
                var description = directChildText(project, "description");
                var url = directChildText(project, "url");
                var licenses = new ArrayList<LicenseInfo>();

                Node licensesContainer = directChild(project, "licenses");
                if (licensesContainer instanceof Element element) {
                    var childNodes = element.getChildNodes();
                    for (int i = 0; i < childNodes.getLength(); i++) {
                        var child = childNodes.item(i);
                        if (child instanceof Element licenseElement && "license".equals(licenseElement.getTagName())) {
                            licenses.add(new LicenseInfo(
                                    directChildText(licenseElement, "name"),
                                    directChildText(licenseElement, "url")));
                        }
                    }
                }

                return new PomLicenseData(name, description, url, licenses);
            } catch (SAXException | IOException e) {
                throw new RuntimeException("Failed to parse POM: " + pomFile, e);
            }
        }

        private static String directChildText(Element parent, String childName) {
            Node child = directChild(parent, childName);
            return child == null ? "" : child.getTextContent();
        }

        private static Node directChild(Element parent, String childName) {
            NodeList childNodes = parent.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node child = childNodes.item(i);
                if (child instanceof Element element && childName.equals(element.getTagName())) {
                    return child;
                }
            }
            return null;
        }

        private static DocumentBuilder createDocumentBuilder() {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setExpandEntityReferences(false);
            trySetFeature(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
            trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            try {
                return factory.newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new RuntimeException("Unable to create POM parser", e);
            }
        }

        private static void trySetFeature(DocumentBuilderFactory factory, String feature, boolean value) {
            try {
                factory.setFeature(feature, value);
            } catch (ParserConfigurationException ignored) {
                // Keep defaults when parser implementation does not support this.
            }
        }
    }
}
