package io.neonbee.entity;

import static java.util.Objects.requireNonNull;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsService;

public final class EntityModelDefinition {
    /**
     * File extension for CSN model definition files.
     */
    public static final String CSN = ".csn";

    /**
     * File extension for EDMX model files.
     */
    public static final String EDMX = ".edmx";

    private final Map<String, byte[]> csnModelDefinitions;

    private final Map<String, byte[]> associatedModelDefinitions;

    /**
     * Creates a new {@link EntityModelDefinition} based on a CSN and any number of associated model definitions like
     * EDMX files.
     *
     * @param csnModelDefinitions        any number of CSN model definitions
     * @param associatedModelDefinitions any number of associated model definition files such as EDMX
     */
    public EntityModelDefinition(Map<String, byte[]> csnModelDefinitions,
            Map<String, byte[]> associatedModelDefinitions) {
        this.csnModelDefinitions = requireNonNull(csnModelDefinitions);
        this.associatedModelDefinitions = requireNonNull(associatedModelDefinitions);
    }

    /**
     * Get all CSN model definitions of this {@link EntityModelDefinition}.
     *
     * @return a map of CSN models
     */
    public Map<String, byte[]> getCSNModelDefinitions() {
        return csnModelDefinitions;
    }

    /**
     * Get all associated model definitions of this {@link EntityModelDefinition}, such as associated EDMX model files.
     *
     * @return a map of associated models
     */
    public Map<String, byte[]> getAssociatedModelDefinitions() {
        return associatedModelDefinitions;
    }

    @Override
    public String toString() {
        return String.join(",", csnModelDefinitions.keySet()) + "$"
                + String.join(",", associatedModelDefinitions.keySet());
    }

    /**
     * Extracts the prefix of the CSN Model Service to get the Namespace.
     *
     * @param csnModel the CSN Model
     * @return the prefix of the Service or throws a RuntimeException if no service is available in the CDS model
     */
    public static String getNamespace(CdsModel csnModel) {
        return csnModel.services().findAny().map(CdsService::getQualifiedName).map(name -> {
            int lastIndexOf = name.lastIndexOf('.');
            return lastIndexOf == -1 ? "" : name.substring(0, lastIndexOf);
        }).orElseThrow(() -> new RuntimeException("No service found in CDS model!"));
    }

    /**
     * Extracts the namespace from a full qualified service name. For instance a full qualified service name such as
     * "io.neonbee.test1.TestService" will be modified into "io.neonbee.test1".
     *
     * @param qualifiedServiceName full qualified service name such as "io.neonbee.test1.TestService"
     * @return extracted namespace
     */
    public static String retrieveNamespace(String qualifiedServiceName) {
        int lastIndexOf = qualifiedServiceName.lastIndexOf('.');
        return lastIndexOf == -1 ? "" : qualifiedServiceName.substring(0, lastIndexOf);
    }

    /**
     * Resolves a list of EDMX paths from a CsnPath and a CdsModel.
     *
     * @param csnPath  csn path
     * @param cdsModel cds model
     * @return a list of EDMX paths
     */
    public static List<Path> resolveEdmxPaths(Path csnPath, CdsModel cdsModel) {
        return cdsModel.services().map(service -> {
            String qualifiedName = service.getQualifiedName();
            String edmxFileName = qualifiedName + EDMX;
            return csnPath.getParent().resolve(edmxFileName);
        }).collect(Collectors.toList());
    }
}
