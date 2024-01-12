package io.neonbee.endpoint.odatav4.internal.olingo.expression;

import static com.google.common.truth.Truth.assertThat;
import static io.neonbee.test.base.NeonBeeTestBase.LONG_RUNNING_TEST;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.EdmPrimitiveTypeKind;
import org.apache.olingo.commons.api.edm.constants.EdmTypeKind;
import org.apache.olingo.commons.core.edm.EdmPropertyImpl;
import org.apache.olingo.commons.core.edm.EdmTypeImpl;
import org.apache.olingo.server.api.uri.UriInfoResource;
import org.apache.olingo.server.api.uri.UriResourcePrimitiveProperty;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor;
import org.apache.olingo.server.core.uri.queryoption.OrderByItemImpl;
import org.apache.olingo.server.core.uri.queryoption.OrderByOptionImpl;
import org.apache.olingo.server.core.uri.queryoption.expression.MemberImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.vertx.ext.web.RoutingContext;

@Tag(LONG_RUNNING_TEST)
class OrderExpressionExecutorTest {
    private final RoutingContext routingContext = mock(RoutingContext.class);

    @Test
    @DisplayName("Ordering by single property (strings must be case-insensitive)")
    void executeOrderOptionSinglePropertyTest() {
        Entity entity1 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "Sales Cloud"));
        Entity entity2 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "SAP Marketing Cloud"));
        Entity entity3 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "SAP Commerce"));

        List<Entity> entityList = new ArrayList<>(List.of(entity1, entity2, entity3));
        List<Entity> expectedEntityListAsc = new ArrayList<>(List.of(entity1, entity3, entity2));

        EdmTypeImpl edmType = mock(EdmTypeImpl.class);
        when(edmType.getKind()).thenReturn(EdmTypeKind.PRIMITIVE);
        when(edmType.toString()).thenReturn("Edm.String");

        EdmPropertyImpl edmProperty = mock(EdmPropertyImpl.class);
        when(edmProperty.getType()).thenReturn(edmType);
        when(edmProperty.getName()).thenReturn("testStringProperty");

        UriResourcePrimitiveProperty uriResourcePrimitiveProperty = mock(UriResourcePrimitiveProperty.class);
        when(uriResourcePrimitiveProperty.getProperty()).thenReturn(edmProperty);

        UriInfoResource resourcePath = mock(UriInfoResource.class);
        when(resourcePath.getUriResourceParts()).thenReturn(List.of(uriResourcePrimitiveProperty));

        MemberImpl member = mock(MemberImpl.class);
        when(member.getResourcePath()).thenReturn(resourcePath);

        OrderByItemImpl orderByItem = mock(OrderByItemImpl.class);
        when(orderByItem.getExpression()).thenReturn(member);

        OrderByOptionImpl orderByOption = mock(OrderByOptionImpl.class);
        when(orderByOption.getOrders()).thenReturn(List.of(orderByItem));

        assertThat(OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, entityList))
                .containsExactlyElementsIn(expectedEntityListAsc).inOrder();

        List<Entity> expectedEntityListDesc = new ArrayList<>(List.of(entity2, entity3, entity1));
        when(orderByItem.isDescending()).thenReturn(true);
        assertThat(OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, entityList))
                .containsExactlyElementsIn(expectedEntityListDesc).inOrder();
    }

    @Test
    @DisplayName("Ordering by multiple properties (strings must be case-insensitive)")
    void executeOrderOptionMultiplePropertiesTest() {
        Entity entity1 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "a"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 1));
        Entity entity2 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "c"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 2));
        Entity entity3 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "B"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 3));
        Entity entity4 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "c"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 1));

        List<Entity> entityList = new ArrayList<>(List.of(entity1, entity2, entity3, entity4));

        // Test ASC ordering
        List<Entity> expectedEntityListAsc = new ArrayList<>(List.of(entity1, entity3, entity4, entity2));

        EdmTypeImpl edmType1 = mock(EdmTypeImpl.class);
        when(edmType1.getKind()).thenReturn(EdmTypeKind.PRIMITIVE);
        when(edmType1.toString()).thenReturn(EdmPrimitiveTypeKind.String.toString());

        EdmTypeImpl edmType2 = mock(EdmTypeImpl.class);
        when(edmType2.getKind()).thenReturn(EdmTypeKind.PRIMITIVE);
        when(edmType2.toString()).thenReturn(EdmPrimitiveTypeKind.Int32.toString());

        EdmPropertyImpl edmProperty1 = mock(EdmPropertyImpl.class);
        when(edmProperty1.getType()).thenReturn(edmType1);
        when(edmProperty1.getName()).thenReturn("testStringProperty");

        EdmPropertyImpl edmProperty2 = mock(EdmPropertyImpl.class);
        when(edmProperty2.getType()).thenReturn(edmType2);
        when(edmProperty2.getName()).thenReturn("testNumberProperty");

        UriResourcePrimitiveProperty uriResourcePrimitiveProperty1 = mock(UriResourcePrimitiveProperty.class);
        when(uriResourcePrimitiveProperty1.getProperty()).thenReturn(edmProperty1);

        UriResourcePrimitiveProperty uriResourcePrimitiveProperty2 = mock(UriResourcePrimitiveProperty.class);
        when(uriResourcePrimitiveProperty2.getProperty()).thenReturn(edmProperty2);

        UriInfoResource resourcePath1 = mock(UriInfoResource.class);
        when(resourcePath1.getUriResourceParts()).thenReturn(List.of(uriResourcePrimitiveProperty1));

        UriInfoResource resourcePath2 = mock(UriInfoResource.class);
        when(resourcePath2.getUriResourceParts()).thenReturn(List.of(uriResourcePrimitiveProperty2));

        MemberImpl member1 = mock(MemberImpl.class);
        when(member1.getResourcePath()).thenReturn(resourcePath1);

        MemberImpl member2 = mock(MemberImpl.class);
        when(member2.getResourcePath()).thenReturn(resourcePath2);

        OrderByItemImpl orderByItem1 = mock(OrderByItemImpl.class);
        when(orderByItem1.getExpression()).thenReturn(member1);

        OrderByItemImpl orderByItem2 = mock(OrderByItemImpl.class);
        when(orderByItem2.getExpression()).thenReturn(member2);

        OrderByOptionImpl orderByOption = mock(OrderByOptionImpl.class);
        when(orderByOption.getOrders()).thenReturn(List.of(orderByItem1, orderByItem2));

        assertThat(OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, entityList))
                .containsExactlyElementsIn(expectedEntityListAsc).inOrder();

        // Test DESC ordering
        List<Entity> expectedEntityListDesc = new ArrayList<>(List.of(entity2, entity4, entity3, entity1));

        when(orderByItem1.isDescending()).thenReturn(true);
        when(orderByItem2.isDescending()).thenReturn(true);

        assertThat(OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, entityList))
                .containsExactlyElementsIn(expectedEntityListDesc).inOrder();
    }

    @Test
    @DisplayName("Ordering has to do nothing with the entity list in case of an invalid OrderByOption")
    void executeOrderOptionInvalidOrderByOptionTest() {
        Entity entity1 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "Sales Cloud"));
        Entity entity2 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "SAP Marketing Cloud"));
        Entity entity3 = new Entity() //
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "SAP Commerce"));

        List<Entity> entityList = new ArrayList<>(List.of(entity1, entity2, entity3));
        List<Entity> expectedEntityListAsc = new ArrayList<>(List.of(entity1, entity2, entity3));

        TestExpression member = mock(TestExpression.class);

        OrderByItemImpl orderByItem = mock(OrderByItemImpl.class);
        when(orderByItem.getExpression()).thenReturn(member);

        OrderByOptionImpl orderByOption = mock(OrderByOptionImpl.class);
        when(orderByOption.getOrders()).thenReturn(List.of(orderByItem));

        assertThat(OrderExpressionExecutor.executeOrderOption(routingContext, orderByOption, entityList))
                .containsExactlyElementsIn(expectedEntityListAsc).inOrder();
    }

    @Test
    @DisplayName("OrderExpressionExecutor must also work with null values")
    @SuppressWarnings("JavaUtilDate")
    void orderByShouldHandleNullValuesTest() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

        Date date1 = dateFormat.parse("1970-10-10");
        long long1 = date1.getTime();
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTimeInMillis(date1.getTime());
        Entity entity1 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity1"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("A7CF6C58-31FF-4B12-9670-F4FD80B2E82D")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "a-string"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 111))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, date1))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long1)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, new BigInteger("-1")))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long1)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar1))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, Boolean.TRUE))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.MAX_VALUE))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, Float.MAX_VALUE))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE, Double.MIN_VALUE))
                .addProperty(new Property(null, "testBinaryProperty", ValueType.PRIMITIVE,
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
                                .getBytes(UTF_8)));

        Date date2 = dateFormat.parse("2010-12-01");
        long long2 = date2.getTime();
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTimeInMillis(date2.getTime());
        Entity entity2 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity2"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("96FF1CC8-A538-4362-B050-D5DC7A17169C")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 222))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, date2.getTime()))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long2)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, Long.valueOf(long2)))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long2)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar2))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, Boolean.TRUE))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.valueOf("1000")))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, new BigDecimal(1000)))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE, BigDecimal.valueOf(1.5f)))
                .addProperty(new Property(null, "testBinaryProperty", ValueType.PRIMITIVE,
                        "File Content 2".getBytes(UTF_8)));

        Date date3 = dateFormat.parse("2000-02-10");
        long long3 = date3.getTime();
        Calendar calendar3 = Calendar.getInstance();
        calendar3.setTimeInMillis(date3.getTime());
        Entity entity3 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity3"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("7A299F35-B667-4C6B-8280-A9AFCEF83BB7")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "c-string"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE,
                        Long.valueOf(calendar3.getTimeInMillis())))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long3)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, Long.valueOf(long3)))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long3)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar3))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, Boolean.TRUE))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.valueOf("250")))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, 0d))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE, Float.valueOf("10.512233")))
                .addProperty(new Property(null, "testBinaryProperty", ValueType.PRIMITIVE, null));

        Date date4 = dateFormat.parse("2019-06-21");
        long long4 = date4.getTime();
        Calendar calendar4 = Calendar.getInstance();
        calendar4.setTimeInMillis(date4.getTime());
        Entity entity4 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity4"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("5ad5e2d8-6b53-4382-8b8a-11899a0daf5d")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "d-string"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 444))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, date4))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long4)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, Long.valueOf(long4)))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long4)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar4))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, false))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.valueOf("0")))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, BigDecimal.ZERO))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, (byte) 0xa)) //
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE, Integer.parseInt("5000")))
                .addProperty(
                        new Property(null, "testBinaryProperty", ValueType.PRIMITIVE, "Content 0".getBytes(UTF_8)));

        Entity entity5 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity5"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("3495ca30-14c2-44c9-8ab7-2635821411fa")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "e-string"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, BigInteger.valueOf(555)))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, (byte) 'f')) //
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE,
                        BigInteger.valueOf(Long.MAX_VALUE)))
                .addProperty(
                        new Property(null, "testBinaryProperty", ValueType.PRIMITIVE, "Content 0".getBytes(UTF_8)));

        Date date6 = dateFormat.parse("2011-06-21");
        long long6 = date6.getTime();
        Calendar calendar6 = Calendar.getInstance();
        calendar6.setTimeInMillis(date6.getTime());
        byte[] byteArray = "Another File Content 3".getBytes(UTF_8);
        // Convert byte[] to Byte[]
        Byte[] byteArrayToBeTested = new Byte[byteArray.length];
        Arrays.setAll(byteArrayToBeTested, i -> byteArray[i]);

        Entity entity6 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity6"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("310a3468-3ca6-4da2-a85a-5e78dac2e951")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "f-string"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 666))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, date6))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long6)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, Long.valueOf(long6)))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long6)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar6))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, Boolean.FALSE))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.valueOf("-1")))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, -1f))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, (byte) 0b11111111))
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE,
                        BigInteger.valueOf(Integer.MAX_VALUE)))
                .addProperty(new Property(null, "testBinaryProperty", ValueType.PRIMITIVE, byteArrayToBeTested));

        List<Entity> entityList = new ArrayList<>(List.of(entity1, entity2, entity3, entity4, entity5, entity6));

        // Sort by a String property should sort null value last
        List<Entity> expectedEntityList1 = List.of(entity1, entity3, entity4, entity5, entity6, entity2);
        EntityComparator entityComperatorString =
                new EntityComparator(routingContext, "testStringProperty", false, EdmPrimitiveTypeKind.String);
        Collections.sort(entityList, entityComperatorString);
        assertThat(entityList).isEqualTo(expectedEntityList1);

        // Sort by Integer property and descending should put null value first
        List<Entity> expectedEntityList2 =
                new ArrayList<>(List.of(entity3, entity6, entity5, entity4, entity2, entity1));
        EntityComparator entityComperatorInt =
                new EntityComparator(routingContext, "testNumberProperty", true, EdmPrimitiveTypeKind.Int32);
        Collections.sort(entityList, entityComperatorInt);
        assertThat(entityList).isEqualTo(expectedEntityList2);

        // Sort by Date property and descending should put null value first
        List<Entity> expectedEntityList3 =
                new ArrayList<>(List.of(entity5, entity4, entity6, entity2, entity3, entity1));
        EntityComparator entityComperatorDate =
                new EntityComparator(routingContext, "testDateProperty", true, EdmPrimitiveTypeKind.Date);
        Collections.sort(entityList, entityComperatorDate);
        assertThat(entityList).isEqualTo(expectedEntityList3);

        // Sort by Time property and descending should put null value first
        List<Entity> expectedEntityList4 =
                new ArrayList<>(List.of(entity5, entity4, entity6, entity2, entity3, entity1));
        EntityComparator entityComperatorTime =
                new EntityComparator(routingContext, "testTimeProperty", true, EdmPrimitiveTypeKind.Date);
        Collections.sort(entityList, entityComperatorTime);
        assertThat(entityList).isEqualTo(expectedEntityList4);

        // Sort by Long property and descending should put null value first
        List<Entity> expectedEntityList5 =
                new ArrayList<>(List.of(entity5, entity4, entity6, entity2, entity3, entity1));
        EntityComparator entityComperatorLong =
                new EntityComparator(routingContext, "testLongProperty", true, EdmPrimitiveTypeKind.Int64);
        Collections.sort(entityList, entityComperatorLong);
        assertThat(entityList).isEqualTo(expectedEntityList5);

        // Sort by Date property and descending should put null value first
        List<Entity> expectedEntityList6 =
                new ArrayList<>(List.of(entity5, entity4, entity6, entity2, entity3, entity1));
        EntityComparator entityComperatorTimestamp =
                new EntityComparator(routingContext, "testTimestampProperty", true, EdmPrimitiveTypeKind.Date);
        Collections.sort(entityList, entityComperatorTimestamp);
        assertThat(entityList).isEqualTo(expectedEntityList6);

        // Sort by Calendar property and descending should put null value first
        List<Entity> expectedEntityList7 =
                new ArrayList<>(List.of(entity5, entity4, entity6, entity2, entity3, entity1));
        EntityComparator entityComperatorCalendar =
                new EntityComparator(routingContext, "testCalendarProperty", true, EdmPrimitiveTypeKind.Date);
        Collections.sort(entityList, entityComperatorCalendar);
        assertThat(entityList).isEqualTo(expectedEntityList7);

        // Sort by Boolean property and descending should put null value first
        List<Entity> expectedEntityList8 =
                new ArrayList<>(List.of(entity5, entity2, entity3, entity1, entity4, entity6));
        EntityComparator entityComperatorBoolean =
                new EntityComparator(routingContext, "testBooleanProperty", true, EdmPrimitiveTypeKind.Boolean);
        Collections.sort(entityList, entityComperatorBoolean);
        assertThat(entityList).isEqualTo(expectedEntityList8);

        // Sort by Short property and descending should put null value first
        List<Entity> expectedEntityList9 =
                new ArrayList<>(List.of(entity5, entity1, entity2, entity3, entity4, entity6));
        EntityComparator entityComperatorShort =
                new EntityComparator(routingContext, "testShortProperty", true, EdmPrimitiveTypeKind.Int16);
        Collections.sort(entityList, entityComperatorShort);
        assertThat(entityList).isEqualTo(expectedEntityList9);

        // Sort by Double property and descending should put null value first
        List<Entity> expectedEntityList10 =
                new ArrayList<>(List.of(entity5, entity1, entity2, entity3, entity4, entity6));
        EntityComparator entityComperatorDouble =
                new EntityComparator(routingContext, "testDoubleProperty", true, EdmPrimitiveTypeKind.Double);
        Collections.sort(entityList, entityComperatorDouble);
        assertThat(entityList).isEqualTo(expectedEntityList10);

        // Sort by Guid property and descending should put null value first
        List<Entity> expectedEntityList11 =
                new ArrayList<>(List.of(entity3, entity4, entity5, entity6, entity1, entity2));
        EntityComparator entityComperatorGuid =
                new EntityComparator(routingContext, "testGuidProperty", true, EdmPrimitiveTypeKind.Guid);
        Collections.sort(entityList, entityComperatorGuid);
        assertThat(entityList).isEqualTo(expectedEntityList11);

        // Sort by Binary property and descending should put null value first
        List<Entity> expectedEntityList12 =
                new ArrayList<>(List.of(entity4, entity5, entity2, entity6, entity1, entity3));
        EntityComparator entityComperatorBinary =
                new EntityComparator(routingContext, "testBinaryProperty", false, EdmPrimitiveTypeKind.Binary);
        Collections.sort(entityList, entityComperatorBinary);
        assertThat(entityList).isEqualTo(expectedEntityList12);

        // Sort by Byte property and descending should put null value first
        List<Entity> expectedEntityList13 =
                new ArrayList<>(List.of(entity6, entity4, entity5, entity2, entity1, entity3));
        EntityComparator entityComperatorByte =
                new EntityComparator(routingContext, "testByteProperty", false, EdmPrimitiveTypeKind.Byte);
        Collections.sort(entityList, entityComperatorByte);
        assertThat(entityList).isEqualTo(expectedEntityList13);

        // Sort by Decimal property and descending should put null value first
        List<Entity> expectedEntityList14 =
                new ArrayList<>(List.of(entity1, entity2, entity3, entity4, entity6, entity5));
        EntityComparator entityComperatorDecimal =
                new EntityComparator(routingContext, "testDecimalProperty", false, EdmPrimitiveTypeKind.Decimal);
        Collections.sort(entityList, entityComperatorDecimal);
        assertThat(entityList).isEqualTo(expectedEntityList14);
    }

    @Test
    @DisplayName("OrderExpressionExecutor should throw meaningful errors")
    @SuppressWarnings({ "JavaInstantGetSecondsGetNano", "JavaUtilDate" })
    void orderByShouldThrowMeaningfulErrorTest() throws ParseException {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH);

        Date date1 = dateFormat.parse("1970-10-10");
        long long1 = date1.getTime();
        Calendar calendar1 = Calendar.getInstance();
        calendar1.setTimeInMillis(date1.getTime());
        Entity entity1 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity1"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE, "NotAUuidButAString"))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, Integer.valueOf(1337)))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, 111))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, date1))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long1)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, new BigInteger("-1")))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long1)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar1))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, 1))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.MAX_VALUE))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, Float.MAX_VALUE))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE, 0d))
                .addProperty(new Property(null, "testBinaryProperty", ValueType.PRIMITIVE,
                        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum. Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia deserunt mollit anim id est laborum."
                                .getBytes(UTF_8)));

        Date date2 = dateFormat.parse("2010-12-01");
        long long2 = date2.getTime();
        Calendar calendar2 = Calendar.getInstance();
        calendar2.setTimeInMillis(date2.getTime());
        Entity entity2 = new Entity() //
                .addProperty(new Property(null, "name", ValueType.PRIMITIVE, "entity2"))
                .addProperty(new Property(null, "testGuidProperty", ValueType.PRIMITIVE,
                        UUID.fromString("96FF1CC8-A538-4362-B050-D5DC7A17169C")))
                .addProperty(new Property(null, "testStringProperty", ValueType.PRIMITIVE, "Not null"))
                .addProperty(new Property(null, "testNumberProperty", ValueType.PRIMITIVE, new ArrayList<String>()))
                .addProperty(new Property(null, "testDateProperty", ValueType.PRIMITIVE, date2.toInstant().getNano()))
                .addProperty(new Property(null, "testTimeProperty", ValueType.PRIMITIVE, new Time(long2)))
                .addProperty(new Property(null, "testLongProperty", ValueType.PRIMITIVE, Long.valueOf(long2)))
                .addProperty(new Property(null, "testTimestampProperty", ValueType.PRIMITIVE, new Timestamp(long2)))
                .addProperty(new Property(null, "testCalendarProperty", ValueType.PRIMITIVE, calendar2))
                .addProperty(new Property(null, "testBooleanProperty", ValueType.PRIMITIVE, Boolean.TRUE))
                .addProperty(new Property(null, "testShortProperty", ValueType.PRIMITIVE, Short.valueOf("1000")))
                .addProperty(new Property(null, "testDoubleProperty", ValueType.PRIMITIVE, false))
                .addProperty(new Property(null, "testByteProperty", ValueType.PRIMITIVE, null))
                .addProperty(new Property(null, "testDecimalProperty", ValueType.PRIMITIVE, "1.5f".getBytes(UTF_8)))
                .addProperty(new Property(null, "testBinaryProperty", ValueType.PRIMITIVE, Integer.valueOf(-15)));

        List<Entity> entityList = new ArrayList<>(List.of(entity1, entity2));

        EntityComparator entityComperatorBinary =
                new EntityComparator(routingContext, "testBinaryProperty", true, EdmPrimitiveTypeKind.Binary);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorBinary)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testBinaryProperty. The types of the compared values are Integer and byte[] but both must be one of: byte[], Byte[]");

        EntityComparator entityComperatorNumber =
                new EntityComparator(routingContext, "testNumberProperty", true, EdmPrimitiveTypeKind.Int64);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorNumber)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testNumberProperty. The types of the compared values are ArrayList and Integer but both must be one of: Short, Byte, Integer, Long, BigInteger");

        EntityComparator entityComperatorDecimal =
                new EntityComparator(routingContext, "testDecimalProperty", true, EdmPrimitiveTypeKind.Decimal);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorDecimal)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testDecimalProperty. The types of the compared values are byte[] and Double but both must be one of: Short, Byte, Integer, Long, BigInteger, BigDecimal, Double, Float");

        EntityComparator entityComperatorDouble =
                new EntityComparator(routingContext, "testDoubleProperty", true, EdmPrimitiveTypeKind.Double);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorDouble)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testDoubleProperty. The types of the compared values are Boolean and Float but both must be one of: Short, Byte, Integer, Long, BigDecimal, Double, Float");

        EntityComparator entityComperatorDate =
                new EntityComparator(routingContext, "testDateProperty", true, EdmPrimitiveTypeKind.Date);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorDate)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testDateProperty. The types of the compared values are Integer and Date but both must be one of: Calendar, Date, Timestamp, Time, Long, LocalDate, LocalDateTime, Instant");

        EntityComparator entityComperatorBoolean =
                new EntityComparator(routingContext, "testBooleanProperty", true, EdmPrimitiveTypeKind.Boolean);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorBoolean)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testBooleanProperty. The types of the compared values are Boolean and Integer but both must be one of: Boolean");

        EntityComparator entityComperatorString =
                new EntityComparator(routingContext, "testStringProperty", true, EdmPrimitiveTypeKind.String);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorString)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testStringProperty. The types of the compared values are String and Integer but both must be one of: String");

        EntityComparator entityComperatorGuid =
                new EntityComparator(routingContext, "testGuidProperty", true, EdmPrimitiveTypeKind.Guid);
        assertThat(assertThrows(IllegalArgumentException.class,
                () -> Collections.sort(entityList, entityComperatorGuid)).getMessage()).isEqualTo(
                        "org.apache.olingo.server.api.ODataApplicationException: An error has occurred while comparing two values of property testGuidProperty. The types of the compared values are UUID and String but both must be one of: UUID");
    }

    @SuppressWarnings("rawtypes")
    @Test
    void classDefinitionTest() throws Exception {
        Constructor[] constructors = OrderExpressionExecutor.class.getDeclaredConstructors();
        // The class must only have one (private) constructor
        assertThat(constructors.length).isEqualTo(1);

        Constructor constructor = constructors[0];
        // The constructor must be inaccessible
        assertThat(constructor.canAccess(null)).isFalse();

        // To get the full test coverage, set it to accessible and test it
        constructor.setAccessible(true); // NOPMD
        assertThat(constructor.newInstance().getClass()).isNotNull();
    }

    public static class TestExpression implements Expression {
        @Override
        public <T> T accept(ExpressionVisitor<T> visitor) {
            return null;
        }
    }
}
