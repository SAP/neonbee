package io.neonbee.entity;

import java.util.Objects;
import java.util.Optional;

import com.google.common.annotations.VisibleForTesting;

/**
 * An annotation target consists of an entity name and an optional property.
 *
 * Examples: AnyModels.Service.Products/name or an entity AnyModels.Service.Products
 */
public class AnnotationTarget {
    private static final String SLASH = "/";

    private final String entityName;

    private final Optional<String> property;

    @VisibleForTesting
    AnnotationTarget(String target) {
        String[] components = target.split(SLASH, -1);
        this.entityName = components[0];
        this.property = components.length > 1 ? Optional.of(components[1]) : Optional.empty();
    }

    /**
     * Gets the entity name of the {@link AnnotationTarget}.
     *
     * @return The entity name of the {@link AnnotationTarget}
     */
    public String getEntityName() {
        return entityName;
    }

    /**
     * Gets the optional property of the {@link AnnotationTarget}.
     *
     * @return The value of the {@link AnnotationTarget}
     */
    public Optional<String> getProperty() {
        return property;
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityName, property);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof AnnotationTarget)) {
            return false;
        }

        AnnotationTarget other = (AnnotationTarget) obj;
        return Objects.equals(entityName, other.entityName) && Objects.equals(property, other.property);
    }

    @Override
    public String toString() {
        return "AnnotationProperty [entityName=" + entityName + ", property=" + property + "]";
    }
}
