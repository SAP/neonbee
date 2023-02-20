package io.neonbee.test.base;

import org.apache.olingo.commons.api.edm.FullQualifiedName;

/**
 * This class can be used to address the metadata of OData services that expose their entity model according to
 * [OData-CSDLJSON] or [OData-CSDLXML] at the metadata URL.
 */
public class ODataMetadataRequest extends AbstractODataRequest<ODataMetadataRequest> {
    public ODataMetadataRequest(FullQualifiedName entity) {
        super(entity);
    }

    @Override
    protected ODataMetadataRequest self() {
        return this;
    }

    @Override
    protected String getUri() {
        return getUriNamespacePath() + "$metadata";
    }
}
