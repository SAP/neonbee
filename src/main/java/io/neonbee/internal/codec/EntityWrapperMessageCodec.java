package io.neonbee.internal.codec;

import static io.neonbee.entity.EntityModelManager.getBufferedModel;
import static io.neonbee.entity.EntityModelManager.getBufferedOData;
import static org.apache.olingo.commons.api.format.ContentType.APPLICATION_JSON;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;

import org.apache.olingo.commons.api.data.ContextURL;
import org.apache.olingo.commons.api.data.EntityCollection;
import org.apache.olingo.commons.api.edm.EdmEntitySet;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.server.api.ServiceMetadata;
import org.apache.olingo.server.api.deserializer.DeserializerException;
import org.apache.olingo.server.api.deserializer.DeserializerResult;
import org.apache.olingo.server.api.deserializer.ODataDeserializer;
import org.apache.olingo.server.api.serializer.EntityCollectionSerializerOptions;
import org.apache.olingo.server.api.serializer.ODataSerializer;
import org.apache.olingo.server.api.serializer.SerializerException;
import org.apache.olingo.server.api.serializer.SerializerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.neonbee.entity.EntityWrapper;
import io.neonbee.entity.ModelDefinitionHelper;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.MessageCodec;
import io.vertx.core.json.JsonObject;

public class EntityWrapperMessageCodec implements MessageCodec<EntityWrapper, EntityWrapper> {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

    private static final String ENTITY = "entity";

    private static final String NAME = "name";

    private static final String NAMESPACE = "namespace";

    private static final String ENTITY_TYPE = "entityType";

    private final Vertx vertx;

    /**
     * Creates a new EntityWrapperMessageCodec.
     *
     * @param vertx a Vert.x instance required to get the buffered model
     */
    public EntityWrapperMessageCodec(Vertx vertx) {
        this.vertx = vertx;
    }

    @Override
    public void encodeToWire(Buffer buffer, EntityWrapper entityWrapper) {
        FullQualifiedName entityTypeName = entityWrapper.getTypeName();
        ServiceMetadata serviceMetadata = getServiceMetadata(entityTypeName);
        if (serviceMetadata == null) {
            throw new IllegalStateException("Service metadata was not loaded yet for " + entityWrapper.getTypeName());
        }
        EdmEntityType entityType = serviceMetadata.getEdm().getEntityType(entityTypeName);
        EdmEntitySet entitySet = serviceMetadata.getEdm().getEntityContainer().getEntitySet(entityTypeName.getName());
        ContextURL contextUrl = ContextURL.with().entitySet(entitySet).build();
        EntityCollectionSerializerOptions.Builder optionsBuilder =
                EntityCollectionSerializerOptions.with().contextURL(contextUrl);
        EntityCollectionSerializerOptions options = optionsBuilder.build();

        try {
            EntityCollection entityCollection = new EntityCollection();
            entityCollection.getEntities().addAll(entityWrapper.getEntities());
            JsonObject json = new JsonObject().put(ENTITY_TYPE,
                    new JsonObject().put(NAMESPACE, entityTypeName.getNamespace()).put(NAME, entityTypeName.getName()));
            ODataSerializer odataSerializer = getBufferedOData().createSerializer(APPLICATION_JSON);
            SerializerResult odataSerializerResult =
                    odataSerializer.entityCollection(serviceMetadata, entityType, entityCollection, options);
            json.put(ENTITY, Buffer.buffer(odataSerializerResult.getContent().readAllBytes()).toString());
            buffer.appendString(json.toString());
        } catch (SerializerException | IOException e) {
            LOGGER.warn("Error while serializing entity wrapper.", e);
            throw new RuntimeException(e);
        }
    }

    private ServiceMetadata getServiceMetadata(FullQualifiedName entityTypeName) {
        return getBufferedModel(vertx, ModelDefinitionHelper.retrieveNamespace(entityTypeName.getNamespace()))
                .getEdmx(entityTypeName.getNamespace());
    }

    @Override
    public EntityWrapper decodeFromWire(int position, Buffer buffer) {
        JsonObject jsonObject = buffer.getBuffer(position, buffer.length()).toJsonObject();
        JsonObject entityTypeJsonObject = jsonObject.getJsonObject(ENTITY_TYPE);
        FullQualifiedName entityTypeName =
                new FullQualifiedName(entityTypeJsonObject.getString(NAMESPACE), entityTypeJsonObject.getString(NAME));
        ServiceMetadata serviceMetadata = getServiceMetadata(entityTypeName);
        EdmEntityType entityType = serviceMetadata.getEdm().getEntityType(entityTypeName);
        try {
            String payload = jsonObject.getString(ENTITY);
            ODataDeserializer odataDeserializer =
                    getBufferedOData().createDeserializer(APPLICATION_JSON, serviceMetadata);
            DeserializerResult odataDeserializerResult = odataDeserializer
                    .entityCollection(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)), entityType);
            return new EntityWrapper(entityTypeName, odataDeserializerResult.getEntityCollection().getEntities());
        } catch (DeserializerException e) {
            LOGGER.warn("Error while deserializing entity wrapper.", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public EntityWrapper transform(EntityWrapper entity) {
        return entity;
    }

    @Override
    public String name() {
        return "entitywrapper";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
