package io.neonbee.cluster;

import java.io.IOException;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * Tests behavior of neon bee in cluster. Multiple instances of neon bee will be created to maintain cluster. Two nodes
 * will deploy some verticle providing data. Third vnode will deploy verticle using the data of provider verticle. Test
 * scenarios contain cases with stop and restart of nodes with provider verticles during the access to consumer verticle
 * over http in the loop to provoke error situation. Also heartbeat and failure detection features of cluster manager
 * can be then tested.
 */
@Isolated("")
class MultipleInstancesTest {

    private NeonbeeClusterRunner clusterRunner;

    @BeforeAll
    static void setUp() throws IOException {
        NeonbeeClusterRunner.init();
    }

    @AfterAll
    static void tearDown() {
        NeonbeeClusterRunner.cleanUp();
    }

    @BeforeEach
    void beforeEach() {
        clusterRunner = new NeonbeeClusterRunner();
    }

    @Test
    void runInstances() throws IOException, InterruptedException {
        for (int i = 0; i < 3; i++) {
            clusterRunner.startProcess(i);
        }

        Thread.sleep(50000);
    }

    @AfterEach
    void afterEach() {
        clusterRunner.clean();
    }
}
