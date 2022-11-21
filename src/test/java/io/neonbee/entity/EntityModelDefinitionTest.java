package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.impl.CdsModelBuilder;
import com.sap.cds.reflect.impl.CdsServiceBuilder;

class EntityModelDefinitionTest {
    @Test
    void testInstantiation() {
        assertThrows(NullPointerException.class, () -> new EntityModelDefinition(null, null));
        assertThrows(NullPointerException.class, () -> new EntityModelDefinition(Map.of(), null));
        assertThrows(NullPointerException.class, () -> new EntityModelDefinition(null, Map.of()));

        EntityModelDefinition definition =
                new EntityModelDefinition(Map.of("foo", new byte[] { 1, 2, 3 }), Map.of("bar", new byte[] { 4, 5, 6 }));
        assertThat(definition.getCSNModelDefinitions().keySet()).containsExactly("foo");
        assertThat(definition.getCSNModelDefinitions().get("foo")).asList().containsExactly((byte) 1, (byte) 2,
                (byte) 3);
        assertThat(definition.getAssociatedModelDefinitions().keySet()).containsExactly("bar");
        assertThat(definition.getAssociatedModelDefinitions().get("bar")).asList().containsExactly((byte) 4, (byte) 5,
                (byte) 6);
        assertThat(definition.toString()).isEqualTo("foo$bar");
    }

    @Test
    @DisplayName("Checks if namespace can be extracted")
    void getNamespace() {
        CdsServiceBuilder serviceBuilder = new CdsServiceBuilder(List.of(), "namespace.Service");
        CdsModelBuilder modelBuilder = CdsModelBuilder.create();
        modelBuilder.addService(serviceBuilder);
        CdsModel cdsModel = modelBuilder.build();

        assertThat(EntityModelDefinition.getNamespace(cdsModel)).isEqualTo("namespace");

        serviceBuilder = new CdsServiceBuilder(List.of(), "Service");
        modelBuilder = CdsModelBuilder.create();
        modelBuilder.addService(serviceBuilder);
        cdsModel = modelBuilder.build();
        assertThat(EntityModelDefinition.getNamespace(cdsModel)).isEqualTo("");

        modelBuilder = CdsModelBuilder.create();
        cdsModel = modelBuilder.build();
        assertThat(EntityModelDefinition.getNamespace(cdsModel)).isNull();
    }

    @Test
    @DisplayName("Checks if null is returned if no service was found in CDS model")
    void getNamespaceFails() {
        CdsModelBuilder modelBuilder = CdsModelBuilder.create();
        CdsModel cdsModel = modelBuilder.build();
        assertThat(EntityModelDefinition.getNamespace(cdsModel)).isNull();
    }

    @Test
    @DisplayName("Checks namespace is properly extracted from full qualified service name")
    void retrieveNamespace() {
        assertThat(EntityModelDefinition.retrieveNamespace("io.neonbee.test.TestService")).isEqualTo("io.neonbee.test");
        assertThat(EntityModelDefinition.retrieveNamespace("TestService")).isEqualTo("");
    }

    @Test
    @DisplayName("Checks edmx paths are properly resolved")
    void resolveEdmxPaths() throws IOException {
        List<Path> expectedEdmxFileNames = List.of(Path.of("io.neonbee.test2.TestService2Cars.edmx"),
                Path.of("io.neonbee.test2.TestService2Users.edmx"));
        Path csnPath = TEST_RESOURCES.resolveRelated("TestService2.csn");
        CdsModel cdsModel = CdsModel.read(Files.newInputStream(csnPath));
        List<Path> edmxPaths = EntityModelDefinition.resolveEdmxPaths(csnPath, cdsModel);
        Stream<Path> edmxFileNames = edmxPaths.stream().map(Path::getFileName);

        com.google.common.truth.Truth8.assertThat(edmxFileNames).containsExactlyElementsIn(expectedEdmxFileNames);
    }
}
