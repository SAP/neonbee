package io.neonbee.endpoint.odatav4.internal.olingo.edm;

import java.util.Objects;

import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;

public final class EdmPrimitiveNull implements EdmPrimitiveType {
    private static final EdmPrimitiveNull SINGLETON_INSTANCE = new EdmPrimitiveNull();

    private static final String NULL_STRING = "null";

    /**
     * Returns always the same instance of EdmPrimitiveNull, to save memory.
     *
     * @return singleton instance of EdmPrimitiveNull
     */
    public static EdmPrimitiveNull getInstance() {
        return SINGLETON_INSTANCE;
    }

    @Override
    public boolean equals(Object object) {
        return (this == object) || ((object != null) && (this.getClass() == object.getClass()));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this, this.getClass());
    }

    @Override
    public String fromUriLiteral(String literal) throws EdmPrimitiveTypeException {
        return literal;
    }

    @Override
    public String toUriLiteral(String literal) {
        return literal;
    }

    @Override
    public Class<?> getDefaultType() {
        return null;
    }

    @Override
    public FullQualifiedName getFullQualifiedName() {
        return new FullQualifiedName(getNamespace(), getName());
    }

    @Override
    public EdmTypeKind getKind() {
        return EdmTypeKind.PRIMITIVE;
    }

    @Override
    public String getName() {
        return "Null";
    }

    @Override
    public String getNamespace() {
        return EDM_NAMESPACE;
    }

    @Override
    public boolean isCompatible(EdmPrimitiveType primitiveType) {
        return equals(primitiveType);
    }

    @Override
    public String toString() {
        return getFullQualifiedName().getFullQualifiedNameAsString();
    }

    @Override
    public boolean validate(String value, Boolean isNullable, Integer maxLength, Integer precision, Integer scale,
            Boolean isUnicode) {
        return ((value == null) && ((isNullable == null) || isNullable)) || NULL_STRING.equals(value);
    }

    @Override
    public <T> T valueOfString(String value, Boolean isNullable, Integer maxLength, Integer precision, Integer scale,
            Boolean isUnicode, Class<T> returnType) throws EdmPrimitiveTypeException {
        if (value == null) {
            if ((isNullable != null) //
                    && Boolean.FALSE.equals(isNullable)) {
                throw new EdmPrimitiveTypeException("Error: The literal 'null' is not allowed");
            }
            return null;
        }

        if (NULL_STRING.equals(value)) {
            return null;
        } else {
            throw new EdmPrimitiveTypeException("Error: The literal '" + value + "' has illegal content");
        }
    }

    @Override
    public String valueToString(Object value, Boolean isNullable, Integer maxLength, Integer precision, Integer scale,
            Boolean isUnicode) throws EdmPrimitiveTypeException {
        if (value == null) {
            if ((isNullable != null) && Boolean.FALSE.equals(isNullable)) {
                throw new EdmPrimitiveTypeException("Error: The value 'null' is not allowed");
            }
            return null;
        }
        return NULL_STRING;
    }
}
