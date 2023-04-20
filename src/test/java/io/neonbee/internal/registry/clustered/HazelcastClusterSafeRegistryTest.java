package io.neonbee.internal.registry.clustered;

//@ExtendWith(VertxExtension.class)
class HazelcastClusterSafeRegistryTest {

//    @Test
//    void register(Vertx vertx, VertxTestContext context) {
//        testDelayedMethodExecution(vertx, context, "7be56c89-9b3d-452b-8009-cc801a1bcc35",
//                registry -> registry.register("key", "value"));
//    }
//
//    @Test
//    void unregister(Vertx vertx, VertxTestContext context) {
//        testDelayedMethodExecution(vertx, context, "43ebcf4f-0416-4016-8aa8-a55a2c20338f",
//                registry -> registry.unregister("key", "value"));
//    }
//
//    @Test
//    void unregisterNode(Vertx vertx, VertxTestContext context) {
//        testDelayedMethodExecution(vertx, context, "8d670ef8-6eaa-44d5-b589-ac72fce9e606",
//                registry -> registry.unregisterNode("ClusterNodeID:0"));
//    }
//
//    private static void testDelayedMethodExecution(Vertx vertx, VertxTestContext context, String uuid,
//            Function<HazelcastClusterSafeRegistry, Future<Void>> method) {
//        UUID migrationListenerUuid = UUID.fromString(uuid);
//
//        PartitionService partitionServiceMock = mock(PartitionService.class);
//        when(partitionServiceMock.isClusterSafe()).thenReturn(Boolean.FALSE);
//        when(partitionServiceMock.addMigrationListener(any()))
//                .thenReturn(migrationListenerUuid);
//
//        HazelcastClusterSafeRegistry registry = spy(
//                new HazelcastClusterSafeRegistry(vertx, "unitTestRegistry", partitionServiceMock));
//        doReturn("ClusterNodeID:0").when(registry).getClusterNodeId();
//
//        method.apply(registry)
//                .onSuccess(unused -> context.verify(() -> {
//                    // ensure that the MigrationListener is removed
//                    verify(partitionServiceMock, times(1)).removeMigrationListener(migrationListenerUuid);
//                    context.completeNow();
//                }))
//                .onFailure(context::failNow);
//
//        // get the MigrationListener
//        ArgumentCaptor<MigrationListener> captor = ArgumentCaptor.forClass(MigrationListener.class);
//        verify(partitionServiceMock).addMigrationListener(captor.capture());
//        MigrationListener migrationListener = captor.getValue();
//
//        // send the event, that the partition replica migration is completed
//        ReplicaMigrationEvent event = mock(ReplicaMigrationEvent.class);
//        when(event.getElapsedTime()).thenReturn(10L);
//        when(event.isSuccess()).thenReturn(Boolean.TRUE);
//        migrationListener.replicaMigrationCompleted(event);
//    }
}
