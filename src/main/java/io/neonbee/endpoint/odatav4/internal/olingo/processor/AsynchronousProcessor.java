package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static java.util.Objects.requireNonNull;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.processor.Processor;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

public abstract class AsynchronousProcessor implements Processor {
    private static final String PROCESSING_STACK = "processingStack";
    private static final Promise<Void> INITIAL_STATE = null;

    protected Vertx vertx;

    protected RoutingContext routingContext;

    private final Promise<Void> processPromise;

    private Promise<Void> subProcessPromise;

    AsynchronousProcessor(Vertx vertx, RoutingContext routingContext, Promise<Void> processPromise) {
        this.vertx = vertx;
        this.routingContext = routingContext;
        this.processPromise = processPromise;
    }

    /**
     * Returns either the global processPromise, created in the endpoint, to finish processing for the OData request. In
     * case this request is called in batch processing, it'll return a new sub-processPromise and store the future on
     * the processingStack.
     *
     * <b>Important:</b> Should be called directly when entering the Olingo method once and stored in a reference.
     *
     * @return the processPromise
     */
    public Promise<Void> getProcessPromise() {
        if (subProcessPromise != null) {
            // never create a second subProcessPromise, as it'll never resolve
            return subProcessPromise;
        } else if (processingStack().isEmpty()) {
            // return the main endpoint processPromise if not in batch processing, or in case this is batch
            // processing, but the first call (by the BatchProcessor) to enterBatchProcessing
            return processPromise;
        } else {
            // we are in batch processing (somebody has called enterBatchProcessing before and there is an element
            // on the processingStack). Thus create a new subProcessPromise and put it onto the stack
            subProcessPromise = Promise.promise();
            requireNonNull(processingStack().peek(), "head of deque is empty").add(subProcessPromise.future());
            return subProcessPromise;
        }
    }

    /**
     * The enterBatchProcessing method will return a processPromise (either the global one, or a sub-processPromise) and
     * push a new processing stack to the processingStack. After processing the returned future should be resolved, as
     * soon as all elements of the top processingStack resolve.
     *
     * @return a processPromise to resolve, when this layer of the processingStack finish
     */
    public Promise<Void> enterBatchProcessing() {
        Promise<Void> processPromise = getProcessPromise();
        processingStack().push(new ArrayList<>());
        return processPromise;
    }

    /**
     * This will pop the top element from he processingStack, as soon as all futures of the list returned resolve, the
     * processFuture returned by enterBatchProcessing should be resolved.
     *
     * @return a list of processFutures to wait for being resolved
     */
    public List<Future<Void>> wrapUpBatchProcessing() {
        return processingStack().pop();
    }

    private static Deque<List<Future<Void>>> processingStack() {
        Context context = Vertx.currentContext();
        Deque<List<Future<Void>>> processingStack = context.get(PROCESSING_STACK);
        if (Objects.isNull(processingStack)) {
            context.put(PROCESSING_STACK, processingStack = new ArrayDeque<>());
        }
        return processingStack;
    }

    /**
     * Resets the {@code subProcessPromise} to {@code null}.
     * <p>
     * This method is used to release any references held by the {@code subProcessPromise}, ensuring that the processor
     * starts with a clean state. It is intentionally set to {@code null} as part of the processor's lifecycle
     * management.
     */
    private void resetSubProcessPromise() {
        subProcessPromise = INITIAL_STATE; // Intentional reset to null
    }

    /**
     * Initializes the processor by resetting its internal state.
     * <p>
     * This method is called during the processor's initialization phase. It uses {@link #resetSubProcessPromise()} to
     * ensure the {@code subProcessPromise} is cleared, preparing the processor for a new lifecycle.
     *
     * @param odata           the OData instance for this processor
     * @param serviceMetadata the metadata for the OData service
     */
    @Override
    public void init(OData odata, ServiceMetadata serviceMetadata) {
        resetSubProcessPromise();
    }
}
