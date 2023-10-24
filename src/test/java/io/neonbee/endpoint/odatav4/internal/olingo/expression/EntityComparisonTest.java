package io.neonbee.endpoint.odatav4.internal.olingo.expression;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.EDM_BINARY_JAVA_TYPES;
import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.EDM_STRING_JAVA_TYPES;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.vertx.ext.web.RoutingContext;

class EntityComparisonTest {
    private final EntityComparison testEntityComparisonImplementation = new EntityComparison() {};

    private static final byte[] BYTES_TEST = "Test".getBytes(UTF_8);

    private static final byte[] BYTES_42 = "42".getBytes(UTF_8);

    @Test
    void instanceOfExpectedTypeTest() {
        // byte[] and Object[] => false
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, BYTES_TEST,
                new Object[3])).isFalse();

        // byte[] and int[] => false
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, BYTES_TEST,
                new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 })).isFalse();

        // Integer and byte[] => false
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, 42, BYTES_42))
                .isFalse();

        // byte[] and Integer => false
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, BYTES_42,
                Integer.valueOf(42))).isFalse();

        // int and String => false
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_STRING_JAVA_TYPES, 5, "Hugo"))
                .isFalse();

        // Integer and String => false
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_STRING_JAVA_TYPES, 5, "Hugo"))
                .isFalse();

        // byte[] and byte[] => true
        assertThat(
                testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, BYTES_TEST, BYTES_42))
                        .isTrue();

        // Byte[] and byte[] => true
        byte[] byteArray = "Test ABC".getBytes(UTF_8);
        // Convert byte[] to Byte[]
        Byte[] byteArrayToBeTested = new Byte[byteArray.length];
        Arrays.setAll(byteArrayToBeTested, i -> byteArray[i]);

        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, byteArrayToBeTested,
                "Test 123".getBytes(UTF_8))).isTrue();

        // Byte[] and Byte[] => true
        assertThat(testEntityComparisonImplementation.instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, byteArrayToBeTested,
                byteArrayToBeTested)).isTrue();
    }

    static Stream<Arguments> comparePropertyValuesParameters() {
        UUID uuid1 = UUID.fromString("1386d8da-4cb4-4fee-97ef-5a24d6a41b34");
        UUID uuid2 = UUID.fromString("74affcbf-1c66-4903-9cf3-685f511c93b0");

        return Stream.of(
                Arguments.of(null, uuid1, uuid1, EdmPrimitiveTypeKind.Guid, "ID", 0),
                Arguments.of(null, uuid1, uuid1.toString(), EdmPrimitiveTypeKind.Guid, "ID", 0),
                Arguments.of(null, uuid1, uuid2, EdmPrimitiveTypeKind.Guid, "ID", -1),
                Arguments.of(null, uuid2, uuid1.toString(), EdmPrimitiveTypeKind.Guid, "ID", 1));
    }

    @ParameterizedTest(name = "{index}: compare {3} result should be {5}")
    @MethodSource("comparePropertyValuesParameters")
    @DisplayName("Test to compare entity properties of unknown concrete Java types (Object).")
    void comparePropertyValues(RoutingContext routingContext, Object leadingPropertyValue1, Object propertyValue2,
            EdmPrimitiveTypeKind propertyTypeKind, String propertyName, int expected) {
        assertThat(testEntityComparisonImplementation.comparePropertyValues(routingContext, leadingPropertyValue1,
                propertyValue2, propertyTypeKind, propertyName)).isEqualTo(expected);
    }
}
