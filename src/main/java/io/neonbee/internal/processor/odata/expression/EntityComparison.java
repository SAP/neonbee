package io.neonbee.internal.processor.odata.expression;

import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_BINARY_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_BOOLEAN_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_DATE_TIMEOFDAY_DATETIMEOFFSET_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_DECIMAL_DURATION_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_GUID_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_INT16_INT32_INT64_BYTE_SBYTE_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_SINGLE_DOUBLE_JAVA_TYPES;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.EDM_STRING_JAVA_TYPES;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.internal.processor.odata.edm.EdmHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

public interface EntityComparison {
    LoggingFacade LOGGER = LoggingFacade.create();

    /**
     * Converts a passed object to BigInteger if possible.
     *
     * @param object the passed object to be converted to BigInteger. Should be one of: BigInteger or String.
     * @return the BigInteger instance
     */
    private BigInteger toBigInteger(Object object) {
        if (object instanceof BigInteger) {
            return (BigInteger) object;
        } else {
            return new BigInteger(String.valueOf(object));
        }
    }

    /**
     * Converts a passed object to BigDecimal if possible.
     *
     * @param object The passed object to be converted to BigDecimal. Should be one of: BigDecimal, BigInteger or
     *               String.
     * @return The BigDecimal instance
     */
    private BigDecimal toBigDecimal(Object object) {
        if (object instanceof BigDecimal) {
            return (BigDecimal) object;
        } else if (object instanceof BigInteger) {
            return new BigDecimal((BigInteger) object);
        } else {
            return new BigDecimal(String.valueOf(object));
        }
    }

    /**
     * Converts a passed object to Long if possible.
     *
     * @param routingContext the current routingContext
     * @param object         the passed object to be converted to Long. Should be one of: Calendar, Time, Timestamp,
     *                       Date, LocalDate, LocalDateTime, Long or Instant.
     * @return The Long representation of the provided date or time object
     * @throws ODataApplicationException If conversion is not possible.
     */
    private Long dateTimeObjectToLong(RoutingContext routingContext, Object object) throws ODataApplicationException {
        if (object instanceof Time) {
            return ((Time) object).getTime();
        } else {
            return dateTimeObjectToInstant(routingContext, object).toEpochMilli();
        }
    }

    /**
     * Converts a passed object to Instant if possible.
     *
     * @param routingContext the routingContext
     * @param object         the passed object to be converted to Long. Should be one of: Calendar, Time, Timestamp,
     *                       Date, LocalDate, LocalDateTime, Long or Instant.
     * @return The Instant representation of the provided date or time object
     * @throws ODataApplicationException If conversion is not possible.
     */
    @SuppressWarnings("PMD.AvoidCatchingNPE")
    default Instant dateTimeObjectToInstant(RoutingContext routingContext, Object object)
            throws ODataApplicationException {
        Instant instant;
        try {
            if (object instanceof Date) {
                instant = ((Date) object).toInstant().atZone(ZoneId.systemDefault()).toInstant();
            } else if (object instanceof Instant) {
                instant = ((Instant) object).atZone(ZoneId.systemDefault()).toInstant();
            } else if (object instanceof Timestamp) {
                instant = ((Timestamp) object).toInstant().atZone(ZoneId.systemDefault()).toInstant();
            } else if (object instanceof Long) {
                instant = Instant.ofEpochMilli((Long) object).atZone(ZoneId.systemDefault()).toInstant();
            } else if (object instanceof LocalDate) {
                instant = ((LocalDate) object).atStartOfDay(ZoneId.systemDefault()).toInstant();
            } else if (object instanceof Calendar) {
                instant = ((Calendar) object).toInstant().atZone(ZoneId.systemDefault()).toInstant();
            } else if (object instanceof String) {
                String stringValue = (String) object;
                if (EdmHelper.isLocalDate(stringValue)) {
                    instant = LocalDate.parse(stringValue).atStartOfDay(ZoneId.systemDefault()).toInstant();
                } else if (EdmHelper.isLocalDateTime(stringValue)) {
                    instant = LocalDateTime.parse(stringValue).atZone(ZoneId.systemDefault()).toInstant();
                } else {
                    instant = Instant.parse(stringValue).atZone(ZoneId.systemDefault()).toInstant();
                }
            } else {
                String message = "Converting object of type" + object.getClass() + " is not yet supported.";
                LOGGER.correlateWith(routingContext).error(message);
                throw new ODataApplicationException(message, HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                        Locale.ENGLISH);
            }
        } catch (NullPointerException | IllegalArgumentException | DateTimeParseException e) {
            String message = "Converting object of type" + object.getClass() + " failed.";
            LOGGER.correlateWith(routingContext).error(message);
            throw new ODataApplicationException(message, HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(),
                    Locale.ENGLISH, e);
        }
        return instant;
    }

    /**
     * Checks if one of the classes in the passed list matches the passed value.
     *
     * @param listOfClasses list of expected classes that represents the allowed types the value1 will be checked
     *                      against.
     * @param value1        the first value to be compared checked
     * @return {@code true} if the provided objects's type is in the provided list of expected classes or a sub-class of
     *         them; {@code false} otherwise
     */
    default boolean instanceOfExpectedType(List<Class<?>> listOfClasses, Object value1) {
        return listOfClasses.stream().anyMatch(c -> c.isAssignableFrom(value1.getClass()));
    }

    /**
     * Checks if one of the classes in the passed list matches the passed values.
     *
     * @param listOfClasses list of expected classes that represents the allowed types the value1 and value2 will be
     *                      checked against.
     * @param value1        the first value to be checked
     * @param value2        the second value to be checked
     * @return {@code true} if the provided objects's type is in the provided list of expected classes or a sub-class of
     *         them; {@code false} otherwise
     */
    default boolean instanceOfExpectedType(List<Class<?>> listOfClasses, Object value1, Object value2) {
        return instanceOfExpectedType(listOfClasses, value1) && instanceOfExpectedType(listOfClasses, value2);
    }

    /**
     * Convenience method to creates and log exceptions that occur during comparison.
     *
     * @param routingContext the current routingContext
     * @param listOfClasses  list of expected classes that represents the allowed types
     * @param value1         the first value to be checked
     * @param value2         the second value to be checked
     * @param propertyName   the entity property name
     * @return the created ODataApplicationException which is finally wrapped into a RuntimeException
     */
    default RuntimeException createAndLogException(RoutingContext routingContext, List<Class<?>> listOfClasses,
            Object value1, Object value2, String propertyName) {
        String messageTemplate =
                "An error has occurred while comparing two values of property %s. The types of the compared values are %s and %s but both must be one of: %s";
        String message = String.format(messageTemplate, propertyName, value1.getClass().getSimpleName(),
                value2.getClass().getSimpleName(),
                listOfClasses.stream().map(Class::getSimpleName).collect(Collectors.joining(", ")));

        // Only RuntimeExceptions can be thrown from Comparators by contract
        RuntimeException exception = new IllegalArgumentException(new ODataApplicationException(message,
                HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH));

        errorLog(routingContext, message, exception);

        return exception;
    }

    /**
     * Compare entity properties of unknown concrete Java types (Object).
     *
     * @param routingContext        the routing context
     * @param leadingPropertyValue1 the leading (first) property type the second property is converted into
     * @param propertyValue2        the property that is compared to the first (leading) property
     * @param propertyTypeKind      the Edm primitive type kind that is taken into account during type conversion
     * @param propertyName          the name of the property that is compared
     * @return Returns a negative integer, zero, or a positive integer as the first property is less than, equal to, or
     *         greater than the second property.
     */
    @SuppressWarnings({ "PMD.CyclomaticComplexity", "PMD.ExcessiveMethodLength" })
    default int comparePropertyValues(RoutingContext routingContext, Object leadingPropertyValue1,
            Object propertyValue2, EdmPrimitiveTypeKind propertyTypeKind, String propertyName) {
        switch (propertyTypeKind) {
        case Binary:
            // In case of binaries, we will order by binary size, because comparing large byte arrays could be very
            // expensive.
            // Has to be one of: byte[], Byte[]
            if (instanceOfExpectedType(EDM_BINARY_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return Integer.compare(Array.getLength(leadingPropertyValue1), Array.getLength(propertyValue2));
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_BINARY_JAVA_TYPES, leadingPropertyValue1, propertyValue2,
                    propertyName);
        case Int16:
        case Int32:
        case Int64:
        case Byte:
        case SByte:
            // Has to be one of: Short, Byte, Integer, Long, BigInteger
            if (instanceOfExpectedType(EDM_INT16_INT32_INT64_BYTE_SBYTE_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return toBigInteger(leadingPropertyValue1).compareTo(toBigInteger(propertyValue2));
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_INT16_INT32_INT64_BYTE_SBYTE_JAVA_TYPES,
                    leadingPropertyValue1, propertyValue2, propertyName);
        case Decimal:
        case Duration:
            // Has to be one of: BigDecimal, BigInteger, Double, Float, Byte, Short, Integer, Long
            if (instanceOfExpectedType(EDM_DECIMAL_DURATION_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return toBigDecimal(leadingPropertyValue1).compareTo(toBigDecimal(propertyValue2));
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_DECIMAL_DURATION_JAVA_TYPES, leadingPropertyValue1,
                    propertyValue2, propertyName);
        case Single:
        case Double:
            // Has to be one of: Double, Float, BigDecimal, Byte, Short, Integer, Long
            if (instanceOfExpectedType(EDM_SINGLE_DOUBLE_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return toBigDecimal(leadingPropertyValue1).compareTo(toBigDecimal(propertyValue2));
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_SINGLE_DOUBLE_JAVA_TYPES, leadingPropertyValue1,
                    propertyValue2, propertyName);
        case Date:
        case TimeOfDay:
        case DateTimeOffset:
            // Has to be one of: Calendar, Date, Timestamp, Time, Long, LocalDate, LocalDateTime, Instant
            if (instanceOfExpectedType(EDM_DATE_TIMEOFDAY_DATETIMEOFFSET_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return dateTimeObjectToLong(routingContext, leadingPropertyValue1)
                            .compareTo(dateTimeObjectToLong(routingContext, propertyValue2));
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_DATE_TIMEOFDAY_DATETIMEOFFSET_JAVA_TYPES,
                    leadingPropertyValue1, propertyValue2, propertyName);
        case Boolean:
            // Has to be one of: Boolean
            if (instanceOfExpectedType(EDM_BOOLEAN_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return Boolean.compare((Boolean) leadingPropertyValue1, (Boolean) propertyValue2);
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_BOOLEAN_JAVA_TYPES, leadingPropertyValue1, propertyValue2,
                    propertyName);
        case String:
            // Has to be one of: String
            if (instanceOfExpectedType(EDM_STRING_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    return ((String) leadingPropertyValue1).compareToIgnoreCase((String) propertyValue2);
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_STRING_JAVA_TYPES, leadingPropertyValue1, propertyValue2,
                    propertyName);
        case Guid:
            // Has to be one of: UUID
            if (instanceOfExpectedType(EDM_GUID_JAVA_TYPES, leadingPropertyValue1)) {
                try {
                    // Example UUID: xxxxxxxx-xxxx-Bxxx-Axxx-xxxxxxxxxxxx
                    // The order is determined by 3 most significant bit of A
                    return ((UUID) leadingPropertyValue1).compareTo((UUID) propertyValue2);
                } catch (Exception e) {
                    errorLog(routingContext, e);
                }
            }
            throw createAndLogException(routingContext, EDM_GUID_JAVA_TYPES, leadingPropertyValue1, propertyValue2,
                    propertyName);
        default:
            throw new IllegalArgumentException(
                    new ODataApplicationException("Error during comparison of entity properties.",
                            HttpStatusCode.INTERNAL_SERVER_ERROR.getStatusCode(), Locale.ENGLISH));
        }
    }

    private void errorLog(RoutingContext routingContext, Exception e) {
        errorLog(routingContext, null, e);
    }

    private void errorLog(RoutingContext routingContext, String message, Exception e) {
        if (message != null) {
            if (routingContext != null) {
                LOGGER.correlateWith(routingContext).error(message, e);
            } else {
                LOGGER.error(message, e);
            }
        } else {
            if (routingContext != null) {
                LOGGER.correlateWith(routingContext).error(e.getMessage(), e);
            } else {
                LOGGER.error(e.getMessage(), e);
            }
        }
    }
}
