package io.neonbee.entity;

import java.util.Collections;
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
    private final CdsModel csn;

    private final Map<String, ServiceMetadata> edmxMap;

    private EntityModel(CdsModel csn, Map<String, ServiceMetadata> edmxMap) {
        this.csn = csn;
        this.edmxMap = edmxMap;
    }

    static EntityModel of(CdsModel csn, Map<String, ServiceMetadata> edmxMap) {
        return new EntityModel(csn, edmxMap);
    }

    /**
     * Returns the CSN model.
     *
     * @return CdsModel as CSNModel
     */
    public CdsModel getCsn() {
        return csn;
    }

    /**
     * Returns the first EDMX {@link ServiceMetadata} model of the {@link #edmxMap}.
     *
     * @return the first EDMX model
     */
    public ServiceMetadata getEdmx() {
        return edmxMap.values().stream().findFirst().orElse(null);
    }

    /**
     * Returns one EDMX model.
     *
     * @param namespace full-qualified name of the service
     * @return Returns one EDMX {@link ServiceMetadata} model which is mapped to a namespace
     */
    public ServiceMetadata getEdmx(String namespace) {
        return edmxMap.get(namespace);
    }

    /**
     * Gets a map of all EDMX models which belongs to one service.
     *
     * @return a map of all EDMX {@link ServiceMetadata} models which belongs to one service
     */
    public Map<String, ServiceMetadata> getEdmxes() {
        return Collections.unmodifiableMap(edmxMap);
    }
}
