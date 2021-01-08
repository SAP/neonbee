package io.neonbee.entity;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsService;

public final class ModelDefinitionHelper {
    private static final String EDMX = ".edmx";

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
