package io.neonbee.entity;

import static io.neonbee.internal.helper.CollectionHelper.mutableCopyOf;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import java.util.List;
import java.util.Objects;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.internal.codec.EntityWrapperMessageCodec;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;

public class EntityWrapper {
    private FullQualifiedName typeName;

    private List<Entity> entities;

    /**
     * EntityWrapper for one entity and its type.
     *
     * @param typeNamespaceAndName The full qualified namespace and name of the type
     * @param entity               Any entity
     */
    public EntityWrapper(String typeNamespaceAndName, Entity entity) {
        this(typeNamespaceAndName, entity != null ? singletonList(entity) : emptyList());
    }

    /**
     * EntityWrapper for a any number of entities and its types.
     *
     * @param typeNamespaceAndName The full qualified namespace and name of the type
     * @param entities             Any number of entities
     */
    public EntityWrapper(String typeNamespaceAndName, List<Entity> entities) {
        this(new FullQualifiedName(typeNamespaceAndName), entities);
    }

    /**
     * EntityWrapper for one entity and its type.
     *
     * @param typeName The full qualified type name
     * @param entity   Any entity
     */
    public EntityWrapper(FullQualifiedName typeName, Entity entity) {
        this(typeName, entity != null ? singletonList(entity) : emptyList());
    }

    /**
     * EntityWrapper for a any number of entities and its types.
     *
     * @param typeName The full qualified type name
     * @param entities Any number of entities
     */
    public EntityWrapper(FullQualifiedName typeName, List<Entity> entities) {
        this.typeName = typeName;
        this.entities = mutableCopyOf(entities);
    }

    /**
     * Returns the full qualified type name of the entities in this entity wrapper.
     *
     * @return the full qualified type name
     */
    public FullQualifiedName getTypeName() {
        return typeName;
    }

    /**
     * Returns the first entity of this entity wrapper.
     *
     * @return the first entity, or null in case there is none
     */
    public Entity getEntity() {
        return entities.stream().findFirst().orElse(null);
    }

    /**
     * Returns a list of entities of this entity wrapper.
     *
     * @return a list of entities
     */
    public List<Entity> getEntities() {
        return entities;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entities, typeName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        } else if (!(obj instanceof EntityWrapper)) {
            return false;
        }

        EntityWrapper other = (EntityWrapper) obj;
        return Objects.equals(entities, other.entities) && Objects.equals(typeName, other.typeName);
    }

    /**
     * Converts an {@link EntityWrapper} to a {@link Buffer}.
     *
     * A Vertx instance with loaded schema description for the entity must be provided to this method, since the schema
     * metadata is required during the serialization (conversion to buffer) process.
     *
     * @param vertx vertx, in which the schemas are loaded
     * @return a buffer representation of entity wrapper
     */
    public Buffer toBuffer(Vertx vertx) {
        EntityWrapperMessageCodec codec = new EntityWrapperMessageCodec(vertx);
        Buffer buffer = Buffer.buffer();
        codec.encodeToWire(buffer, this);
        return buffer;
    }

    /**
     * Converts an {@link Buffer} to a {@link EntityWrapper}
     *
     * A Vertx instance with loaded schema description for the entity must be provided to this method, since the schema
     * metadata is required during the deserialization (conversion to buffer) process.
     *
     * @param vertx  vertx, in which the schemas are loaded
     * @param buffer buffer containing the representation
     * @return an entity wrapper
     */
    public static EntityWrapper fromBuffer(Vertx vertx, Buffer buffer) {
        EntityWrapperMessageCodec codec = new EntityWrapperMessageCodec(vertx);
        return codec.decodeFromWire(0, buffer);
    }
}
