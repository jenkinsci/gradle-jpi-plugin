package org.jenkinsci.gradle.plugins.jpi.internal;

import groovy.util.Node;
import groovy.util.NodeList;
import groovy.util.XmlParser;
import groovy.xml.QName;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;

/**
 * Extracts license data from Maven POM files.
 */
public class LicenseDataExtractor {
    private final XmlParser parser;
    private final QName qDescription = QName.valueOf("description");
    private final QName qLicense = QName.valueOf("license");
    private final QName qLicenses = QName.valueOf("licenses");
    private final QName qName = QName.valueOf("name");
    private final QName qUrl = QName.valueOf("url");

    /**
     * Creates a new extractor with the given XML parser.
     *
     * @param parser the XML parser to use
     */
    public LicenseDataExtractor(XmlParser parser) {
        this.parser = parser;
    }

    /** Creates a new extractor with a default XML parser. */
    public LicenseDataExtractor() {
        this(init());
    }

    /**
     * Extracts license data from the given reader.
     *
     * @param reader the reader containing POM XML
     * @return the extracted license data
     */
    public LicenseData extractFrom(Reader reader) {
        try {
            Node pom = parser.parse(reader);

            String name = pom.getAt(qName).text();
            String description = pom.getAt(qDescription).text();
            String url = pom.getAt(qUrl).text();
            NodeList licensesContainer = pom.getAt(qLicenses);

            Set<License> mapped = new HashSet<>();

            for (Object o : licensesContainer) {
                NodeList licenses = ((Node) o).getAt(qLicense);
                for (Object l : licenses) {
                    Node n = (Node) l;
                    String licenseUrl = n.getAt(qUrl).text();
                    String licenseName = n.getAt(qName).text();
                    mapped.add(new License(licenseName, licenseUrl));
                }
            }
            return new LicenseData(name, description, url, mapped);
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    private static XmlParser init() {
        try {
            return new XmlParser(false, false);
        } catch (ParserConfigurationException | SAXException e) {
            throw new RuntimeException(e);
        }
    }
}
