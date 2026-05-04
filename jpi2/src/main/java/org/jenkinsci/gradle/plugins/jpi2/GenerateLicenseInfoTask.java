package org.jenkinsci.gradle.plugins.jpi2;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates {@code licenses.xml} for libraries bundled into the plugin package.
 */
public abstract class GenerateLicenseInfoTask extends DefaultTask {
    /** Standard name under which this task is registered. */
    public static final String NAME = "generateLicenseInfo";
    private static final String LICENSE_NAMESPACE = "licenses";

    /** @return directory where {@code licenses.xml} is written */
    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    /** @return POM files for all bundled runtime dependencies */
    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getPomFiles();

    /** @return version of the plugin project, written into the root dependency entry */
    @Input
    public abstract Property<String> getProjectVersion();

    /** @return Maven artifact ID of the plugin project */
    @Input
    public abstract Property<String> getProjectName();

    /** @return Maven group ID of the plugin project */
    @Input
    public abstract Property<String> getProjectGroup();

    /** @return optional human-readable description included in the root dependency entry */
    @Input
    @Optional
    public abstract Property<String> getProjectDescription();

    /** @return optional project URL included in the root dependency entry */
    @Input
    @Optional
    public abstract Property<String> getProjectUrl();

    /** Collects POM files and writes a {@code licenses.xml} describing their license metadata. */
    @TaskAction
    public void generateLicenseInfo() {
        var outputDir = getOutputDirectory().get().getAsFile();
        var outputFile = new File(outputDir, "licenses.xml");
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IllegalStateException("Could not create output directory: " + outputDir);
        }

        var pomFiles = collectPomFiles();
        writeLicensesFile(outputFile, pomFiles);
    }

    private Set<File> collectPomFiles() {
        var pomFiles = getPomFiles().getFiles();
        var resolved = new HashSet<File>();
        for (var pomFile : pomFiles) {
            if (pomFile.exists()) {
                resolved.add(pomFile);
            } else {
                getLogger().warn("POM file does not exist: {}", pomFile);
            }
        }
        return resolved;
    }

    private void writeLicensesFile(File outputFile, Set<File> pomFiles) {
        var extractor = new PomLicenseDataExtractor();
        var document = createDocument();

        var version = getProjectVersion().get();
        var name = getProjectName().get();
        var group = getProjectGroup().get();
        var description = getProjectDescription().getOrNull();
        var url = getProjectUrl().getOrNull();

        var root = document.createElementNS(LICENSE_NAMESPACE, "l:dependencies");
        root.setAttribute("xmlns:l", LICENSE_NAMESPACE);
        root.setAttribute("version", version);
        root.setAttribute("artifactId", name);
        root.setAttribute("groupId", group);
        document.appendChild(root);

        var projectDependency = appendDependency(document, root,
                version,
                name,
                group,
                description != null ? description : name,
                url);
        appendDescription(document, projectDependency, description != null ? description : "");

        pomFiles.stream()
                .sorted(Comparator.comparing(File::getName))
                .forEach(pomFile -> {
                    var data = extractor.extractFrom(pomFile);
                    var dependency = appendDependency(document, root,
                            data.version(),
                            data.artifactId(),
                            data.groupId(),
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

    private record PomLicenseData(String groupId, String artifactId, String version, String name, String description, String url, List<LicenseInfo> licenses) {
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
                var root = document.getDocumentElement();

                var groupId = directChildText(root, "groupId");
                var artifactId = directChildText(root, "artifactId");
                var version = directChildText(root, "version");
                var name = directChildText(root, "name");
                var description = directChildText(root, "description");
                var url = directChildText(root, "url");
                var licenses = new ArrayList<LicenseInfo>();

                // Fall back to parent GAV if not directly specified
                Node parentNode = directChild(root, "parent");
                if (parentNode instanceof Element parentElement) {
                    if (groupId.isEmpty()) groupId = directChildText(parentElement, "groupId");
                    if (version.isEmpty()) version = directChildText(parentElement, "version");
                }

                Node licensesContainer = directChild(root, "licenses");
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

                return new PomLicenseData(groupId, artifactId, version, name, description, url, licenses);
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
