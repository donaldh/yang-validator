package donaldh;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;
import org.opendaylight.yangtools.yang.data.impl.codec.xml.XmlCodecProvider;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.ToNormalizedNodeParser;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.DomUtils;
import org.opendaylight.yangtools.yang.data.impl.schema.transform.dom.parser.DomToNormalizedNodeParserFactory;
import org.opendaylight.yangtools.yang.model.api.ContainerSchemaNode;
import org.opendaylight.yangtools.yang.model.api.Module;
import org.opendaylight.yangtools.yang.model.api.SchemaContext;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaContextFactory;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceFilter;
import org.opendaylight.yangtools.yang.model.repo.api.SchemaSourceRepresentation;
import org.opendaylight.yangtools.yang.model.repo.api.SourceIdentifier;
import org.opendaylight.yangtools.yang.model.repo.api.YangTextSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.PotentialSchemaSource;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaListenerRegistration;
import org.opendaylight.yangtools.yang.model.repo.spi.SchemaSourceListener;
import org.opendaylight.yangtools.yang.model.repo.util.FilesystemSchemaSourceCache;
import org.opendaylight.yangtools.yang.parser.repo.SharedSchemaRepository;
import org.w3c.dom.Element;

public class YangValidator {
	
	private static final File MODELS_FOLDER;
	
	static {
		try {
			MODELS_FOLDER = new File(YangValidator.class.getResource("/yang/").toURI());
		} catch (Throwable t) {
			throw new IllegalStateException(t);
		}
	}

	public static void main(String[] args) {
		
		SharedSchemaRepository repo = new SharedSchemaRepository("test");
		FilesystemSchemaSourceCache<YangTextSchemaSource> fileSourceProvider = 
				new FilesystemSchemaSourceCache<YangTextSchemaSource>(repo, YangTextSchemaSource.class, MODELS_FOLDER);
		SchemaContextFactory schemaContextFactory = repo.createSchemaContextFactory(SchemaSourceFilter.ALWAYS_ACCEPT);
		
		final Set<SourceIdentifier> allSources = new HashSet<SourceIdentifier>();
        final SchemaListenerRegistration reg = repo.registerSchemaSourceListener(new SchemaSourceListener() {

			public void schemaSourceEncountered(SchemaSourceRepresentation source) {}

			public void schemaSourceRegistered(Iterable<PotentialSchemaSource<?>> sources) {
                for (final PotentialSchemaSource<?> source : sources) {
                	allSources.add(source.getSourceIdentifier());
                }
			}

			public void schemaSourceUnregistered(PotentialSchemaSource<?> source) {}
        });
        reg.close();
		
		try {
			SchemaContext schema = schemaContextFactory.createSchemaContext(allSources).get();
			
			XmlCodecProvider codecProvider = DomUtils.defaultValueCodecProvider();
			DomToNormalizedNodeParserFactory f = DomToNormalizedNodeParserFactory.getInstance(codecProvider, schema);
			
			ToNormalizedNodeParser<Element, ContainerNode, ContainerSchemaNode> parser = f.getContainerNodeParser();

			Set<Module> modules = schema.getModules();
			for (Module m : modules) {
				m.getDataChildByName(name);
			}
		} catch (Throwable t) {
			System.err.println(t.getClass().getName() + " : " + t.getMessage());
		}
		
	}

}
