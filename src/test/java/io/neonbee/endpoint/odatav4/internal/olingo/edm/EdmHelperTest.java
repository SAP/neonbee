package io.neonbee.internal.processor.odata.edm;

import static com.google.common.truth.Truth.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Calendar;
import java.util.TimeZone;

import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeException;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.provider.CsdlProperty;
import org.apache.olingo.commons.core.edm.EdmPropertyImpl;
import org.apache.olingo.server.api.ODataApplicationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EdmHelperTest {

    @Test
    @DisplayName("Test getEdmPrimitiveTypeKindByPropertyType method")
    void getEdmPrimitiveTypeKindByPropertyTypeTest() throws ODataApplicationException {
        assertThat(EdmHelper.getEdmPrimitiveTypeKindByPropertyType("String")).isEqualTo(EdmPrimitiveTypeKind.String);
        assertThat(EdmHelper.getEdmPrimitiveTypeKindByPropertyType("Boolean")).isEqualTo(EdmPrimitiveTypeKind.Boolean);
        assertThat(EdmHelper.getEdmPrimitiveTypeKindByPropertyType("Int32")).isEqualTo(EdmPrimitiveTypeKind.Int32);
        assertThat(EdmHelper.getEdmPrimitiveTypeKindByPropertyType("Int64")).isEqualTo(EdmPrimitiveTypeKind.Int64);
    }

    @Test
    @DisplayName("Test extractValueFromLiteral method")
    void extractValueFromLiteralTest() {
        // String IDs
        assertThat(EdmHelper.extractValueFromLiteral("'ID5'")).isEqualTo("ID5");
        assertThat(EdmHelper.extractValueFromLiteral("'10815'")).isEqualTo("10815");

        // Number ID
        assertThat(EdmHelper.extractValueFromLiteral("323")).isEqualTo("323");

        // Something that cannot be a valid ID
        assertThat(EdmHelper.extractValueFromLiteral("INVALID_ID_0815$")).isEqualTo("INVALID_ID_0815$");
    }

    @Test
    @DisplayName("Test isLocalDate")
    void isLocalDateTest() {
        assertThat(EdmHelper.isLocalDate("2010-01-01")).isTrue();
        assertThat(EdmHelper.isLocalDate("2010-12-31")).isTrue();
        assertThat(EdmHelper.isLocalDate("2010-2-31")).isFalse();
    }

    @Test
    @DisplayName("Test isLocalDateTime")
    void isLocalDateTimeTest() {
        assertThat(EdmHelper.isLocalDateTime("2010-01-01T05:32:09")).isTrue();
        assertThat(EdmHelper.isLocalDateTime("2010-01-01T05:32:09Z")).isFalse();
        assertThat(EdmHelper.isLocalDateTime("2010-2-31")).isFalse();
    }

    @Test
    @DisplayName("Test convert string to edm property")
    void convertStringValueToEdmStringProperty() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("account", EdmPrimitiveTypeKind.String);
        Property property = EdmHelper.convertStringValueToEdmProperty("SAP-Account", edmProperty);
        assertThat(property.getValue()).isEqualTo("SAP-Account");
    }

    @Test
    @DisplayName("Test convert string to edm int property")
    void convertStringValueToEdmInt32Property() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("age", EdmPrimitiveTypeKind.Int32);
        Property property = EdmHelper.convertStringValueToEdmProperty("12", edmProperty);
        assertThat(property.getValue()).isEqualTo(12);
    }

    @Test
    @DisplayName("Test convert string to edm boolean property")
    void convertStringValueToEdmBooleanProperty() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("male", EdmPrimitiveTypeKind.Boolean);
        Property property = EdmHelper.convertStringValueToEdmProperty("true", edmProperty);
        assertThat(property.getValue()).isEqualTo(Boolean.TRUE);
    }

    @Test
    @DisplayName("Test convert string to edm double property")
    void convertStringValueToEdmDoubleProperty() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("salary", EdmPrimitiveTypeKind.Double);
        Property property = EdmHelper.convertStringValueToEdmProperty("12.58", edmProperty);
        assertThat(property.getValue()).isEqualTo(12.58);
    }

    @Test
    @DisplayName("Test convert string to edm decimal property")
    void convertStringValueToEdmDecimalProperty() throws EdmPrimitiveTypeException {
        CsdlProperty csdlProperty = new CsdlProperty();
        csdlProperty.setName("salary");
        csdlProperty.setType(EdmPrimitiveTypeKind.Decimal.getFullQualifiedName());
        csdlProperty.setScale(2);
        csdlProperty.setPrecision(9);
        EdmProperty edmProperty = new EdmPropertyImpl(null, csdlProperty);
        Property property = EdmHelper.convertStringValueToEdmProperty("12.58", edmProperty);
        assertThat(((BigDecimal) property.getValue()).doubleValue()).isEqualTo(12.58);
    }

    @Test
    @DisplayName("Test convert string to edm date property")
    void convertStringValueToEdmDateProperty() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("birthdate", EdmPrimitiveTypeKind.Date);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 1970);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DATE, 1);
        LocalDate localDate = LocalDate.ofInstant(cal.toInstant(), cal.getTimeZone().toZoneId());
        Property property = EdmHelper.convertStringValueToEdmProperty("1970-01-01", edmProperty);
        Calendar value = (Calendar) property.getValue();
        LocalDate localDateValue = LocalDate.ofInstant(value.toInstant(), value.getTimeZone().toZoneId());
        assertThat(localDateValue).isEqualTo(localDate);
    }

    @Test
    @DisplayName("Test convert string to edm time property")
    void convertStringValueToEdmTimeOfDayProperty() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("birthtime", EdmPrimitiveTypeKind.TimeOfDay);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.YEAR, 1970);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DATE, 1);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        LocalTime localTime = LocalTime.ofInstant(cal.toInstant(), cal.getTimeZone().toZoneId());
        Property property = EdmHelper.convertStringValueToEdmProperty("12:00:00", edmProperty);
        Calendar value = (Calendar) property.getValue();
        LocalTime localTimeValue = LocalTime.ofInstant(value.toInstant(), value.getTimeZone().toZoneId());
        assertThat(localTimeValue).isEqualTo(localTime);
    }

    @Test
    @DisplayName("Test convert string to edm date time property")
    void convertStringValueToEdmDateTimeOffsetProperty() throws EdmPrimitiveTypeException {
        EdmProperty edmProperty = buildProperty("birthtime", EdmPrimitiveTypeKind.DateTimeOffset);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Berlin"));
        cal.set(Calendar.YEAR, 1970);
        cal.set(Calendar.MONTH, 0);
        cal.set(Calendar.DATE, 1);
        cal.set(Calendar.HOUR_OF_DAY, 12);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp localDateTime = new Timestamp(cal.toInstant().toEpochMilli());
        Property property =
                EdmHelper.convertStringValueToEdmProperty("1970-01-01T12:00:00+01:00[Europe/Berlin]", edmProperty);
        Timestamp localDateTimeValue = (Timestamp) property.getValue();
        assertThat(localDateTimeValue).isEqualTo(localDateTime);
    }

    private EdmProperty buildProperty(String name, EdmPrimitiveTypeKind type) {
        CsdlProperty csdlProperty = new CsdlProperty();
        csdlProperty.setName(name);
        csdlProperty.setType(type.getFullQualifiedName());
        return new EdmPropertyImpl(null, csdlProperty);
    }
}
