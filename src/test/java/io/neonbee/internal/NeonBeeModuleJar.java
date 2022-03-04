package io.neonbee.internal;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import io.neonbee.internal.deploy.DeployableModels;
import io.neonbee.internal.deploy.DeployableModule;
import io.neonbee.internal.deploy.DeployableVerticle;
import io.neonbee.internal.scanner.HookScanner;

public class NeonBeeModuleJar extends BasicJar {
    /**
     * A list of dummy verticles added with {@link NeonBeeModuleJarFactory#withVerticles()}.
     */
    public static final List<String> DUMMY_VERTICLES = List.of("ClassA", "ClassB");

    /**
     * A list of dummy models added with {@link NeonBeeModuleJarFactory#withModels()}.
     */
    public static final Map<String, byte[]> DUMMY_MODELS =
            Map.of("models/User.csn", "userCsn".getBytes(UTF_8), "models/Product.csn", "productCsn".getBytes(UTF_8));

    /**
     * A list of dummy extension models added with {@link NeonBeeModuleJarFactory#withModels()}.
     */
    public static final Map<String, byte[]> DUMMY_EXTENSION_MODELS = Map.of("models/User.edmx",
            "userEdmx".getBytes(UTF_8), "models/Product.edmx", "productEdmx".getBytes(UTF_8));

    /**
     * Creates a factory which can be used to construct a {@link NeonBeeModuleJar}.
     *
     * @param name the name of the {@link NeonBeeModuleJar}
     * @return a {@link NeonBeeModuleJarFactory} which provides methods to construct a {@link NeonBeeModuleJar}.
     */
    public static NeonBeeModuleJarFactory create(String name) {
        return new NeonBeeModuleJarFactory(name);
    }

    public NeonBeeModuleJar(String name, ClassTemplate... verticleTemplates) throws IOException {
        this(name, Arrays.asList(verticleTemplates));
    }

    public NeonBeeModuleJar(String name, List<ClassTemplate> verticleTemplates) throws IOException {
        super(createManifest(name, extractIdentifiers(verticleTemplates)),
                transformToContentMap(verticleTemplates, Map.of(), Map.of()));
    }

    public NeonBeeModuleJar(String name, List<ClassTemplate> verticleTemplates, Map<String, byte[]> models,
            Map<String, byte[]> extensionModels) throws IOException {
        super(createManifest(name, extractIdentifiers(verticleTemplates), models.keySet(), extensionModels.keySet(),
                List.of()), transformToContentMap(verticleTemplates, models, extensionModels));
    }

    /**
     * @see NeonBeeModuleJar#createManifest(String, Collection, Collection, Collection, Collection)
     *
     * @param name        The module name / identifier
     * @param deployables The deployables
     * @return A Manifest with the passed deployables
     */
    public static Manifest createManifest(String name, Collection<String> deployables) {
        return createManifest(name, deployables, List.of(), List.of(), List.of());
    }

    /**
     * Generates a Manifest file with the attribute NeonBee-Deployables which contains the passed verticle deployables
     * and the attribute NeonBee-Models which contains the passed model paths.
     *
     * @param name                The module name / identifier
     * @param deployables         The FQN of the deployables
     * @param modelPaths          The paths to the model files (*.csn) inside of the JAR
     * @param extensionModelPaths The paths to the extension model files (*.edmx) inside of the JAR
     * @param hooks               The FQN of the hooks
     * @return A Manifest with the passed deployables
     */
    public static Manifest createManifest(String name, Collection<String> deployables, Collection<String> modelPaths,
            Collection<String> extensionModelPaths, Collection<String> hooks) {
        Map<String, String> attributes = Map.of(DeployableModule.NEONBEE_MODULE, name,
                DeployableVerticle.NEONBEE_DEPLOYABLES, String.join(";", deployables), HookScanner.NEONBEE_HOOKS,
                String.join(";", hooks), DeployableModels.NEONBEE_MODELS, String.join(";", modelPaths),
                DeployableModels.NEONBEE_MODEL_EXTENSIONS, String.join(";", extensionModelPaths));
        return BasicJar.createManifest(attributes);
    }

    private static List<String> extractIdentifiers(List<ClassTemplate> verticleTemplates) {
        return verticleTemplates.stream().map(ClassTemplate::getClassName).collect(Collectors.toList());
    }

    private static Map<ZipEntry, byte[]> transformToContentMap(List<ClassTemplate> verticleTemplates,
            Map<String, byte[]> models, Map<String, byte[]> extensionModels) throws IOException {
        Map<ZipEntry, byte[]> content = new HashMap<>(verticleTemplates.size());
        for (ClassTemplate verticleTemplate : verticleTemplates) {
            content.put(new ZipEntry(getJarEntryName(verticleTemplate.getClassName())),
                    verticleTemplate.compileToByteCode());
        }
        for (Map.Entry<String, byte[]> entry : models.entrySet()) {
            content.put(new ZipEntry(entry.getKey()), entry.getValue());
        }
        for (Map.Entry<String, byte[]> entry : extensionModels.entrySet()) {
            content.put(new ZipEntry(entry.getKey()), entry.getValue());
        }

        return content;
    }

    /**
     * A factory for {@link NeonBeeModuleJar}.
     */
    public static class NeonBeeModuleJarFactory {
        private final String name;

        private final List<ClassTemplate> verticleTemplates = new ArrayList<>();

        private final Map<String, byte[]> models = new HashMap<>();

        private final Map<String, byte[]> extensionModels = new HashMap<>();

        NeonBeeModuleJarFactory(String name) {
            this.name = name;
        }

        /**
         * Add two dummy verticles ("ClassA" and "ClassB") to the module.
         *
         * @return the factory for chaining
         * @throws IOException failed to create a {@link DummyVerticleTemplate}
         */
        public NeonBeeModuleJarFactory withVerticles() throws IOException {
            return withVerticles(DUMMY_VERTICLES.toArray(String[]::new));
        }

        /**
         * Add any number of verticles to the module.
         *
         * @param classNames the class names of the verticles
         * @return the factory for chaining
         * @throws IOException failed to create a {@link DummyVerticleTemplate}
         */
        public NeonBeeModuleJarFactory withVerticles(String... classNames) throws IOException {
            for (String className : classNames) {
                addVerticle(new DummyVerticleTemplate(className, "doesn't matter"));
            }
            return this;
        }

        /**
         * Add dummy models to the module.
         *
         * @return the factory for chaining
         */
        public NeonBeeModuleJarFactory withModels() {
            DUMMY_MODELS.forEach(this::addModel);
            DUMMY_EXTENSION_MODELS.forEach(this::addExtensionModel);

            return this;
        }

        /**
         * Add a verticle template to the module.
         *
         * @param verticleTemplate the verticle template to add
         * @return the factory for chaining
         */
        public NeonBeeModuleJarFactory addVerticle(ClassTemplate verticleTemplate) {
            verticleTemplates.add(verticleTemplate);
            return this;
        }

        /**
         * Add a model to the module.
         *
         * @param name    the name of the model
         * @param content the content of the model
         * @return the factory for chaining
         */
        public NeonBeeModuleJarFactory addModel(String name, byte[] content) {
            models.put(name, content);
            return this;
        }

        /**
         * Add a extension model to the module.
         *
         * @param name    the name of the model
         * @param content the content of the model
         * @return the factory for chaining
         */
        public NeonBeeModuleJarFactory addExtensionModel(String name, byte[] content) {
            extensionModels.put(name, content);
            return this;
        }

        /**
         * Build the module JAR.
         *
         * @return the module jar
         * @throws IOException if building the module JAR failed
         */
        public NeonBeeModuleJar build() throws IOException {
            return new NeonBeeModuleJar(name, verticleTemplates, models, extensionModels);
        }
    }
}
