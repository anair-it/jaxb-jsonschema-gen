package org.anair.jsonschema.mojo;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.factories.SchemaFactoryWrapper;
import com.fasterxml.jackson.module.jsonSchema.factories.VisitorContext;

/**
 * Generate JSON schema files from JAXB classes
 * <p>
 * Generated files will be in src/main/resources/${jsonschemaDirectory}
 * 
 * <p>
 * Plugin configuration:
 * <pre>{@code
 		<plugin>
			<groupId>org.anair.maven</groupId>
			<artifactId>jsonschema-gen-mojo</artifactId>
			<version>0.0.1</version>
			<configuration>
				<jaxbDirectory>basedir/target/generated/jaxb</jaxbDirectory>
				<jsonschemaDirectory>json-schema</jsonschemaDirectory>
				<includes>
					<include>/com/foo/**.java</include>
				</includes>
				<excludes>
					<exclude>/com/foo/Bar.java</exclude>
				</excludes>
			</configuration>

			<executions>
				<execution>
					<goals>
						<goal>jaxb-jsonschema-gen</goal>
					</goals>
				</execution>
			</executions>
		</plugin>
 * }</pre>
 * 
 * @author anair
 *
 */
@Mojo(name = "jaxb-jsonschema-gen", defaultPhase=LifecyclePhase.GENERATE_RESOURCES, requiresDependencyResolution=ResolutionScope.RUNTIME)
public class JaxbJsonSchemaGenMojo extends AbstractMojo {

	private static final String TARGET_SRC_DIRECTORY = "src/main/resources/";
	private static final String FILE_SEPERATOR = System.getProperty("file.separator");

	private static final Logger LOG = Logger.getLogger(JaxbJsonSchemaGenMojo.class);
	
	@Parameter(property = "jaxbDirectory", required=true)
    private String jaxbDirectory;
	
	@Parameter(property = "jsonschemaDirectory", defaultValue="json-schema")
    private String jsonschemaDirectory;
	
	@Parameter(property = "includes", required=true)
    private List<String> includes;
	
	@Parameter(property = "excludes", required=false)
    private List<String> excludes;
	
	@Parameter(property = "project", required=false, readonly=true)
	private MavenProject project;
	
	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		if(LOG.isTraceEnabled()){
			LOG.trace("JSON Schema directory: "+ jaxbDirectory);
			LOG.trace("JAXB source directory: "+ jsonschemaDirectory);
			LOG.trace("Includes: "+ includes);
			LOG.trace("Excludes: "+ excludes);
		}
		
		try {
			List<String> fileNames = FileUtils.getFileNames(new File(jaxbDirectory), createPattern(includes, "**"), createPattern(excludes, ""), false);
			LOG.debug("JAXB classes: " + fileNames);
			
			String[] fullyQualifiedJaxbClassArr = convertToPackageNameFormat(fileNames);
			
			ObjectMapper objMapper = createJaxbObjectMapper();
			int count = writeJsonSchemaFiles(objMapper, fullyQualifiedJaxbClassArr);
			
			LOG.info("Generated "+ count +" JSON schema files.");
		} catch (IOException e) {
			throw new MojoExecutionException(
					"Unable to get list of JAXB classes.", e);
		} catch (DependencyResolutionRequiredException e) {
			throw new MojoExecutionException(e.getMessage(), e);
		}
	}

	
	private String[] convertToPackageNameFormat(List<String> fileNames) {
		List<String> fullyQualifiedJaxbClassList = new ArrayList<String>();
		for(String fileName: fileNames){
			fileName = FilenameUtils.removeExtension(fileName);
			fileName = StringUtils.replace(fileName, FILE_SEPERATOR, ".");
			fullyQualifiedJaxbClassList.add(fileName);
		}
		
		String[] fullyQualifiedJaxbClassArr = fullyQualifiedJaxbClassList.toArray(new String[fullyQualifiedJaxbClassList.size()]);
		return fullyQualifiedJaxbClassArr;
	}


	private String createPattern(List<String> patterns, String defaultPattern) {
		String pattern = defaultPattern;
		if (CollectionUtils.isNotEmpty(patterns)) {
			pattern = StringUtils.join(patterns.iterator(), ",");
		}
		return pattern;
	}
	
	/**
	 * Create JSON Schema files based upon JAXB classes
	 * 
	 * @throws IOException 
	 * @return number of Json schema files generated 
	 * @throws DependencyResolutionRequiredException 
	 */
	private int writeJsonSchemaFiles(ObjectMapper objMapper, String... fullyQualifiedJaxbClasses) throws IOException, DependencyResolutionRequiredException {
		SchemaFactoryWrapper visitor = new SchemaFactoryWrapper();
		visitor.setVisitorContext(new HideUrnVisitorContext());
		int count = 0;
		for(String fullyQualifiedJaxbClass:fullyQualifiedJaxbClasses){
			try {
				Class<?> clz = Class.forName(fullyQualifiedJaxbClass, false, getClassLoader());
				
				objMapper.acceptJsonFormatVisitor(objMapper.constructType(clz), visitor);
//				objMapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'"));
//				objMapper.setTimeZone(TimeZone.getDefault());
//				objMapper.disable(SerializationFeature.WRITE_EMPTY_JSON_ARRAYS);
				JsonSchema jsonSchema = visitor.finalSchema();
				jsonSchema.setId(clz.getSimpleName());
				String jsonSchemaString = objMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema);
				
				File file = new File(TARGET_SRC_DIRECTORY + jsonschemaDirectory + FILE_SEPERATOR + clz.getSimpleName()+".json");
				org.apache.commons.io.FileUtils.writeStringToFile(file, jsonSchemaString, Charset.defaultCharset());
				LOG.info("Generated: " + file.getName());
				count++;
			} catch (JsonMappingException e) {
				LOG.error("Unable to map JSON: " + e);
			} catch (JsonProcessingException e) {
				LOG.error("Unable to process JSON: " + e);
			} catch (ClassNotFoundException e) {
				LOG.error("Unable to find class " + e);
			}
		}
		return count;
	}
	
	/**
	 * Create instance of ObjectMapper with JAXB introspector
	 * and default type factory.
	 *
	 * @return Instance of ObjectMapper with JAXB introspector
	 *    and default type factory.
	 */
	private ObjectMapper createJaxbObjectMapper() {
		final ObjectMapper mapper = new ObjectMapper();
		final TypeFactory typeFactory = TypeFactory.defaultInstance();
		final AnnotationIntrospector introspector = new JaxbAnnotationIntrospector(typeFactory);
		mapper.getDeserializationConfig().with(introspector);
		mapper.getSerializationConfig().with(introspector);
		return mapper;
	}
	
	private ClassLoader getClassLoader() throws MalformedURLException, DependencyResolutionRequiredException {
		List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
		URL[] runtimeUrls = new URL[runtimeClasspathElements.size()];
		for (int i = 0; i < runtimeClasspathElements.size(); i++) {
			String element = (String) runtimeClasspathElements.get(i);
			runtimeUrls[i] = new File(element).toURI().toURL();
		}
		URLClassLoader classLoader = new URLClassLoader(runtimeUrls,Thread.currentThread().getContextClassLoader());
		return classLoader;
	}
	
	/**
	 * Overriding default "ref" uri pattern.
	 * <p>Originally it prints "$ref":"urn:jsonschema:com:package:Object". This pattern is invalid and reference is not resolved at deserialization time.
	 *  Hence overriding to print the Object name as the reference.
	 * 
	 * @author anair
	 *
	 */
	class HideUrnVisitorContext extends VisitorContext{
		
		private final HashSet<JavaType> seenSchemas = new HashSet<JavaType>();

	    @Override
		public String addSeenSchemaUri(JavaType seenSchema) {
	        if (seenSchema != null && !seenSchema.isPrimitive()) {
	            seenSchemas.add(seenSchema);
	            return javaTypeToUrn(seenSchema);
	        }
	        return null;
	    }

		@Override
		public String getSeenSchemaUri(JavaType seenSchema) {
			return (seenSchemas.contains(seenSchema)) ? javaTypeToUrn(seenSchema) : null;
		}

		@Override
		public String javaTypeToUrn(JavaType jt) {
			return jt.getRawClass().getSimpleName();
		}
	}
	
	public String getJaxbDirectory() {
		return jaxbDirectory;
	}

	public void setJaxbDirectory(String jaxbDirectory) {
		this.jaxbDirectory = jaxbDirectory;
	}

	public List<String> getIncludes() {
		return includes;
	}

	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}

	public List<String> getExcludes() {
		return excludes;
	}

	public void setExcludes(List<String> excludes) {
		this.excludes = excludes;
	}

	public String getJsonschemaDirectory() {
		return jsonschemaDirectory;
	}

	public void setJsonschemaDirectory(String jsonschemaDirectory) {
		this.jsonschemaDirectory = jsonschemaDirectory;
	}
	
}
