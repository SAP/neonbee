package io.neonbee.internal;

import static io.neonbee.NeonBeeInstanceConfiguration.ClusterManager.HAZELCAST;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.neonbee.NeonBee;
import io.neonbee.NeonBeeExtension;
import io.neonbee.NeonBeeInstanceConfiguration;
import io.neonbee.internal.hazelcast.ReplicatedDataAccessor;

@Tag(LONG_RUNNING_TEST)
@Execution(ExecutionMode.SAME_THREAD)
@ExtendWith(NeonBeeExtension.class)
@Isolated
class SharedDataAccessorFactoryTest {

    @Test
    void testGetSharedDataAccessor(
            @NeonBeeInstanceConfiguration(
                    clustered = true,
                    activeProfiles = {},
                    clusterManager = HAZELCAST,
                    neonBeeConfig = "{ \"useReplicatedMaps\" : true }") NeonBee neonBee) {
        SharedDataAccessor sharedDataAccessor = new SharedDataAccessorFactory(neonBee.getVertx())
                .getSharedDataAccessor(SharedDataAccessorFactoryTest.class);
        assertInstanceOf(ReplicatedDataAccessor.class, sharedDataAccessor);
    }
}
