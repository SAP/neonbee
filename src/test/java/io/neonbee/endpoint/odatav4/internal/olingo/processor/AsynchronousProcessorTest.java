package io.neonbee.endpoint.odatav4.internal.olingo.processor;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.stream.Stream;

import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.api.ServiceMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;

class AsynchronousProcessorTest {

    private Context context;

    @BeforeEach
    void setUp() {
        // Initialize Vertx instance and Context once
        Vertx vertx = Vertx.vertx();
        context = vertx.getOrCreateContext();

        // Mock dependencies
        Promise<Void> processPromiseMock = Promise.promise();

        // Set up a real processing stack in the context
        Deque<List<Future<Void>>> processingStack = new ArrayDeque<>();
        processingStack.add(new ArrayList<>());
        processingStack.peek().add(processPromiseMock.future()); // Add the future to the stack

        context.put("processingStack", processingStack);

    }

    // Parameterized test method to verify the processor initialization
    @ParameterizedTest(name = "{index}: {0}")
    @MethodSource("processors")
    void testInitResetsSubProcessPromise(AsynchronousProcessor processor) {
        context.runOnContext(v -> {
            // Ensure getProcessPromise returns the same instance before and after init
            Promise<Void> firstProcessPromise = processor.getProcessPromise();
            assertSame(firstProcessPromise, processor.getProcessPromise(),
                    "The two promises should be the same instance.");

            // Mock OData and ServiceMetadata
            OData odataMock = mock(OData.class);
            ServiceMetadata serviceMetadataMock = mock(ServiceMetadata.class);

            // Call init to reset subProcessPromise
            processor.init(odataMock, serviceMetadataMock);
            assertSame(firstProcessPromise, processor.getProcessPromise(),
                    "The two promises should be the same instance.");
        });
    }

    // Method to provide processors for parameterized test
    private static Stream<AsynchronousProcessor> processors() {
        // Return a stream of different processor types for testing
        return Stream.of(
                new BatchProcessor(Vertx.vertx(), mock(RoutingContext.class), Promise.promise()),
                new CountEntityCollectionProcessor(Vertx.vertx(), mock(RoutingContext.class), Promise.promise()),
                new EntityProcessor(Vertx.vertx(), mock(RoutingContext.class), Promise.promise()),
                new PrimitiveProcessor(Vertx.vertx(), mock(RoutingContext.class), Promise.promise()));
    }
}
