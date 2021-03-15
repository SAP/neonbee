package io.neonbee.internal.processor.odata.expression;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_BINARY_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_STRING_JAVA_TYPES;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

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
}
