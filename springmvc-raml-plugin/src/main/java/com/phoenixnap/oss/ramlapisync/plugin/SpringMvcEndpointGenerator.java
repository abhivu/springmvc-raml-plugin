/*
 * Copyright 2002-2017 the original author or authors.
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See
 * the License for the
 * specific language governing permissions and limitations under the License.
 */
package com.phoenixnap.oss.ramlapisync.plugin;


import com.google.common.reflect.ClassPath;
import com.phoenixnap.oss.ramlapisync.data.ApiBodyMetadata;
import com.phoenixnap.oss.ramlapisync.data.ApiResourceMetadata;
import com.phoenixnap.oss.ramlapisync.generation.RamlParser;
import com.phoenixnap.oss.ramlapisync.generation.rules.ConfigurableRule;
import com.phoenixnap.oss.ramlapisync.generation.rules.RamlLoader;
import com.phoenixnap.oss.ramlapisync.generation.rules.Rule;
import com.phoenixnap.oss.ramlapisync.generation.rules.Spring4ControllerStubRule;
import com.phoenixnap.oss.ramlapisync.naming.NamingHelper;
import com.phoenixnap.oss.ramlapisync.naming.SchemaHelper;
import com.phoenixnap.oss.ramlapisync.pojo.PojoGenerationConfig;
import com.phoenixnap.oss.ramlapisync.raml.InvalidRamlResourceException;
import com.phoenixnap.oss.ramlapisync.raml.RamlRoot;
import com.phoenixnap.oss.ramlapisync.raml.rjp.raml10v2.RJP10V2RamlRoot;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.classworlds.realm.DuplicateRealmException;
import org.jsonschema2pojo.Annotator;
import org.jsonschema2pojo.GenerationConfig;
import org.jsonschema2pojo.Jackson1Annotator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * Maven Plugin MOJO specific to Generation of Spring MVC Endpoints from RAML documents.
 *
 * @author Kurt Paris
 * @since 0.2.1
 */
public class SpringMvcEndpointGenerator {
    /**
     * Path to the raml document to be verified
     */
    protected String ramlPath = "/Users/rahul/projects/viu-api-specifications/src/main/resources/api.raml";

    //    absolute path
    protected String basedir = ".";

    /**
     * Path to the pom  document to be verified
     */
    protected String pomPath = "NA";


    /**
     * Relative file path where the Java files will be saved to
     */
    protected String outputRelativePath = "target/generated-sources";

    /**
     * IF this is set to true, we will only parse methods that consume, produce or accept the
     * requested defaultMediaType
     */
    protected Boolean addTimestampFolder = false;

    /**
     * Java package to be applied to the generated files
     */
    protected String basePackage = "com.viu.api.specification";

    /**
     * The URI or relative path to the folder/network location containing JSON Schemas
     */
    protected String schemaLocation;


    /**
     * A boolean indicating whether the POJOs for unreferenced schemas defined in the RAML file
     * should be generated. By default, such schemas are not generated.
     */
    protected Boolean generateUnreferencedSchemas = false;


    /**
     * The explicit base path under which the rest endpoints should be located.
     * If overrules the baseUri setting in the raml spec.
     */
    protected String baseUri = "/api/v3";

    /**
     * If set to true, we will generate seperate methods for different content types in the RAML
     */
    protected Boolean seperateMethodsByContentType = false;

    /**
     * If set to true, we will generate Jackson 1 annotations inside the model objects
     */
    protected Boolean useJackson1xCompatibility = false;

    /**
     * The full qualified name of the Rule that should be used for code generation.
     */
    protected String rule = "com.phoenixnap.oss.ramlapisync.generation.rules.Spring4ControllerDecoratorRule";

    /**
     * Map of key/value configuration parameters that can be used to modify behaviour or certain
     * rules
     */
    protected Map<String, String> ruleConfiguration = new LinkedHashMap<>();

    /**
     * Configuration passed to JSONSchema2Pojo for generation of pojos.
     */
    protected JsonShema2PojoGenerationConfig1 generationConfig = new JsonShema2PojoGenerationConfig1();

    protected PojoGenerationConfig typeGenerationConfig = mapGenerationConfigMapping();

    /**
     * If set to true, we will generate methods with HttpHeaders as a parameter
     */
    protected Boolean injectHttpHeadersParameter = false;

    /**
     * How many levels of uri will be included in generated class names. Default
     * is 1 which means that only current resource will be in included in
     * controller/decorator names.
     */
    protected Integer resourceDepthInClassNames = 1;

    /**
     * Top level of URI included in generated class names. Default is 0 which means
     * that all resources will be in included in controller/decorator names.
     */
    protected Integer resourceTopLevelInClassNames = 0;

    /**
     * Reverse order of resource path that will be included in generated class names. Default is false which means
     * that resources will be in included in controller/decorator names from left to right.
     */
    protected Boolean reverseOrderInClassNames = false;

    private ClassRealm classRealm;

    private String resolvedSchemaLocation;


    protected void generateEndpoints()
            throws IOException, InvalidRamlResourceException {

        String resolvedPath = this.basedir;
        if (resolvedPath.endsWith(File.separator) || resolvedPath.endsWith("/")) {
            resolvedPath = resolvedPath.substring(0, resolvedPath.length() - 1);
        }

        String resolvedRamlPath = ramlPath;//this.basedir;
//
//        if (!ramlPath.startsWith(File.separator) && !ramlPath.startsWith("/")) {
//            resolvedRamlPath += File.separator + ramlPath;
//        }
//        else {
//            resolvedRamlPath += ramlPath;
//        }

        // Resolve schema location and add to classpath
        resolvedSchemaLocation = getSchemaLocation();

        RamlRoot loadRamlFromFile = RamlLoader.loadRamlFromFile(new File(resolvedRamlPath).toURI().toString());

        JCodeModel codeModel = null;
        //In the RJP10V2 we have support for a unified code model. RJP08V1 does not work well with this.
        //TODO update RJP08V1 to support a unified view.
        boolean unifiedModel = false;
        if (loadRamlFromFile instanceof RJP10V2RamlRoot) {
            codeModel = new JCodeModel();
            unifiedModel = true;
        }

        //Map the jsconschema2pojo config to ours. This will need to eventually take over. update just in case previous one was set before jsonconfig was set
        typeGenerationConfig = mapGenerationConfigMapping();

        RamlParser par = new RamlParser(typeGenerationConfig, getBasePath(loadRamlFromFile), seperateMethodsByContentType, injectHttpHeadersParameter, this.resourceDepthInClassNames, this.resourceTopLevelInClassNames, this.reverseOrderInClassNames);
        Set<ApiResourceMetadata> controllers = par.extractControllers(codeModel, loadRamlFromFile);

        if (StringUtils.hasText(outputRelativePath)) {
            if (!outputRelativePath.startsWith(File.separator) && !outputRelativePath.startsWith("/")) {
                resolvedPath += File.separator;
            }
            resolvedPath += outputRelativePath;
        } else {
            resolvedPath += "/target/generated-sources/spring-mvc";
        }

        File rootDir = new File(resolvedPath + (addTimestampFolder == true ? System.currentTimeMillis() : "") + "/");

        if (!rootDir.exists() && !rootDir.mkdirs()) {
            throw new IOException("Could not create directory:" + rootDir.getAbsolutePath());
        }

        generateCode(codeModel, controllers, rootDir);
        generateUnreferencedSchemas(codeModel, resolvedRamlPath, loadRamlFromFile, rootDir);

        if (unifiedModel) {
            buildCodeModelToDisk(codeModel, "Unified", rootDir);
        }
    }


    private void generateUnreferencedSchemas(JCodeModel codeModel, String resolvedRamlPath, RamlRoot loadRamlFromFile, File rootDir) {
        if (this.generateUnreferencedSchemas) {
            System.out.println("Generating Code for Unreferenced Schemas");

            if (loadRamlFromFile.getSchemas() != null && !loadRamlFromFile.getSchemas().isEmpty()) {
                for (Map<String, String> map : loadRamlFromFile.getSchemas()) {
                    for (String schemaName : map.keySet()) {
                        System.out.println("Generating POJO for unreferenced schema " + schemaName);
                        ApiBodyMetadata tempBodyMetadata = SchemaHelper.mapSchemaToPojo(loadRamlFromFile, schemaName, resolvedRamlPath, schemaName, this.resolvedSchemaLocation);
                        // TODO Check if this already has been written to disk
                        generateModelSources(codeModel, tempBodyMetadata, rootDir, this.generationConfig, this.useJackson1xCompatibility == true ? new Jackson1Annotator(this.generationConfig) : null);
                    }
                }
            }
        }
    }

    /**
     * @param codeModel   If not null this will operated assuming a unified code model for all output
     * @param controllers
     * @param rootDir
     */
    private void generateCode(JCodeModel codeModel, Set<ApiResourceMetadata> controllers, File rootDir) {
        for (ApiResourceMetadata met : controllers) {
            System.out.println("");
            System.out.println("-----------------------------------------------------------");
            System.out.println("Generating Code for Resource: " + met.getName());
            System.out.println("");

            if (codeModel == null) {
                Set<ApiBodyMetadata> dependencies = met.getDependencies();
                for (ApiBodyMetadata body : dependencies) {
                    generateModelSources(codeModel, body, rootDir, generationConfig, useJackson1xCompatibility == true ? new Jackson1Annotator(generationConfig) : null);
                }
            }

            generateControllerSource(codeModel, met, rootDir);
        }
    }


    /*
     * @return The configuration property <baseUri> (if set) or the baseUri from the RAML spec.
     */
    private String getBasePath(RamlRoot loadRamlFromFile) {
        // we take the given baseUri from raml spec by default.
        String basePath = loadRamlFromFile.getBaseUri();

        // If the baseUri is explicitly set by the plugin configuration we take it.
        if (baseUri != null) {
            basePath = baseUri;
        }

        // Because we can't load an empty string parameter value from maven config
        // the user needs to set a single "/", to overrule the raml spec.
        if (basePath != null && basePath.equals("/")) {
            // We remove a single "/" cause the leading slash will be generated by the raml
            // endpoints.
            basePath = "";
        }

        return basePath;
    }


    @SuppressWarnings("unchecked")
    private Rule<JCodeModel, JDefinedClass, ApiResourceMetadata> loadRule() {
        Rule<JCodeModel, JDefinedClass, ApiResourceMetadata> ruleInstance = new Spring4ControllerStubRule();
        try {
            ruleInstance = (Rule<JCodeModel, JDefinedClass, ApiResourceMetadata>) Class.forName(rule).newInstance();;
            System.out.println(StringUtils.collectionToCommaDelimitedString(ruleConfiguration.keySet()));
            System.out.println(StringUtils.collectionToCommaDelimitedString(ruleConfiguration.values()));

            if (ruleInstance instanceof ConfigurableRule<?, ?, ?> && !CollectionUtils.isEmpty(ruleConfiguration)) {
                System.out.println("SETTING CONFIG");
                ((ConfigurableRule<?, ?, ?>) ruleInstance).applyConfiguration(ruleConfiguration);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("Could not instantiate Rule " + this.rule + ". The default Rule will be used for code generation.");
        }
        return ruleInstance;
    }


    private ClassRealm getClassRealm() throws IOException, PlexusContainerException,
            DuplicateRealmException {
        if (classRealm == null) {
            ClassLoader cl = getClass().getClassLoader();
            Set<ClassPath.ClassInfo> classesInPackage = ClassPath.from(cl).getTopLevelClassesRecursive("com.viu.api.specification");
            //            final ClassWorld classWorld = new ClassWorld();
//            final ClassRealm realm = classWorld.newRealm("class-realm", getClass().getClassLoader());
//            Enumeration<URL> runtimeClasspathElements = realm.findResources("");

            for (ClassPath.ClassInfo aClassesInPackage : classesInPackage) {
                classRealm.addURL(aClassesInPackage.url());

            }
        }
        return classRealm;
    }


    private void generateModelSources(JCodeModel codeModel, ApiBodyMetadata body, File rootDir, GenerationConfig config, Annotator annotator) {
        boolean build = false;
        if (codeModel == null) {
            System.out.println("Generating Model object for: " + body.getName());
            build = true;
            if (config == null && annotator == null) {
                codeModel = body.getCodeModel();
            } else {
                codeModel = body.getCodeModel(resolvedSchemaLocation, basePackage + NamingHelper.getDefaultModelPackage(), config, annotator);
            }
        }
        if (build && codeModel != null) {
            buildCodeModelToDisk(codeModel, body.getName(), rootDir);
        }
    }


    private String getSchemaLocation() {

        if (StringUtils.hasText(schemaLocation)) {

            if (!schemaLocation.contains(":")) {
                String resolvedPath = this.basedir;
                if (resolvedPath.endsWith(File.separator) || resolvedPath.endsWith("/")) {
                    resolvedPath = resolvedPath.substring(0, resolvedPath.length() - 1);
                }

                if (!schemaLocation.startsWith(File.separator) && !schemaLocation.startsWith("/")) {
                    resolvedPath += File.separator;
                }

                resolvedPath += schemaLocation;


                if (!schemaLocation.endsWith(File.separator) && !schemaLocation.endsWith("/")) {
                    resolvedPath += File.separator;
                }
                resolvedPath = resolvedPath.replace(File.separator, "/").replace("\\", "/");
                try {
                    URLClassLoader urlClassLoader = (URLClassLoader) ClassLoader.getSystemClassLoader();
                    Class<?> urlClass = URLClassLoader.class;
                    Method method = urlClass.getDeclaredMethod("addURL", new Class[]{URL.class});
                    method.setAccessible(true);
                    method.invoke(urlClassLoader, new Object[]{new File(resolvedPath).toURI().toURL()});
                    return "classpath:/"; // since we have added this folder to the classpath this
                    // should be used by the plugin
                } catch (Exception ex) {
                    System.out.println("Could not add schema location to classpath");
                    ex.printStackTrace();
                    return new File(resolvedPath).toURI().toString();
                }
            }
            return schemaLocation;
        }
        return null;
    }


    private void generateControllerSource(JCodeModel codeModel, ApiResourceMetadata met, File dir) {
        boolean build = false;
        if (codeModel == null) {
            codeModel = new JCodeModel();
            build = true;
        }
        loadRule().apply(met, codeModel);
        if (build) {
            buildCodeModelToDisk(codeModel, met.getName(), dir);
        }
    }


    private void buildCodeModelToDisk(JCodeModel codeModel, String name, File dir) {
        try {
            codeModel.build(dir);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Could not build code model for " + name);
        }
    }


    public void execute() throws Exception {
        long startTime = System.currentTimeMillis();

        try {
            generateEndpoints();
        } catch (IOException e) {
            throw new Exception("Unexpected exception while executing Spring MVC Endpoint Generation Plugin." + e.toString());
        } catch (InvalidRamlResourceException e) {
            throw new Exception("Supplied RAML has failed validation and cannot be loaded." + e.toString());
        }

        System.out.println("Endpoint Generation Complete in:" + (System.currentTimeMillis() - startTime) + "ms");
    }

    protected PojoGenerationConfig mapGenerationConfigMapping() {
        PojoGenerationConfig config = new PojoGenerationConfig()
                .withPackage(basePackage, null);

        if (generationConfig != null) {
            config.apply(generationConfig);
        }
        return config;
    }

    public static void main(String[] args) throws Exception {
        SpringMvcEndpointGenerator springMvcEndpointGenerator = new SpringMvcEndpointGenerator();
        springMvcEndpointGenerator.execute();
    }
}
