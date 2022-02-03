package io.neonbee.entity;

import static java.util.Objects.requireNonNull;

import java.util.Map;

import org.apache.olingo.server.api.ServiceMetadata;

import com.sap.cds.reflect.CdsModel;

/**
 * An entity model is an encapsulation of CSN and EDMX model formats for the same CDS underlying model. Since one CDS
 * model can contain multiple services, which will be translated to multiple EDMX files, an EntityModel can have
 * multiple EDMX {@link ServiceMetadata} models, which are stored in a map with the full-qualified name of the service
 * as the key.
 */
public final class EntityModel {
    private final CdsModel csnModel;

    private final Map<String, ServiceMetadata> edmxMap;

    private EntityModel(CdsModel csnModel, Map<String, ServiceMetadata> edmxMap) {
        this.csnModel = requireNonNull(csnModel);
        // create a copy, so the map cannot be changed afterwards
        this.edmxMap = Map.copyOf(edmxMap);
    }

    static EntityModel of(CdsModel csmModel, Map<String, ServiceMetadata> edmxMap) {
        return new EntityModel(csmModel, edmxMap);
    }

    /**
     * Returns the CSN model.
     *
     * @deprecated use {@link #getCsnModel() instead}
     * @return CdsModel as CSNModel
     */
    @Deprecated(forRemoval = true)
    public CdsModel getCsn() {
        return getCsnModel();
    }

    /**
     * Returns the first EDMX {@link ServiceMetadata} model of the {@link #getEdmxes}.
     *
     * @deprecated use {@link #getEdmxMetadata()} instead
     * @return the first EDMX model or null
     */
    @Deprecated(forRemoval = true)
    public ServiceMetadata getEdmx() {
        return getEdmxMetadata();
    }

    /**
     * Returns one EDMX model.
     *
     * @deprecated use {@link #getEdmxMetadata(String)} instead
     * @param namespace full-qualified name of the service
     * @return one EDMX {@link ServiceMetadata} model which is mapped to a namespace or null
     */
    @Deprecated(forRemoval = true)
    public ServiceMetadata getEdmx(String namespace) {
        return getEdmxMetadata(namespace);
    }

    /**
     * Gets a map of all EDMX models which belongs to one service.
     *
     * @deprecated use {@link #getAllEdmxMetadata()} instead
     * @return a map of all EDMX {@link ServiceMetadata} models which belongs to one service
     */
    @Deprecated
    public Map<String, ServiceMetadata> getEdmxes() {
        return getAllEdmxMetadata();
    }

    /**
     * Returns the CSN model.
     *
     * @return CdsModel as CSNModel
     */
    public CdsModel getCsnModel() {
        return csnModel;
    }

    /**
     * Returns the first EDMX {@link ServiceMetadata} model of the {@link #getAllEdmxMetadata()}.
     *
     * @return the first EDMX model or null
     */
    public ServiceMetadata getEdmxMetadata() {
        return edmxMap.values().stream().findFirst().orElse(null);
    }

    /**
     * Returns one EDMX model.
     *
     * @param namespace full-qualified name of the service
     * @return one EDMX {@link ServiceMetadata} model which is mapped to a namespace or null
     */
    public ServiceMetadata getEdmxMetadata(String namespace) {
        return edmxMap.get(namespace);
    }

    /**
     * Gets a map of all EDMX models which belongs to one service.
     *
     * @return a map of all EDMX {@link ServiceMetadata} models which belongs to one service
     */
    public Map<String, ServiceMetadata> getAllEdmxMetadata() {
        return edmxMap;
    }
}
