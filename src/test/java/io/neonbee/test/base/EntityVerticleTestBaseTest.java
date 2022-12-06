package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.nio.file.Path;
import java.util.List;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.neonbee.entity.EntityVerticle;
import io.vertx.junit5.Checkpoint;
import io.vertx.junit5.VertxTestContext;

class EntityVerticleTestBaseTest extends EntityVerticleTestBase {

    static final FullQualifiedName CUSTOMER = new FullQualifiedName("Service", "Customer");

    static final FullQualifiedName FINANCE = new FullQualifiedName("Service", "Finance");

    final EntityVerticle firstCustomerEV = createDummyEntityVerticle(CUSTOMER).withStaticResponse(new Entity());

    final EntityVerticle secondCustomerEV = createDummyEntityVerticle(CUSTOMER).withStaticResponse(new Entity());

    final EntityVerticle financeEV = createDummyEntityVerticle(FINANCE).withStaticResponse(new Entity());

    @Override
    protected List<Path> provideEntityModels() {
        return List.of(TEST_RESOURCES.resolve("io/neonbee/entity/TestService1.csn"));
    }

    @Test
    @DisplayName("test that the getName method returns unique names for dummy EntityVerticles")
    void testDummyEntityVerticleGetName() {
        assertThat(firstCustomerEV.getName()).isNotEqualTo(secondCustomerEV.getName());
        assertThat(financeEV.getName()).isNotEqualTo(firstCustomerEV.getName());
        assertThat(financeEV.getName()).isNotEqualTo(secondCustomerEV.getName());
    }

    @Test
    @DisplayName("test the deployment of two dummy EntityVerticles serving the same entity")
    void testDeployDummyEntityVerticlesTwice(VertxTestContext context) {
        deployVerticle(firstCustomerEV).compose(x -> deployVerticle(secondCustomerEV))
                .compose(x -> requestEntity(CUSTOMER)).onSuccess(entityWrapper -> context.verify(() -> {
                    assertThat(entityWrapper.getEntities()).hasSize(2);
                    context.completeNow();
                })).onFailure(context::failNow);
    }

    @Test
    @DisplayName("test that dummy EntityVerticles are deployed correctly")
    void testDeployDummyEntityVerticles(VertxTestContext context) {
        Checkpoint checkpoint = context.checkpoint(2);

        deployVerticle(firstCustomerEV).compose(x -> deployVerticle(financeEV)).compose(x -> requestEntity(CUSTOMER))
                .onSuccess(entityWrapper -> context.verify(() -> {
                    assertThat(entityWrapper.getTypeName().getFullQualifiedNameAsString())
                            .isEqualTo(CUSTOMER.getFullQualifiedNameAsString());
                    checkpoint.flag();
                })).compose(x -> requestEntity(CUSTOMER)).onSuccess(entityWrapper -> context.verify(() -> {
                    assertThat(entityWrapper.getTypeName().getFullQualifiedNameAsString())
                            .isEqualTo(CUSTOMER.getFullQualifiedNameAsString());
                    checkpoint.flag();
                })).onFailure(context::failNow);
    }
}
