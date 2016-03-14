package donaldh;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlCodecProvider;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.ToNormalizedNodeParser;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.DataSchemaNode;
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

        final String schemaDir = args[0];
        final String yangfile = args[1];

        try {
            YangTextSchemaContextResolver resolver = YangTextSchemaContextResolver.create("resolver");

            for (File f : new File(schemaDir).listFiles()) {
                resolver.registerSource(f.toURI().toURL());
            }

            SchemaContext schema = resolver.getSchemaContext().get();

            Map<String, DataSchemaNode> modulesByNodeName = new HashMap<>();

            Set<Module> modules = schema.getModules();
            for (Module m : modules) {
                for (DataSchemaNode n : m.getChildNodes()) {
                    modulesByNodeName.put(n.getQName().getLocalName(), n);
                }
            }

            XmlCodecProvider codecProvider = DomUtils.defaultValueCodecProvider();
            DomToNormalizedNodeParserFactory f = DomToNormalizedNodeParserFactory.getInstance(codecProvider, schema);
            ToNormalizedNodeParser<Element, ContainerNode, ContainerSchemaNode> parser = f.getContainerNodeParser();

            DocumentBuilderFactory namespaceFactory = DocumentBuilderFactory.newInstance();
            namespaceFactory.setNamespaceAware(true);
            DocumentBuilder builder = namespaceFactory.newDocumentBuilder();
            InputStream is = new FileInputStream(new File(yangfile));
            Document d = builder.parse(is);

            Element root = d.getDocumentElement();
            String nodeName = root.getLocalName();

            DataSchemaNode schemaNode = modulesByNodeName.get(nodeName);
            ContainerNode c = parser.parse(Collections.singletonList(root), (ContainerSchemaNode) schemaNode);

            System.out.println(c);

        } catch (Throwable t) {
            System.err.println(t.getClass().getName() + " : " + t.getMessage());
        }
    }
}
