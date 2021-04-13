package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static io.neonbee.internal.helper.AsyncHelper.allComposite;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.commons.api.http.HttpHeader;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.ODataLibraryException;
import org.apache.olingo.server.api.ODataRequest;
import org.apache.olingo.server.api.ODataResponse;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.batch.BatchFacade;
import org.apache.olingo.server.api.deserializer.batch.BatchOptions;
import org.apache.olingo.server.api.deserializer.batch.BatchRequestPart;
import org.apache.olingo.server.api.deserializer.batch.ODataResponsePart;
import org.apache.olingo.server.api.serializer.BatchSerializerException;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

@edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "NM_SAME_SIMPLE_NAME_AS_INTERFACE",
        justification = "Common practice in Olingo to name the implementation of the processor same as the interface")
public class BatchProcessor extends AsynchronousProcessor
        implements org.apache.olingo.server.api.processor.BatchProcessor {
    private OData odata;

    /**
     * Creates a new BatchProcessor.
     *
     * @param vertx          the related Vert.x instance
     * @param routingContext the routingContext of the related request
     * @param processPromise the promise to complete when data has been fetched
     */
    public BatchProcessor(Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        super(vertx, routingContext, processPromise);
    }

    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        this.odata = odata;
    }

    @Override
    public void processBatch(BatchFacade facade, ODataRequest request, ODataResponse response)
            throws ODataApplicationException, ODataLibraryException {
        String boundary = facade.extractBoundaryFromContentType(request.getHeader(HttpHeader.CONTENT_TYPE));
        BatchOptions options = BatchOptions.with().rawBaseUri(request.getRawBaseUri())
                .rawServiceResolutionUri(request.getRawServiceResolutionUri()).build();
        List<BatchRequestPart> requestParts =
                odata.createFixedFormatDeserializer().parseBatchRequest(request.getBody(), boundary, options);

        // by entering batch processing here, we'll enter a new processing phase
        Promise<Void> processPromise = enterBatchProcessing();

        List<ODataResponsePart> responseParts = new ArrayList<>();
        for (BatchRequestPart part : requestParts) {
            responseParts.add(facade.handleBatchRequest(part));
        }

        // wrap up the batch processing here, which will complete the current processing phase
        allComposite(wrapUpBatchProcessing()).onComplete(resultHandler -> {
            if (resultHandler.failed()) {
                processPromise.fail(resultHandler.cause());
                return;
            }

            try {
                String responseBoundary = "batch_" + UUID.randomUUID().toString();
                InputStream responseContent =
                        odata.createFixedFormatSerializer().batchResponse(responseParts, responseBoundary);

                response.setHeader(HttpHeader.CONTENT_TYPE,
                        ContentType.MULTIPART_MIXED + ";boundary=" + responseBoundary);
                response.setContent(responseContent);
                response.setStatusCode(HttpStatusCode.ACCEPTED.getStatusCode());

                processPromise.complete();
            } catch (BatchSerializerException e) {
                processPromise.fail(e);
            }
        });
    }

    /**
     * NOTE: NeonBee does NOT support processing / rolling-back change sets so far! This method will simply execute all
     * ODataRequests consecutively. It would not make sense to fail / roll-back the transaction in this method, as
     * facade.handleODataRequest will return immediately (as the request is processed) asynchronous. This is why the
     * transaction handling has to take place in a different place more likely.
     */
    @Override
    public ODataResponsePart processChangeSet(BatchFacade facade, List<ODataRequest> requests)
            throws ODataApplicationException, ODataLibraryException {
        List<ODataResponse> responses = new ArrayList<>();

        for (ODataRequest request : requests) {
            ODataResponse response = facade.handleODataRequest(request);

            int statusCode = response.getStatusCode();
            if (statusCode < BAD_REQUEST.code()) {
                responses.add(response);
            } else {
                return new ODataResponsePart(response, false);
            }
        }

        return new ODataResponsePart(responses, true);
    }
}
