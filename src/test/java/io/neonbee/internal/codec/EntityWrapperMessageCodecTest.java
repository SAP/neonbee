package io.neonbee.internal.codec;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.util.concurrent.TimeUnit;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import io.neonbee.entity.EntityModelManager;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.test.base.NeonBeeTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.junit5.Timeout;
import io.vertx.junit5.VertxTestContext;

class EntityWrapperMessageCodecTest extends NeonBeeTestBase {
    private final Entity entity = new Entity().addProperty(new Property(null, "name", ValueType.PRIMITIVE, "NAME"))
            .addProperty(new Property(null, "description", ValueType.PRIMITIVE, "DESCRIPTION"))
            .addProperty(new Property(null, "ID", ValueType.PRIMITIVE, "ID"));

    private final EntityWrapper wrapper = new EntityWrapper("io.neonbee.codec.CodecService.TestUsers", entity);

    private EntityWrapperMessageCodec codec;

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        return WorkingDirectoryBuilder.standard().addModel(TEST_RESOURCES.resolveRelated("CodecService.csn"));
    }

    @BeforeEach
    void setUp() {
        codec = new EntityWrapperMessageCodec(getNeonBee().getVertx());
    }

    @Test
    @DisplayName("Should serialize and deserialize an EntityWrapper correctly.")
    @Timeout(value = 2, timeUnit = TimeUnit.SECONDS)
    void encodeDecode(VertxTestContext testContext) {
        EntityModelManager.reloadModels(getNeonBee().getVertx()).<Void>compose(map -> {
            Buffer buffer = Buffer.buffer();
            codec.encodeToWire(buffer, wrapper);
            EntityWrapper decodeFromWire = codec.decodeFromWire(0, buffer);
            assertThat(decodeFromWire.getTypeName().getNamespace()).isEqualTo("io.neonbee.codec.CodecService");
            assertThat(decodeFromWire.getTypeName().getName()).isEqualTo("TestUsers");
            Entity decodedEntity = decodeFromWire.getEntity();
            assertThat(decodedEntity.getProperty("name").getValue()).isEqualTo("NAME");
            assertThat(decodedEntity.getProperty("description").getValue()).isEqualTo("DESCRIPTION");
            assertThat(decodedEntity.getProperty("ID").getValue()).isEqualTo("ID");

            return Future.succeededFuture(null);
        }).onComplete(testContext.succeedingThenComplete());
    }

    @Test
    @DisplayName("Transform should return the same object")
    void testTransform() {
        assertThat(codec.transform(wrapper)).isSameInstanceAs(wrapper);
    }

    @Test
    void testSystemCodecID() {
        assertThat(codec.systemCodecID()).isEqualTo(-1);
    }

    @Test
    void testName() {
        assertThat(codec.name()).isEqualTo("entitywrapper");
    }
}
