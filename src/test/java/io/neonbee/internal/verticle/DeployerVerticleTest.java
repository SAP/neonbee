package io.neonbee.internal.verticle;

public class DeployerVerticleTest {

    // 1. Override DeployerVerticle to be able to catch events and futures
    // 2. Create NeonBeeModule and copy it into verticle folder
    // 3. check that DeployerVerticle has created models temp directory maybe reflections are necessary to get the
    // modelTempDir variable from NeonBeeModule
    // 4. check if verticle are deployed -> maybe reflections are necessary to get the
    // succeededDeployments variable from NeonBeeModule

    // 5. Delete the NeonBeeModule from verticle directory
    // 6. check if modelsTempDir is deleted
    // 7. check if verticle are undeployed.
}
