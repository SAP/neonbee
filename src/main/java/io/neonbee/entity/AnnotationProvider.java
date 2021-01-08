package io.neonbee.entity;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.provider.CsdlAnnotations;

import com.google.common.annotations.VisibleForTesting;

/**
 * An entity annotation provider encapsulates the annotation of a target schema namespace.
 * <p>
 * Useful links to understand what OData vocabularies are: (1)
 * http://docs.oasis-open.org/odata/odata-vocabularies/v4.0/odata-vocabularies-v4.0.html (2)
 * https://www.odata.org/blog/vocabularies/
 */
public class AnnotationProvider {
    @VisibleForTesting
    final String schemaNamespace;

    private final Map<String, List<CsdlAnnotations>> annotationsByEntity;

    private boolean isLatestVersion = true;

    @VisibleForTesting
    AnnotationProvider(String schemaNamespace, List<CsdlAnnotations> annotationGroups) {
        this.schemaNamespace = schemaNamespace;
        this.annotationsByEntity = annotationGroups.stream().collect(
                Collectors.groupingBy(annotations -> new AnnotationTarget(annotations.getTarget()).getEntityName()));
    }

    /**
     * Returns all annotations which belongs to one requested entity.
     *
     * @param entityName The name of the entity
     * @return A list of {@link CsdlAnnotations}
     */
    public List<CsdlAnnotations> getAnnotationsByEntity(String entityName) {
        return annotationsByEntity.get(entityName);
    }

    /**
     * Returns all entity names.
     *
     * @return A set of entity names
     */
    public Set<String> getEntityNames() {
        return annotationsByEntity.keySet();
    }

    /**
     * Returns all annotations of this namespace.
     *
     * @return A list of {@link CsdlAnnotations}
     */
    public List<CsdlAnnotations> getAnnotations() {
        return annotationsByEntity.values().stream().flatMap(annotations -> annotations.stream())
                .collect(Collectors.toList());
    }

    /**
     * Gets the latest version of the AnnotationProvider to determine if this instance is outdated.
     *
     * @return isLatestVersion
     */
    public boolean isLatestVersion() {
        return isLatestVersion;
    }

    /**
     * Sets the value of the latestVersion to determine if a newer version of an AnnotationProvider of this instance has
     * been instantiated.
     *
     * @param latestVersion latestVersion
     */
    void setLatestVersion(Boolean latestVersion) {
        isLatestVersion = latestVersion;
    }
}
