package donaldh;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.api.schema.MapNode;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlCodecProvider;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.serializer.DomFromNormalizedNodeSerializerFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
import org.opendaylight.yangtools.yang.model.api.ListSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.RpcDefinition;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.api.SchemaNode;
import org.opendaylight.yangtools.yang.model.parser.api.YangSyntaxErrorException;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceException;
import org.opendaylight.yangtools.yang.parser.repo.YangTextSchemaContextResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

public class YangValidator {

    DocumentBuilderFactory documentBuilderFactory;
    SchemaContext schema;

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage is: YangValidator <schema-dir> <yang-file ...>");
            System.exit(1);
        }

        Logger logger = Logger.getLogger("org.opendaylight.yangtools");
        logger.setLevel(Level.WARNING);

        final String schemaDir = args[0];

        try {
            YangValidator validator = new YangValidator(schemaDir);
            for (String yangFile : Arrays.copyOfRange(args, 1, args.length)) {
                System.out.println("Parsing " + yangFile);
                SchemaNodePair parsed = validator.parse(yangFile);
                validator.generate(parsed);
            }
        } catch (Throwable t) {
            System.err.println(t.getClass().getName() + " : " + t.getMessage());
        }
    }

    public YangValidator(String schemaDir)
            throws MalformedURLException, SchemaSourceException, IOException, YangSyntaxErrorException {
        documentBuilderFactory = DocumentBuilderFactory.newInstance();
        documentBuilderFactory.setNamespaceAware(true);
        schema = getSchemaContext(schemaDir);
    }

    static class SchemaNodePair {
        public final SchemaNode schemaNode;
        public final NormalizedNode<?, ?> node;

        public SchemaNodePair(SchemaNode s, NormalizedNode<?, ?> n) {
            schemaNode = s;
            node = n;
        }
    }

    public SchemaNodePair parse(String yangFile)
            throws ParserConfigurationException, SAXException, IOException {

        // Read the source XML file
        DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
        InputStream is = new FileInputStream(new File(yangFile));
        Document d = builder.parse(is);

        // Look up the schema node for the root element
        Element root = d.getDocumentElement();
        String nodeName = root.getLocalName();

        DataSchemaNode schemaNode = null;
        Set<Module> modules = schema.getModules();
        search: for (Module m : modules) {
            for (DataSchemaNode n : m.getChildNodes()) {
                if (n.getQName().getLocalName().equals(nodeName)) {
                    schemaNode = n;
                    break search;
                }
            }
            for (RpcDefinition rpc : m.getRpcs()) {
                if (rpc.getQName().getLocalName().equals(nodeName)) {
                    root = getFirstChildElement(root);
                    schemaNode = rpc.getInput();
                    break search;
                }
            }
        }

        if (schemaNode == null) {
            throw new RuntimeException("Cannot find a schema for " + nodeName);
        }

        // Transform to NormalizedNode graph
        List<Element> elements = Collections.singletonList(root);
        NormalizedNode<?, ?> n = null;

        XmlCodecProvider codecProvider = DomUtils.defaultValueCodecProvider();
        DomToNormalizedNodeParserFactory f = DomToNormalizedNodeParserFactory.getInstance(codecProvider, schema);

        // Parse
        if (schemaNode instanceof ContainerSchemaNode) {
            n = f.getContainerNodeParser().parse(elements, (ContainerSchemaNode) schemaNode);
        } else if (schemaNode instanceof ListSchemaNode) {
            n = f.getOrderedListNodeParser().parse(elements, (ListSchemaNode) schemaNode);
        } else {
            throw new RuntimeException("Unable to parse " + schemaNode.getClass().getSimpleName());
        }

        return new SchemaNodePair(schemaNode, n);
    }

    public void generate(SchemaNodePair parsed) throws TransformerException, ParserConfigurationException {
        // Serialize back to XML dom
        Document doc = documentBuilderFactory.newDocumentBuilder().newDocument();
        DomFromNormalizedNodeSerializerFactory serializerFactory = DomFromNormalizedNodeSerializerFactory
                .getInstance(doc, DomUtils.defaultValueCodecProvider());
        Iterable<Element> iterable;
        if (parsed.node instanceof ContainerNode) {
            iterable = serializerFactory.getContainerNodeSerializer().serialize((ContainerSchemaNode) parsed.schemaNode,
                    (ContainerNode) parsed.node);
        } else if (parsed.node instanceof MapNode) {
            iterable = serializerFactory.getMapNodeSerializer().serialize((ListSchemaNode) parsed.schemaNode,
                    (MapNode) parsed.node);
        } else {
            throw new RuntimeException("Unable to serialize " + parsed.node.getClass().getSimpleName());
        }

        Element e = iterable.iterator().next();
        prettyPrint(e);
    }

    private void prettyPrint(Element e)
            throws TransformerFactoryConfigurationError, TransformerConfigurationException, TransformerException {
        // Pretty print the XML
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xalan}indent-amount", "2");
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(baos);
        transformer.transform(new DOMSource(e), result);
        System.out.println(baos);
    }

    private static SchemaContext getSchemaContext(final String schemaDir)
            throws SchemaSourceException, IOException, YangSyntaxErrorException, MalformedURLException {
        // Build a schema context from a directory of YANG files
        YangTextSchemaContextResolver resolver = YangTextSchemaContextResolver.create("resolver");
        for (File f : new File(schemaDir).listFiles()) {
            resolver.registerSource(f.toURI().toURL());
        }
        SchemaContext schema = resolver.getSchemaContext().get();
        return schema;
    }

    private static Element getFirstChildElement(Element parent) {
        Node n = parent.getFirstChild();
        while (n != null && n.getNodeType() != Node.ELEMENT_NODE) {
            n = n.getNextSibling();
        }
        return (Element) n;
    }
}
