package io.neonbee.internal.processor.odata.edm;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.AbstractMap;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.server.api.OData;
import org.apache.olingo.server.core.ODataImpl;

@SuppressWarnings("checkstyle:JavadocVariable")
public final class EdmConstants {
    public static final int MILLISECONDS_PER_DAY = 24 * 60 * 60 * 1000;

    public static final int FACTOR_SECOND_INT = 1000;

    public static final BigDecimal FACTOR_SECOND = BigDecimal.valueOf(FACTOR_SECOND_INT);

    public static final BigInteger EDM_SBYTE_MIN = BigInteger.valueOf(Byte.MIN_VALUE);

    public static final BigInteger EDM_SBYTE_MAX = BigInteger.valueOf(Byte.MAX_VALUE);

    public static final BigInteger EDM_BYTE_MIN = BigInteger.ZERO;

    public static final BigInteger EDM_BYTE_MAX = BigInteger.valueOf((Byte.MAX_VALUE * 2L) + 1);

    public static final BigInteger EDM_INT16_MIN = BigInteger.valueOf(Short.MIN_VALUE);

    public static final BigInteger EDM_INT16_MAX = BigInteger.valueOf(Short.MAX_VALUE);

    public static final BigInteger EDM_INT32_MIN = BigInteger.valueOf(Integer.MIN_VALUE);

    public static final BigInteger EDM_INT32_MAX = BigInteger.valueOf(Integer.MAX_VALUE);

    public static final BigInteger EDM_INT64_MIN = BigInteger.valueOf(Long.MIN_VALUE);

    public static final BigInteger EDM_INT64_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    public static final BigDecimal EDM_SINGLE_MIN = BigDecimal.valueOf(Float.MIN_VALUE);

    public static final BigDecimal EDM_SINGLE_MAX = BigDecimal.valueOf(Float.MAX_VALUE);

    public static final int IS_EQUAL = 0;

    public static final int LESS_THAN = -1;

    public static final int GREATER_THAN = 1;

    public static final List<Class<?>> EDM_BINARY_JAVA_TYPES = List.of(byte[].class, Byte[].class);

    public static final List<Class<?>> EDM_INT16_INT32_INT64_BYTE_SBYTE_JAVA_TYPES =
            List.of(Short.class, Byte.class, Integer.class, Long.class, BigInteger.class);

    public static final List<Class<?>> EDM_DECIMAL_DURATION_JAVA_TYPES = List.of(Short.class, Byte.class, Integer.class,
            Long.class, BigInteger.class, BigDecimal.class, Double.class, Float.class);

    public static final List<Class<?>> EDM_SINGLE_DOUBLE_JAVA_TYPES =
            List.of(Short.class, Byte.class, Integer.class, Long.class, BigDecimal.class, Double.class, Float.class);

    public static final List<Class<?>> EDM_DATE_TIMEOFDAY_DATETIMEOFFSET_JAVA_TYPES = List.of(Calendar.class,
            Date.class, Timestamp.class, Time.class, Long.class, LocalDate.class, LocalDateTime.class, Instant.class);

    public static final List<Class<?>> EDM_BOOLEAN_JAVA_TYPES = List.of(Boolean.class);

    public static final List<Class<?>> EDM_STRING_JAVA_TYPES = List.of(String.class);

    public static final List<Class<?>> EDM_GUID_JAVA_TYPES = List.of(UUID.class);

    public static final EdmPrimitiveType PRIMITIVE_NULL = EdmPrimitiveNull.getInstance();

    // Private as it should only be used within this class to create the primitive type instances.
    private static final OData ODATA = new ODataImpl();

    public static final EdmPrimitiveType PRIMITIVE_STRING =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.String);

    public static final EdmPrimitiveType PRIMITIVE_BOOLEAN =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Boolean);

    public static final EdmPrimitiveType PRIMITIVE_DATE_TIME_OFFSET =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.DateTimeOffset);

    public static final EdmPrimitiveType PRIMITIVE_DATE = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Date);

    public static final EdmPrimitiveType PRIMITIVE_TIME_OF_DAY =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.TimeOfDay);

    public static final EdmPrimitiveType PRIMITIVE_DURATION =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Duration);

    public static final EdmPrimitiveType PRIMITIVE_SBYTE =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.SByte);

    public static final EdmPrimitiveType PRIMITIVE_BYTE = ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Byte);

    public static final EdmPrimitiveType PRIMITIVE_INT16 =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Int16);

    public static final EdmPrimitiveType PRIMITIVE_INT32 =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Int32);

    public static final EdmPrimitiveType PRIMITIVE_INT64 =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Int64);

    public static final EdmPrimitiveType PRIMITIVE_DECIMAL =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Decimal);

    public static final EdmPrimitiveType PRIMITIVE_SINGLE =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Single);

    public static final EdmPrimitiveType PRIMITIVE_DOUBLE =
            ODATA.createPrimitiveTypeInstance(EdmPrimitiveTypeKind.Double);

    public static final Map<EdmPrimitiveType, Class<?>> TYPE_MAPPINGS =
            Map.ofEntries(new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_BYTE, BigInteger.class),
                    new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_SBYTE, BigInteger.class),
                    new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_INT16, BigInteger.class),
                    new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_INT32, BigInteger.class),
                    new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_INT64, BigInteger.class),
                    new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_DECIMAL, BigDecimal.class),
                    new AbstractMap.SimpleEntry<EdmPrimitiveType, Class<?>>(PRIMITIVE_SINGLE, BigDecimal.class));

    private EdmConstants() {
        // No need to instantiate
    }
}
