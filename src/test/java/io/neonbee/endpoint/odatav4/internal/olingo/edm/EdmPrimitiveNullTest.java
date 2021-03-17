package io.neonbee.internal.processor.odata.edm;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdmPrimitiveNullTest {
    EdmPrimitiveNull edmPrimitiveNull;

    @BeforeEach
    void beforeEach() {
        edmPrimitiveNull = new EdmPrimitiveNull();
    }

    @Test
    @DisplayName("Test equals method")
    @SuppressWarnings({ "EqualsIncompatibleType", "TruthSelfEquals" })
    void equalsTest() throws ClassNotFoundException {
        assertThat(edmPrimitiveNull).isEqualTo(edmPrimitiveNull);
        assertThat(edmPrimitiveNull).isEqualTo(new EdmPrimitiveNull());
        assertThat(edmPrimitiveNull).isEqualTo(EdmPrimitiveNull.getInstance());
        assertThat(edmPrimitiveNull).isNotEqualTo(null);
    }

    @Test
    @DisplayName("Test fromUriLiteral method")
    void fromUriLiteralTest() throws EdmPrimitiveTypeException {
        assertThat(edmPrimitiveNull.fromUriLiteral("null")).isEqualTo("null");
        assertThat(edmPrimitiveNull.fromUriLiteral(null)).isNull();
    }

    @Test
    @DisplayName("Test toUriLiteral method")
    void toUriLiteralTest() {
        assertThat(edmPrimitiveNull.toUriLiteral("null")).isEqualTo("null");
        assertThat(edmPrimitiveNull.toUriLiteral(null)).isNull();
    }

    @Test
    @DisplayName("Test getDefaultType method")
    void getDefaultTypeTest() {
        assertThat(edmPrimitiveNull.getDefaultType()).isNull();
    }

    @Test
    @DisplayName("Test getName method")
    void getNameTest() {
        assertThat(edmPrimitiveNull.getName()).isEqualTo("Null");
    }

    @Test
    @DisplayName("Test getNamespace method")
    void getNamespaceTest() {
        assertThat(edmPrimitiveNull.getNamespace()).isEqualTo("Edm");
    }

    @Test
    @DisplayName("Test getFullQualifiedName method")
    void getFullQualifiedNameTest() {
        assertThat(edmPrimitiveNull.getFullQualifiedName()).isEqualTo(new FullQualifiedName("Edm", "Null"));
    }

    @Test
    @DisplayName("Test getKind method")
    void getKindTest() {
        assertThat(edmPrimitiveNull.getKind()).isEqualTo(EdmTypeKind.PRIMITIVE);
    }

    @Test
    @DisplayName("Test isCompatible method")
    void isCompatibleTest() {
        assertThat(edmPrimitiveNull.isCompatible(new EdmPrimitiveNull())).isTrue();
    }

    @Test
    @DisplayName("Test toString method")
    void toStringTest() {
        assertThat(edmPrimitiveNull.toString()).isEqualTo("Edm.Null");
    }

    @Test
    @DisplayName("Test validate method")
    void validateTest() {
        assertThat(edmPrimitiveNull.validate(null, null, null, null, null, null)).isTrue();
        assertThat(edmPrimitiveNull.validate(null, true, null, null, null, null)).isTrue();
        assertThat(edmPrimitiveNull.validate("null", true, null, null, null, null)).isTrue();
        assertThat(edmPrimitiveNull.validate("null", true, null, 100, 0, false)).isTrue();
        assertThat(edmPrimitiveNull.validate(null, false, null, null, null, null)).isFalse();
    }

    @Test
    @DisplayName("Test valueOfString method")
    void valueOfStringTest() throws EdmPrimitiveTypeException {
        assertThat(edmPrimitiveNull.valueOfString("null", null, null, null, null, null, String.class)).isNull();
        assertThrows(EdmPrimitiveTypeException.class,
                () -> edmPrimitiveNull.valueOfString("Lord C.", null, null, null, null, null, String.class));
        assertThat(edmPrimitiveNull.valueOfString(null, true, null, null, null, null, String.class)).isNull();
        assertThrows(EdmPrimitiveTypeException.class,
                () -> edmPrimitiveNull.valueOfString(null, false, null, null, null, null, String.class));
        assertThat(edmPrimitiveNull.valueOfString(null, null, null, null, null, null, String.class)).isNull();
    }

    @Test
    @DisplayName("Test valueToString method")
    void valueToStringTest() throws EdmPrimitiveTypeException {
        assertThat(edmPrimitiveNull.valueToString("Lord C.", null, null, null, null, null)).isEqualTo("null");
        assertThat(edmPrimitiveNull.valueToString(null, true, null, null, null, null)).isNull();
        assertThrows(EdmPrimitiveTypeException.class,
                () -> edmPrimitiveNull.valueToString(null, false, null, null, null, null));
        assertThat(edmPrimitiveNull.valueToString(null, null, null, null, null, null)).isNull();
    }
}
