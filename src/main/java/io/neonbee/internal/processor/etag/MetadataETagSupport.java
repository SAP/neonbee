package io.neonbee.internal.processor.etag;

import org.apache.olingo.server.api.etag.ServiceMetadataETagSupport;

public class MetadataETagSupport implements ServiceMetadataETagSupport {
    private final String metadataETag;

    private final String serviceDocumentETag;

    @SuppressWarnings("checkstyle:MissingJavadocMethod") // Don't know exactly what this is for
    public MetadataETagSupport(String metadataETag) {
        this.metadataETag = metadataETag;
        this.serviceDocumentETag = metadataETag;
    }

    @Override
    public String getMetadataETag() {
        return metadataETag;
    }

    @Override
    public String getServiceDocumentETag() {
        return serviceDocumentETag;
    }
}
