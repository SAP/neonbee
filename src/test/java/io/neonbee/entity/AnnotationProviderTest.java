package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.AnnotationHelper.createConstantAnnotations;

import java.util.List;
import java.util.Set;

import org.apache.olingo.commons.api.edm.provider.CsdlAnnotations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class AnnotationProviderTest {
    private static final String COMMON_BACKEND = "common.backend";

    private static final String SCHEMA_NAMEPSACE = "AnyModel.Service";

    private static final CsdlAnnotations QUERY_ANNOTATION =
            createConstantAnnotations("AnyModel.Service.Products", COMMON_BACKEND, "query", "PRODUCTS");

    private static final CsdlAnnotations QUERY_ANNOTATION2 =
            createConstantAnnotations("AnyModel.Service.Customers", COMMON_BACKEND, "query", "CUSTOMERS");

    private static final CsdlAnnotations FIELD_ANNOTATION =
            createConstantAnnotations("AnyModel.Service.Products/ID", COMMON_BACKEND, "field", "Official_Name_/key");

    private static final CsdlAnnotations FIELD_ANNOTATION2 = createConstantAnnotations("AnyModel.Service.Products/Name",
            COMMON_BACKEND, "field", "Official_Name_/title");

    private static final List<CsdlAnnotations> ALL_ANNOTATIONS =
            List.of(QUERY_ANNOTATION, QUERY_ANNOTATION2, FIELD_ANNOTATION, FIELD_ANNOTATION2);

    private AnnotationProvider annotationProvider;

    @BeforeEach
    void beforeEach() {
        annotationProvider = new AnnotationProvider(SCHEMA_NAMEPSACE, ALL_ANNOTATIONS);
    }

    @Test
    @DisplayName("check if getting annotations by entity works")
    public void getAnnotationsByEntity() {
        List<CsdlAnnotations> productAnnotations =
                annotationProvider.getAnnotationsByEntity("AnyModel.Service.Products");
        assertThat(productAnnotations).hasSize(3);
        assertThat(productAnnotations).containsExactly(QUERY_ANNOTATION, FIELD_ANNOTATION, FIELD_ANNOTATION2);

        List<CsdlAnnotations> customerAnnotations =
                annotationProvider.getAnnotationsByEntity("AnyModel.Service.Customers");
        assertThat(customerAnnotations).hasSize(1);
        assertThat(customerAnnotations).containsExactly(QUERY_ANNOTATION2);
    }

    @Test
    @DisplayName("check if getting all annotations works")
    public void getAnnotations() {
        List<CsdlAnnotations> annotations = annotationProvider.getAnnotations();
        assertThat(annotations).hasSize(4);
        assertThat(annotations).containsExactly(QUERY_ANNOTATION, QUERY_ANNOTATION2, FIELD_ANNOTATION,
                FIELD_ANNOTATION2);
    }

    @Test
    @DisplayName("check if setting and getting latest version works")
    public void isLatestVersionTest() {
        assertThat(annotationProvider.isLatestVersion()).isTrue();
        annotationProvider.setLatestVersion(false);
        assertThat(annotationProvider.isLatestVersion()).isFalse();
    }

    @Test
    @DisplayName("check if getting entity names works")
    public void getEntityNames() {
        Set<String> names = annotationProvider.getEntityNames();
        assertThat(names).hasSize(2);
        assertThat(names).containsExactly("AnyModel.Service.Products", "AnyModel.Service.Customers");
    }
}
