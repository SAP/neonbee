package io.neonbee.internal.processor.odata.expression.operands;

import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_BOOLEAN;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_BYTE;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DATE;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DATE_TIME_OFFSET;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DECIMAL;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DOUBLE;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_INT16;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_INT32;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_INT64;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_NULL;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_SBYTE;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_SINGLE;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.TYPE_MAPPINGS;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.internal.processor.odata.edm.EdmConstants;
import io.neonbee.internal.processor.odata.expression.EntityComparison;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

public class ExpressionVisitorOperand implements EntityComparison {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    protected Object value;

    private final EdmType type;

    private final EdmProperty edmProperty;

    private final RoutingContext routingContext;

    /**
     * Creates a new ExpressionVisitorOperand.
     *
     * @param routingContext the current routingContext
     * @param value          the value of this operand
     * @param type           the type of this operand
     */
    public ExpressionVisitorOperand(RoutingContext routingContext, Object value, EdmType type) {
        this(routingContext, value, type, null);
    }

    /**
     * Creates a new ExpressionVisitorOperand.
     *
     * @param routingContext the current routingContext
     * @param value          the value of this operand
     * @param type           the type of this operand
     * @param edmProperty    the property of this operand
     */
    public ExpressionVisitorOperand(RoutingContext routingContext, Object value, EdmType type,
            EdmProperty edmProperty) {
        this.routingContext = routingContext;
        this.value = value;
        this.type = type;
        this.edmProperty = edmProperty;
    }

    /**
     * This method is used to normalize the types of two different ExpressionVisitorOperand (the current instance and a
     * provided one). This is needed e. g. if a ExpressionVisitorOperands have to be compared or processed. Then they
     * have to be from the same EdmPrimitiveType. By provided a ExpressionVisitorOperand, the current instance of
     * ExpressionVisitorOperand is changed to the type of the provided ExpressionVisitorOperand.
     *
     * @param otherOperand The ExpressionVisitorOperand with type is used as base type.
     * @return the ExpressionVisitorOperand instance with type changed to the provided ExpressionVisitorOperand's type.
     * @throws ODataApplicationException If type casting fails
     */
    public ExpressionVisitorOperand normalizeTypes(ExpressionVisitorOperand otherOperand)
            throws ODataApplicationException {
        ExpressionVisitorOperand other = otherOperand.setType();
        EdmType otherType = other.getType();

        // In case of numberic values make sure that the EDM type is equal, check also the java type.
        // It is possible that there is an conversion even if the same EdmType is provided.
        // For example consider an Edm.Int32 (internal Integer) and an Edm.Int16 (internal Short) value:
        // shortInstance.equals(intInstance) will always be false!
        if ((type.equals(otherType) && (value != null) && (other.getValue() != null)
                && (value.getClass() == other.getValue().getClass()))
                || (is(PRIMITIVE_NULL) || other.is(PRIMITIVE_NULL))) {
            return this;
        }

        if (type.equals(PRIMITIVE_DOUBLE) || otherType.equals(PRIMITIVE_DOUBLE)) {
            return setType(PRIMITIVE_DOUBLE);
        } else if (type.equals(PRIMITIVE_SINGLE) || otherType.equals(PRIMITIVE_SINGLE)) {
            return setType(PRIMITIVE_SINGLE);
        } else if (type.equals(PRIMITIVE_DECIMAL) || otherType.equals(PRIMITIVE_DECIMAL)) {
            return setType(PRIMITIVE_DECIMAL);
        } else if (type.equals(PRIMITIVE_INT64) || otherType.equals(PRIMITIVE_INT64)) {
            return setType(PRIMITIVE_INT64);
        } else if (type.equals(PRIMITIVE_INT32) || otherType.equals(PRIMITIVE_INT32)) {
            return setType(PRIMITIVE_INT32);
        } else if (type.equals(PRIMITIVE_INT16) || otherType.equals(PRIMITIVE_INT16)) {
            return setType(PRIMITIVE_INT16);
        } else {
            return setType((EdmPrimitiveType) type);
        }
    }

    /**
     * When created it is unclear if the value (type Object) of this ExpressionVisitorOperand is a single object or a
     * list of objects. Furthermore the EdmType is unclear (type is null). This method will set the appropriate
     * EdmPrimitiveType of this ExpressionVisitorOperand.
     *
     * @return the ExpressionVisitorOperand with set type (EdmPrimitiveType)
     * @throws ODataApplicationException If casting to the passed {@link EdmPrimitiveType} fails
     */
    public ExpressionVisitorOperand setType() throws ODataApplicationException {
        if (isNull()) {
            return this;
        } else if ((type instanceof EdmPrimitiveType) && !(value instanceof Collection)) {
            return value.getClass() == getDefaultType((EdmPrimitiveType) type) ? this
                    : setType((EdmPrimitiveType) type);
        } else {
            String message =
                    "A single primitive-type instance is expected. A collection of primitive-types is currently not supported.";
            LOGGER.correlateWith(routingContext).error(message);
            throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    /**
     * When created it is unclear if the value (type Object) of this ExpressionVisitorOperand is a single object or a
     * list of objects. Furthermore the EdmType is unclear (type is null). This method will try to set the appropriate
     * EdmPrimitiveType of this ExpressionVisitorOperand to the provided one. This is used e. g. when the type should be
     * changed when comparing 2 different ExpressionVisitorOperands.
     *
     * @param type The type to be set
     * @return The ExpressionVisitorOperand with set type (EdmPrimitiveType)
     * @throws ODataApplicationException If casting to the passed {@link EdmPrimitiveType} fails
     */
    public ExpressionVisitorOperand setType(EdmPrimitiveType type) throws ODataApplicationException {
        if (is(PRIMITIVE_NULL)) {
            return this;
        } else if (isNull()) {
            return new ExpressionVisitorOperand(routingContext, null, type);
        }

        Exception exception = null;
        Object newValue = null;
        if (type.equals(PRIMITIVE_BOOLEAN)) {
            newValue = Boolean.valueOf((String) value);
        } // Use BigInteger for arbitrarily large whole numbers
        else if (type.equals(PRIMITIVE_SBYTE) || type.equals(PRIMITIVE_BYTE) || type.equals(PRIMITIVE_INT16)
                || type.equals(PRIMITIVE_INT32) || type.equals(PRIMITIVE_INT64)) {
            if (value instanceof BigInteger) {
                newValue = value;
            } else if ((value instanceof Byte) || (value instanceof Short) || (value instanceof Integer)
                    || (value instanceof Long)) {
                newValue = BigInteger.valueOf(((Number) value).longValue());
            } else if (value instanceof String) {
                newValue = new BigInteger(String.valueOf(value));
            }
            // Use BigDecimal for unlimited precision
        } else if (type.equals(PRIMITIVE_DOUBLE) || type.equals(PRIMITIVE_SINGLE) || type.equals(PRIMITIVE_DECIMAL)) {
            try {
                newValue = new BigDecimal(value.toString());
            } catch (NumberFormatException e) {
                exception = e;
            }
        } else if (PRIMITIVE_DATE.equals(type) || PRIMITIVE_DATE_TIME_OFFSET.equals(type)) {
            newValue = dateTimeObjectToInstant(routingContext, value);
        } else {
            // Use type conversion of EdmPrimitive types
            try {
                String literal = getLiteral(value);
                newValue = cast(type.fromUriLiteral(literal), type);
            } catch (EdmPrimitiveTypeException e) {
                exception = e;
            }
        }

        if (newValue != null) {
            return new ExpressionVisitorOperand(routingContext, newValue, type);
        }

        String message = "Cast of value with type" + value.getClass() + " to type " + type + " failed.";
        LOGGER.correlateWith(routingContext).error(message);
        throw new ODataApplicationException(message, HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(),
                Locale.ENGLISH, exception);
    }

    /**
     * Returns the property of this operand.
     *
     * @return the property
     */
    public EdmProperty getEdmProperty() {
        return edmProperty;
    }

    /**
     * Returns the type of this operand.
     *
     * @return the type
     */
    public EdmType getType() {
        return type;
    }

    /**
     * Cast the value of this operand to the passed type.
     *
     * @param <T>   the desired type
     * @param clazz class of the desired type
     * @return The value casted to the desired type
     */
    public <T> T getTypedValue(Class<T> clazz) {
        return clazz.cast(value);
    }

    /**
     * Checks if the type of this ExpressionVisitorOperand matches one of the passed ones.
     *
     * @param types the types to validate
     * @return true of the type matches one of the passed ones, otherwise false.
     */
    public boolean is(EdmPrimitiveType... types) {
        return Arrays.stream(types).anyMatch(t -> t.equals(type));
    }

    /**
     * Checks if the type of this ExpressionVisitorOperand is boolean.
     *
     * @return true of the type is boolean, otherwise false.
     */
    public boolean isBooleanType() {
        return is(PRIMITIVE_BOOLEAN);
    }

    /**
     * Checks if the type of this ExpressionVisitorOperand is decimal.
     *
     * @return true of the type is decimal, otherwise false.
     */
    public boolean isDecimalType() {
        return is(PRIMITIVE_NULL, PRIMITIVE_SINGLE, PRIMITIVE_DOUBLE, PRIMITIVE_DECIMAL);
    }

    /**
     * Checks if the type of this ExpressionVisitorOperand is integer.
     *
     * @return true of the type is integer, otherwise false.
     */
    public boolean isIntegerType() {
        return is(PRIMITIVE_NULL, PRIMITIVE_BYTE, PRIMITIVE_SBYTE, PRIMITIVE_INT16, PRIMITIVE_INT32, PRIMITIVE_INT64);
    }

    /**
     * Checks if the type of this ExpressionVisitorOperand is {@link EdmConstants#PRIMITIVE_NULL} or the value is null.
     *
     * @return true of the type is PRIMITIVE_NULL or the value is null, otherwise false.
     */
    public boolean isNull() {
        return is(PRIMITIVE_NULL) || (value == null);
    }

    @Override
    public String toString() {
        return new StringBuilder().append("ExpressionVisitorOperand [type=").append(type).append(", edmProperty=")
                .append(edmProperty).append("]").toString();
    }

    private Object cast(String value, EdmPrimitiveType type) throws EdmPrimitiveTypeException {
        try {
            EdmProperty edmProperty = getEdmProperty();
            if (edmProperty == null) {
                return type.valueOfString(value, null, null, null, null, null, getDefaultType(type));
            } else {
                return type.valueOfString(value, edmProperty.isNullable(), edmProperty.getMaxLength(),
                        edmProperty.getPrecision(), edmProperty.getScale(), edmProperty.isUnicode(),
                        getDefaultType(type));
            }
        } catch (EdmPrimitiveTypeException e) {
            LOGGER.error("Can't cast EdmPrimitiveType: {}", value, e);
        }
        return null;
    }

    private Class<?> getDefaultType(EdmPrimitiveType type) {
        return TYPE_MAPPINGS.get(type) != null ? TYPE_MAPPINGS.get(type) : type.getDefaultType();
    }

    /**
     * Get the value of this operand.
     *
     * @return the value
     */
    public Object getValue() {
        return value;
    }

    private String getLiteral(Object value) throws EdmPrimitiveTypeException {
        String uriLiteral = null;
        if (getEdmProperty() != null) {
            uriLiteral = ((EdmPrimitiveType) type).valueToString(value, getEdmProperty().isNullable(),
                    getEdmProperty().getMaxLength(), getEdmProperty().getPrecision(), getEdmProperty().getScale(),
                    getEdmProperty().isUnicode());
        } else {
            uriLiteral = ((EdmPrimitiveType) type).valueToString(value, null, null, null, null, null);
        }
        return ((EdmPrimitiveType) type).toUriLiteral(uriLiteral);
    }
}
