package io.neonbee.internal.processor.odata.edm;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmEntityType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriParameter;
import org.apache.olingo.server.api.uri.UriResourceEntitySet;

import io.neonbee.entity.EntityModelManager;
import io.neonbee.internal.processor.odata.expression.operands.ExpressionVisitorOperand;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

public final class EdmHelper {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final Pattern STRING_KEY_PATTERN = Pattern.compile("'(.+)'");

    private static final Pattern NUMBER_KEY_PATTERN = Pattern.compile("([0-9]+)");

    private static final Pattern LOCAL_DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-1][0-9]-[0-3][0-9]"); // yyyy-MM-dd

    private static final Pattern LOCAL_DATETIME_PATTERN =
            Pattern.compile("[0-9]{4}-[0-1][0-9]-[0-3][0-9]T[0-2][0-9]:[0-5][0-9]:[0-5][0-9]"); // yyyy-MM-ddTHH:mm:ss

    private EdmHelper() {
        // No need to instantiate
    }

    /**
     * Get the appropriate EdmPrimitiveTypeKind like 'Double', 'String' etc. for a provided property type.
     *
     * @param propertyType the property type as String value
     * @return the matching EdmPrimitiveTypeKind
     * @throws ODataApplicationException If no matching {@link EdmPrimitiveTypeKind} can be found
     */
    public static EdmPrimitiveTypeKind getEdmPrimitiveTypeKindByPropertyType(String propertyType)
            throws ODataApplicationException {
        return Stream.of(EdmPrimitiveTypeKind.values())
                .filter(e -> e.getFullQualifiedName().toString().equals(propertyType)
                        || e.getFullQualifiedName().toString().equals("Edm." + propertyType))
                .findFirst()
                .orElseThrow(() -> new ODataApplicationException(
                        "No matching EdmPrimitiveTypeKind found for propertyType: " + propertyType,
                        HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(), Locale.ENGLISH));
    }

    /**
     * Extracts the key (usually the id) from the OData key predicate found in the UriInfo. And add it to the passed
     * Entity.
     *
     * @param entity               the Entity to add the key predicate values
     * @param uriResourceEntitySet the OData request's uriResourceEntitySet object
     * @param routingContext       the RoutingContext used for correlated logging
     * @return the Entity filled with the values of the key predicate or an unmodified Entity if the key predicate could
     *         not be extracted
     */
    public static Entity addKeyPredicateValues(Entity entity, UriResourceEntitySet uriResourceEntitySet,
            RoutingContext routingContext) {
        try {
            EdmEntityType edmEntityType = uriResourceEntitySet.getEntityType();
            for (UriParameter uriParam : uriResourceEntitySet.getKeyPredicates()) {
                String propertyName = uriParam.getName();
                // Get the EdmPrimitiveTypeKind like Edm.String or Edm.Int32 of the key property
                EdmPrimitiveTypeKind edmPrimitiveTypeKind = getEdmPrimitiveTypeKindByPropertyType(
                        edmEntityType.getProperty(propertyName).getType().toString());

                // Get the value as String representation
                String valueAsString = extractValueFromLiteral(routingContext, uriParam.getText());

                // Transform the string value into a object of the related EdmType
                EdmPrimitiveType edmPrimitiveType =
                        EntityModelManager.getBufferedOData().createPrimitiveTypeInstance(edmPrimitiveTypeKind);
                EdmProperty edmProperty = (EdmProperty) edmEntityType.getProperty(propertyName);

                Object value = edmPrimitiveType.valueOfString(valueAsString, edmProperty.isNullable(),
                        edmProperty.getMaxLength(), edmProperty.getPrecision(), edmProperty.getScale(),
                        edmProperty.isUnicode(), edmPrimitiveType.getDefaultType());

                entity.addProperty(new Property(edmPrimitiveTypeKind.getFullQualifiedName().toString(), propertyName,
                        ValueType.PRIMITIVE, value));
            }
        } catch (Exception e) {
            LOGGER.correlateWith(routingContext).error("Failed to add key predicate to the passed entity", e);
        }
        return entity;
    }

    /**
     * Remove the leading and trailing single quotes (') from a provided literal. This is useful when working with
     * literals of type String, because they are wrapped with single quotes (') if provided in a query.
     *
     * @param literal String literal starting and ending with single quotes (')
     * @return a String containing only the value
     */
    public static String extractValueFromLiteral(String literal) {
        return extractValueFromLiteral(null, literal);
    }

    /**
     * Remove the leading and trailing single quotes (') from a provided literal. This is useful when working with
     * literals of type String, because they are wrapped with single quotes (') if provided in a query.
     *
     * @param routingContext RoutingContext instance to correlate the log message with
     * @param literal        String literal starting and ending with single quotes (')
     * @return a String containing only the value
     */
    public static String extractValueFromLiteral(RoutingContext routingContext, String literal) {
        try {
            if (String.valueOf(Boolean.TRUE).equalsIgnoreCase(literal)
                    || String.valueOf(Boolean.FALSE).equalsIgnoreCase(literal)) {
                return literal.toLowerCase(Locale.ENGLISH);
            } else {
                Matcher matcher;
                if ((matcher = STRING_KEY_PATTERN.matcher(literal)).matches()) {
                    return matcher.group(1);
                } else if ((matcher = NUMBER_KEY_PATTERN.matcher(literal)).matches()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            String msg = "Failed to extract value from literal";
            if (routingContext != null) {
                LOGGER.correlateWith(routingContext).error(msg, e);
            } else {
                LOGGER.error(msg, e);
            }
        }
        return literal;
    }

    /**
     * Throws an ODataApplicationException to indicate that a called OData function is not yet implemented.
     *
     * @throws ODataApplicationException with the HTTP status code 501 (Not Implemented)
     * @return nothing as it raises immediately.
     */
    public static ExpressionVisitorOperand throwNotImplementedODataException() throws ODataApplicationException {
        throw new ODataApplicationException("Not implemented", HttpStatusCode.NOT_IMPLEMENTED.getStatusCode(),
                Locale.ENGLISH);
    }

    /**
     * Checks if a date string is in the local date format of yyyy-MM-dd.
     *
     * @param dateString The date to be checked
     * @return true if the date string is in the local date format.
     */
    public static boolean isLocalDate(String dateString) {
        Matcher matcher = LOCAL_DATE_PATTERN.matcher(dateString);
        return matcher.matches();
    }

    /**
     * Checks if a date string is in the date time format of yyyy-MM-ddTHH:mm:ss.
     *
     * @param dateTimeString The date time to be checked
     * @return true if the date string is in the local date format.
     */
    public static boolean isLocalDateTime(String dateTimeString) {
        Matcher matcher = LOCAL_DATETIME_PATTERN.matcher(dateTimeString);
        return matcher.matches();
    }

    /**
     * Convert a string into an EdmProperty instance.
     *
     * @param valueAsString string value
     * @param edmProperty   an edm property instance
     * @return an edm property instance
     * @throws EdmPrimitiveTypeException If converting to {@link EdmProperty} is not possible.
     */
    public static Property convertStringValueToEdmProperty(String valueAsString, EdmProperty edmProperty)
            throws EdmPrimitiveTypeException {
        EdmPrimitiveType edmType = (EdmPrimitiveType) edmProperty.getType();
        Object value = edmType.valueOfString(valueAsString, edmProperty.isNullable(), edmProperty.getMaxLength(),
                edmProperty.getPrecision(), edmProperty.getScale(), edmProperty.isUnicode(), edmType.getDefaultType());
        return new Property(edmType.getName(), edmProperty.getName(), ValueType.PRIMITIVE, value);
    }
}
