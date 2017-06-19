Maven plugin - JSON Schema generator
===============================

This is a maven plugin/mojo to generate JSON schema files from JAXB annotated classes. JSON schemas are generated using [jackson-module-jsonSchema](https://github.com/FasterXML/jackson-module-jsonSchema)

[JSON Schema reference](http://json-schema.org/)


Software Prerequisites
----------------------
1. JDK 8
2. Maven 3+
3. JAXB 2.2+
4. Jackson 2.8.8


Setup
---
1. Create a maven project that contains JAXB annotated classes
2. Add the maven mojo as a plugin. 
	
		<plugin>
			<groupId>org.anair.maven</groupId>
			<artifactId>jsonschema-gen-mojo</artifactId>
			<version>0.0.1</version>
			<configuration>
				<jaxbDirectory>${basedir}/target/generated/jaxb</jaxbDirectory>
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

- The default phase is generate-resources
- Specify the JAXB source directory
- Specify target JSON schema directory. Defaulted to src/main/resources/json-schema
- Specify packages/classes to be included or excluded 
	
Generate JSON schema files
----------
1. Run ``mvn generate-resources``     
2. JSON schema files will be in "jsonschemaDirectory"
