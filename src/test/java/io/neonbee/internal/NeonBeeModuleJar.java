package io.neonbee.internal;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;

import io.neonbee.internal.deploy.NeonBeeModule;

public class NeonBeeModuleJar extends BasicJar {

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
     */
    public static Manifest createManifest(String name, Collection<String> deployables) {
        return createManifest(name, deployables, List.of(), List.of(), List.of());
    }

    /**
     * Generates a Manifest file with the attribute NeonBee-Deployables which contains the passed verticle deployables
     * and the attribute NeonBee-Models which contains the passed model paths.
     *
     * @param deployables         The deployables
     * @param modelPaths          The paths to the model files (*.csn) inside of the JAR
     * @param extensionModelPaths The paths to the extension model files (*.edmx) inside of the JAR
     * @return A Manifest with the passed deployables
     */
    public static Manifest createManifest(String name, Collection<String> deployables, Collection<String> modelPaths,
            Collection<String> extensionModelPaths, Collection<String> hooks) {
        Map<String, String> attributes = Map.of(NeonBeeModule.NEONBEE_MODULE, name, NeonBeeModule.NEONBEE_DEPLOYABLES,
                String.join(";", deployables), NeonBeeModule.NEONBEE_HOOKS, String.join(";", hooks),
                NeonBeeModule.NEONBEE_MODELS, String.join(";", modelPaths), NeonBeeModule.NEONBEE_MODEL_EXTENSIONS,
                String.join(";", extensionModelPaths));
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
        for (Entry<String, byte[]> entry : models.entrySet()) {
            content.put(new ZipEntry(entry.getKey()), entry.getValue());
        }
        for (Entry<String, byte[]> entry : extensionModels.entrySet()) {
            content.put(new ZipEntry(entry.getKey()), entry.getValue());
        }

        return content;
    }
}
