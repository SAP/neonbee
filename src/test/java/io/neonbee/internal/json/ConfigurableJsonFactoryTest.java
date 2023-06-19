package io.neonbee.internal.json;

import static com.fasterxml.jackson.core.StreamReadConstraints.DEFAULT_MAX_STRING_LEN;
import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.helper.StringHelper.EMPTY;
import static io.neonbee.test.helper.ReflectionHelper.setValueOfPrivateField;
import static io.neonbee.test.helper.ReflectionHelper.setValueOfPrivateStaticField;
import static org.junit.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.junit.jupiter.api.parallel.Isolated;
import org.mockito.ArgumentCaptor;
import org.slf4j.Logger;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.neonbee.config.NeonBeeConfig;
import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.data.DataRequest;
import io.neonbee.data.DataVerticle;
import io.neonbee.internal.json.ConfigurableJsonFactory.ConfigurableJacksonCodec;
import io.neonbee.test.base.DataVerticleTestBase;
import io.neonbee.test.helper.WorkingDirectoryBuilder;
import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.jackson.DatabindCodec;
import io.vertx.core.json.jackson.JacksonCodec;
import io.vertx.core.json.jackson.JacksonFactory;
import io.vertx.junit5.VertxTestContext;

@Isolated("The maximum string size is set on the JsonCodec that is injected to Vert.x using SPI, thus it is a global setting, that may influence other tests")
class ConfigurableJsonFactoryTest extends DataVerticleTestBase {
    static final int SMALLER_JSON_LENGTH = 5000;

    @Override
    protected WorkingDirectoryBuilder provideWorkingDirectoryBuilder(TestInfo testInfo, VertxTestContext testContext) {
        WorkingDirectoryBuilder builder = super.provideWorkingDirectoryBuilder(testInfo, testContext);

        switch (testInfo.getTestMethod().map(Method::getName).orElse(EMPTY)) {
        case "testDefaultJsonMaxStringLength":
            return builder;
        default:
            NeonBeeConfig config = new NeonBeeConfig();
            config.setJsonMaxStringSize(SMALLER_JSON_LENGTH);
            return builder.setNeonBeeConfig(config);
        }
    }

    @AfterEach
    void resetToDefault() {
        ConfigurableJsonFactory.CODEC.setMaxStringLength(DEFAULT_MAX_STRING_LEN);
    }

    @Test
    @DisplayName("Databind module registration should be ignored if no Databind Codec is used")
    void testIgnoreModuleRegistrationWithoutDatabindCodec() throws Throwable {
        Logger mockedLogger = mock(Logger.class);
        VertxTestContext.ExecutionBlock resetLogger =
                setValueOfPrivateStaticField(ConfigurableJsonFactory.class, "LOGGER", mockedLogger);
        VertxTestContext.ExecutionBlock resetConfigurableJsonFactoryCodec =
                setValueOfPrivateStaticField(ConfigurableJsonFactory.class, "CODEC", new ConfigurableJacksonCodec());
        VertxTestContext.ExecutionBlock resetJacksonFactoryCodec =
                setValueOfPrivateStaticField(JacksonFactory.class, "CODEC", new JacksonCodec());

        try {
            ConfigurableJsonFactory.CODEC.registerDeserializationModule(null);
            ConfigurableJsonFactory.CODEC.registerSerializationModule(null);
            ArgumentCaptor<String> logMsgCaptor = ArgumentCaptor.forClass(String.class);
            verify(mockedLogger, times(2)).warn(logMsgCaptor.capture());
            List<String> messages = logMsgCaptor.getAllValues();

            assertThat(messages).contains(
                    "JsonCodec is no instance of DatabindCodec, registerDeserializationModule will be ignored.");
            assertThat(messages).contains(
                    "JsonCodec is no instance of DatabindCodec, registerSerializationModule will be ignored.");
        } finally {
            resetLogger.apply();
            resetConfigurableJsonFactoryCodec.apply();
            resetJacksonFactoryCodec.apply();
        }
    }

    @Test
    @DisplayName("Databind module registration should happen if Databind Codec is used")
    void testModuleRegistrationWithDatabindCodec() throws Throwable {
        ObjectMapper serializationMapperMock = mock(ObjectMapper.class);
        ObjectMapper serializationPrettyMapperMock = mock(ObjectMapper.class);
        ObjectMapper deserializationMapperMock = mock(ObjectMapper.class);

        VertxTestContext.ExecutionBlock resetSerializationMapper =
                setValueOfPrivateStaticField(DatabindCodec.class, "mapper", serializationMapperMock);
        VertxTestContext.ExecutionBlock resetSerializationPrettyMapper =
                setValueOfPrivateStaticField(DatabindCodec.class, "prettyMapper", serializationPrettyMapperMock);
        VertxTestContext.ExecutionBlock resetDeserializationMapper =
                setValueOfPrivateField(ConfigurableJsonFactory.CODEC, "mapper", deserializationMapperMock);

        try {
            Module mockedModule = mock(Module.class, RETURNS_SMART_NULLS);

            ConfigurableJsonFactory.CODEC.registerSerializationModule(mockedModule);
            verify(serializationMapperMock).registerModule(mockedModule);
            verify(serializationPrettyMapperMock).registerModule(mockedModule);

            ConfigurableJsonFactory.CODEC.registerDeserializationModule(mockedModule);
            verify(deserializationMapperMock).registerModule(mockedModule);
        } finally {
            resetSerializationMapper.apply();
            resetSerializationPrettyMapper.apply();
            resetDeserializationMapper.apply();
        }
    }

    @Test
    @DisplayName("By default NeonBee should comply to Jacksons DEFAULT_MAX_STRING_LEN")
    void testDefaultJsonMaxStringLength() {
        assertDoesNotThrow(() -> createJsonObjectWithLength(SMALLER_JSON_LENGTH, true));
        assertDoesNotThrow(() -> createJsonObjectWithLengthUnder(DEFAULT_MAX_STRING_LEN, true));
        expectJsonDecodeException(() -> createJsonObjectWithLengthOver(DEFAULT_MAX_STRING_LEN, true));
    }

    @Test
    @DisplayName("NeonBee should accept changing the JSON maxStringLength (e.g. to a smaller value)")
    void testSmallerJsonMaxStringLength() {
        assertDoesNotThrow(() -> createJsonObjectWithLength(SMALLER_JSON_LENGTH, true));
        expectJsonDecodeException(() -> createJsonObjectWithLengthOver(SMALLER_JSON_LENGTH, true));
    }

    @Test
    @DisplayName("Using a JSON in the target verticle that exceeds the maxStringLength should raise a descriptive error message")
    void testRetrieveDescriptiveJsonError(VertxTestContext testContext) {
        DataVerticle<JsonObject> testVerticle = new DataVerticle<>() {
            @Override
            public String getName() {
                return "TestVerticle";
            }

            @Override
            public Future<JsonObject> retrieveData(DataQuery query, DataContext context) {
                return Future.succeededFuture(createJsonObjectWithLengthOver(SMALLER_JSON_LENGTH, true));
            }
        };

        deployVerticle(testVerticle)
                .map(any -> new DataRequest(testVerticle.getName()))
                .compose(this::requestData)
                .onComplete(testContext.failing(throwable -> {
                    assertStringLengthExceptionMessage(throwable);
                    testContext.completeNow();
                }));
    }

    static JsonObject createJsonObjectWithLengthUnder(int length, boolean fromString) {
        return createJsonObjectWithLength(length - 1000, fromString);
    }

    static JsonObject createJsonObjectWithLengthOver(int length, boolean fromString) {
        return createJsonObjectWithLength(length + 1000, fromString);
    }

    static JsonObject createJsonObjectWithLength(int length, boolean fromString) {
        String begin = "{\"a\":\"";
        String end = "\"}";
        String value = "a".repeat(
                Math.max(length - begin.length() - end.length(), 1));
        if (fromString) {
            return new JsonObject(begin + value + end);
        } else {
            return new JsonObject().put("a", value);
        }
    }

    static DecodeException expectJsonDecodeException(Supplier<JsonObject> objectSupplier) {
        DecodeException e = assertThrows(DecodeException.class, () -> objectSupplier.get());
        assertStringLengthExceptionMessage(e);
        return e;
    }

    static void assertStringLengthExceptionMessage(Throwable e) {
        assertThat(e).hasMessageThat()
                .containsMatch("String length \\(\\d+\\) exceeds the maximum length \\(\\d+\\)");
    }
}
