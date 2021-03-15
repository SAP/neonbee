package io.neonbee.entity;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.sap.cds.reflect.CdsModel;
import com.sap.cds.reflect.impl.CdsModelBuilder;
import com.sap.cds.reflect.impl.CdsServiceBuilder;

class ModelDefinitionHelperTest {

    @Test
    @DisplayName("Checks if namespace can be extracted")
    void getNamespace() {
        CdsServiceBuilder serviceBuilder = new CdsServiceBuilder(List.of(), "namespace.Service");
        CdsModelBuilder modelBuilder = CdsModelBuilder.create();
        modelBuilder.addService(serviceBuilder);
        CdsModel cdsModel = modelBuilder.build();

        assertThat(ModelDefinitionHelper.getNamespace(cdsModel)).isEqualTo("namespace");

        serviceBuilder = new CdsServiceBuilder(List.of(), "Service");
        modelBuilder = CdsModelBuilder.create();
        modelBuilder.addService(serviceBuilder);
        cdsModel = modelBuilder.build();
        assertThat(ModelDefinitionHelper.getNamespace(cdsModel)).isEqualTo("");
    }

    @Test
    @DisplayName("Checks if exception will be thrown if no service was found in CDS model")
    void getNamespaceFails() {
        CdsModelBuilder modelBuilder = CdsModelBuilder.create();
        CdsModel cdsModel = modelBuilder.build();
        Assertions.assertThrows(RuntimeException.class, () -> ModelDefinitionHelper.getNamespace(cdsModel));
    }

    @Test
    @DisplayName("Checks namespace is properly extracted from full qualified service name")
    void retrieveNamespace() {
        assertThat(ModelDefinitionHelper.retrieveNamespace("io.neonbee.test.TestService")).isEqualTo("io.neonbee.test");
        assertThat(ModelDefinitionHelper.retrieveNamespace("TestService")).isEqualTo("");
    }

    @Test
    @DisplayName("Checks edmx paths are properly resolved")
    void resolveEdmxPaths() throws IOException {
        List<Path> expectedEdmxFileNames = List.of(Path.of("io.neonbee.test2.TestService2Cars.edmx"),
                Path.of("io.neonbee.test2.TestService2Users.edmx"));
        Path csnPath = TEST_RESOURCES.resolveRelated("TestService2.csn");
        CdsModel cdsModel = CdsModel.read(Files.newInputStream(csnPath));
        List<Path> edmxPaths = ModelDefinitionHelper.resolveEdmxPaths(csnPath, cdsModel);
        Stream<Path> edmxFileNames = edmxPaths.stream().map(Path::getFileName);

        com.google.common.truth.Truth8.assertThat(edmxFileNames).containsExactlyElementsIn(expectedEdmxFileNames);
    }
}
