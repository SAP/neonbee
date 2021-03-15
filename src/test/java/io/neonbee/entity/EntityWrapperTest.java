package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static org.junit.Assert.assertThat;

import java.util.concurrent.TimeUnit;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class EntityWrapperTest extends NeonBeeTestBase {
    private static final EntityWrapper TEST_USER_WRAPPER =
            new EntityWrapper("io.neonbee.test2.TestService2Users.TestUsers", createTestUser());

    private static final Buffer TEST_USER_WRAPPER_SERIALIZED = Buffer
            .buffer("{\"entityType\":{\"namespace\":\"io.neonbee.test2.TestService2Users\",\"name\":\"TestUsers\"}," //
                    + "\"entity\":\"{\\\"@odata.context\\\":\\\"$metadata#TestUsers\\\",\\\"@odata.metadataEtag\\\":\\\"\\\\\\\"2f85d83fa1f93164cf4d3252ca72023e\\\\\\\"\\\",\\\"value\\\":[{\\\"ID\\\":\\\"ID\\\",\\\"name\\\":\\\"NAME\\\",\\\"description\\\":\\\"DESCRIPTION\\\"}]}\"}");

    private static Entity createTestUser() {
        Entity testUser = new Entity().addProperty(new Property("Edm.String", "name", ValueType.PRIMITIVE, "NAME"))
                .addProperty(new Property("Edm.String", "description", ValueType.PRIMITIVE, "DESCRIPTION"))
                .addProperty(new Property("Edm.String", "ID", ValueType.PRIMITIVE, "ID"));
        testUser.setType("io.neonbee.test2.TestService2Users.TestUsers");
        return testUser;
    }

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return WorkingDirectoryBuilder.standard().addModel(TEST_RESOURCES.resolveRelated("TestService2.csn"));
    }

    @Test
    @DisplayName("Check if equals works as expected")
    void testEquals() {
        EntityWrapper fooBarEmptyString = new EntityWrapper("Foo.Bar", (Entity) null);
        EntityWrapper fooBarEmptyFQN = new EntityWrapper(new FullQualifiedName("Foo", "Bar"), (Entity) null);
        EntityWrapper hodorHodorEmptyFQN = new EntityWrapper(new FullQualifiedName("Hodor", "Hodor"), (Entity) null);

        assertThat(fooBarEmptyString).isEqualTo(fooBarEmptyFQN);
        assertThat(hodorHodorEmptyFQN).isNotEqualTo(fooBarEmptyString);
        assertThat(hodorHodorEmptyFQN).isNotEqualTo(fooBarEmptyFQN);

        Entity hodor = new Entity().addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Hodor"));
        Entity sam = new Entity().addProperty(new Property(null, "Name", ValueType.PRIMITIVE, "Sam"));
        EntityWrapper firstNamesHodor = new EntityWrapper("First.Name", hodor);
        EntityWrapper firstNamesSam = new EntityWrapper("First.Name", sam);

        assertThat(firstNamesHodor).isNotEqualTo(firstNamesSam);
        assertThat(firstNamesHodor).isEqualTo(new EntityWrapper("First.Name", hodor));
        assertThat(firstNamesSam).isNotEqualTo(new EntityWrapper("Hodor.Hodor", sam));
    }

    @Test
    @DisplayName("Check if toBuffer works as expected")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testToBuffer(VertxTestContext testContext) {
        EntityModelManager.reloadModels(getNeonBee().getVertx()).onComplete(testContext.succeeding(map -> {
            testContext.verify(() -> {
                Buffer buffer = TEST_USER_WRAPPER.toBuffer(getNeonBee().getVertx());
                assertThat(buffer.toJsonObject()).isEqualTo(TEST_USER_WRAPPER_SERIALIZED.toJsonObject());
                testContext.completeNow();
            });
        }));
    }

    @Test
    @DisplayName("Check if fromBuffer works as expected")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void testFromBuffer(VertxTestContext testContext) {
        EntityModelManager.reloadModels(getNeonBee().getVertx()).onComplete(testContext.succeeding(map -> {
            testContext.verify(() -> {
                EntityWrapper decodedWrapper =
                        EntityWrapper.fromBuffer(getNeonBee().getVertx(), TEST_USER_WRAPPER_SERIALIZED);
                // assertThat(decodedWrapper).isEqualTo(TEST_USER_WRAPPER); is not possible because Olingo compares
                // entities wrong. Entity.equals is comparing properties with List.equals, but if the properties are
                // loaded in a different order this approach fails.
                // If this is a bug in Olingo, this test is fine. If this is an expected behavior in Olingo, then the
                // deserialization is wrong.
                assertThat(decodedWrapper.getTypeName()).isEqualTo(TEST_USER_WRAPPER.getTypeName());
                assertThat(decodedWrapper.getEntity().getProperty("name").getValue())
                        .isEqualTo(TEST_USER_WRAPPER.getEntity().getProperty("name").getValue());
                assertThat(decodedWrapper.getEntity().getProperty("description").getValue())
                        .isEqualTo(TEST_USER_WRAPPER.getEntity().getProperty("description").getValue());
                assertThat(decodedWrapper.getEntity().getProperty("ID").getValue())
                        .isEqualTo(TEST_USER_WRAPPER.getEntity().getProperty("ID").getValue());
                testContext.completeNow();
            });
        }));
    }
}
