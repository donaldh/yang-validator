package donaldh;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
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
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.parser.repo.YangTextSchemaContextResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

public class YangValidator {

    public static void main(String[] args) {
        if (args.length < 2) {
            System.err.println("Usage is: YangValidator <schema-dir> <yang-file>");
            System.exit(1);
        }
        
        Logger logger = Logger.getLogger("org.opendaylight.yangtools");
        logger.setLevel(Level.WARNING);

        final String schemaDir = args[0];
        final String yangFile = args[1];

        try {
            // Build a schema context from a directory of YANG files
            YangTextSchemaContextResolver resolver = YangTextSchemaContextResolver.create("resolver");
            for (File f : new File(schemaDir).listFiles()) {
                resolver.registerSource(f.toURI().toURL());
            }
            SchemaContext schema = resolver.getSchemaContext().get();

            // Create a map from top-level container node names to their schema
            // nodes
            Map<String, DataSchemaNode> modulesByNodeName = new HashMap<>();
            Set<Module> modules = schema.getModules();
            for (Module m : modules) {
                for (DataSchemaNode n : m.getChildNodes()) {
                    modulesByNodeName.put(n.getQName().getLocalName(), n);
                }
            }

            // Get a parser
            XmlCodecProvider codecProvider = DomUtils.defaultValueCodecProvider();
            DomToNormalizedNodeParserFactory f = DomToNormalizedNodeParserFactory.getInstance(codecProvider, schema);

            // Read the source XML file
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setNamespaceAware(true);
            DocumentBuilder builder = documentBuilderFactory.newDocumentBuilder();
            InputStream is = new FileInputStream(new File(yangFile));
            Document d = builder.parse(is);

            // Look up the schema node for the root element
            Element root = d.getDocumentElement();
            String nodeName = root.getLocalName();
            DataSchemaNode schemaNode = modulesByNodeName.get(nodeName);
            List<Element> elements = Collections.singletonList(root);
            NormalizedNode<?, ?> n = null;
            
            if (schemaNode == null) {
                throw new RuntimeException("Cannot find a schema for " + nodeName);
            }
            
            // Parse
            if (schemaNode instanceof ContainerSchemaNode) {
                n = f.getContainerNodeParser().parse(elements, (ContainerSchemaNode) schemaNode);
            } else if (schemaNode instanceof ListSchemaNode) {
                n = f.getOrderedListNodeParser().parse(elements, (ListSchemaNode) schemaNode);
            } else {
                throw new RuntimeException("Unable to parse " + schemaNode.getClass().getSimpleName());
            }

            // Serialize back to XML dom
            Document doc = documentBuilderFactory.newDocumentBuilder().newDocument();
            DomFromNormalizedNodeSerializerFactory serializerFactory = DomFromNormalizedNodeSerializerFactory
                    .getInstance(doc, DomUtils.defaultValueCodecProvider());
            Iterable<Element> iterable;
            if (n instanceof ContainerNode) {
                iterable = serializerFactory.getContainerNodeSerializer().serialize((ContainerSchemaNode) schemaNode, (ContainerNode) n);
            } else if (n instanceof MapNode) {
                iterable = serializerFactory.getMapNodeSerializer().serialize((ListSchemaNode) schemaNode, (MapNode) n);
            } else {
                throw new RuntimeException("Unable to serialize " + n.getClass().getSimpleName());
            }
            Element e = iterable.iterator().next();

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

        } catch (Throwable t) {
            System.err.println(t.getClass().getName() + " : " + t.getMessage());
        }
    }
}
