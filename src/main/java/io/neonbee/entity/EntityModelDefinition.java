package io.neonbee.entity;

import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.google.common.annotations.VisibleForTesting;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.CdsService;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.shareddata.ClusterSerializable;

public final class EntityModelDefinition implements ClusterSerializable {
    /**
     * File extension for CSN model definition files.
     */
    public static final String CSN = ".csn";

    /**
     * File extension for EDMX model files.
     */
    public static final String EDMX = ".edmx";

    private Map<String, byte[]> csData;

    private Map<String, byte[]> associatedData;

    /**
     * Creates a new {@link EntityModelDefinition} based on a CSN and any number of associated model definitions like
     * EDMX files.
     *
     * @param csData         any number of CSN model definitions
     * @param associatedData any number of associated model definition files such as EDMX
     */
    public EntityModelDefinition(Map<String, byte[]> csData, Map<String, byte[]> associatedData) {
        this.csData = requireNonNull(csData);
        this.associatedData = requireNonNull(associatedData);
    }

    @VisibleForTesting
    public EntityModelDefinition() {}

    /**
     * Get all CSN model definitions of this {@link EntityModelDefinition}.
     *
     * @return a map of CSN models
     */
    public Map<String, byte[]> getCSNModelDefinitions() {
        return csData;
    }

    /**
     * Get all associated model definitions of this {@link EntityModelDefinition}, such as associated EDMX model files.
     *
     * @return a map of associated models
     */
    public Map<String, byte[]> getAssociatedData() {
        return associatedData;
    }

    @Override
    public String toString() {
        return String.join(",", csData.keySet().stream().sorted().collect(Collectors.toList())) + "$"
                + String.join(",", associatedData.keySet().stream().sorted().collect(Collectors.toList()));
    }

    /**
     * Extracts the prefix of the CSN Model Service to get the Namespace.
     *
     * @param csnModel the CSN Model
     * @return the prefix of the service in the CSN model, or null, in case no service was defined
     */
    public static String getNamespace(CdsModel csnModel) {
        return csnModel.services().findAny().map(CdsService::getQualifiedName).map(name -> {
            int lastIndexOf = name.lastIndexOf('.');
            return lastIndexOf == -1 ? "" : name.substring(0, lastIndexOf);
        }).orElse(null);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return toString().equals(o.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(csData.keySet(), associatedData.keySet());
    }

    public Buffer toBuffer() {
        Buffer buffer = Buffer.buffer();
        writeToBuffer(buffer);
        return buffer;
    }

    public static EntityModelDefinition fromBuffer(Buffer buffer) {
        EntityModelDefinition definition = new EntityModelDefinition();
        definition.readFromBuffer(0, buffer);
        return definition;
    }

    @Override
    public void writeToBuffer(Buffer buffer) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            byte[] csn = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(csData);
            byte[] associated = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(associatedData);

            buffer.appendInt(csn.length);
            buffer.appendBytes(csn);
            buffer.appendInt(associated.length);
            buffer.appendBytes(associated);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public int readFromBuffer(int pos, Buffer buffer) {

        try {
            ObjectMapper mapper = new ObjectMapper();
            ObjectReader reader = mapper.readerFor(new TypeReference<Map<String, byte[]>>() {});

            int len = buffer.getInt(pos);
            pos += 4;
            csData = reader.readValue(buffer.getBytes(), pos, pos + len);
            pos += len;

            len = buffer.getInt(pos);
            pos += 4;
            associatedData = reader.readValue(buffer.getBytes(), pos, pos + len);
            pos += len;
            return pos;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
