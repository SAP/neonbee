package io.neonbee.test.endpoint.odata.verticle;

import static io.neonbee.test.helper.ResourceHelper.TEST_RESOURCES;

import java.nio.file.Path;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.data.ValueType;
import org.apache.olingo.commons.api.edm.FullQualifiedName;

import io.neonbee.data.DataContext;
import io.neonbee.data.DataQuery;
import io.neonbee.entity.EntityVerticle;
import io.neonbee.entity.EntityWrapper;
import io.neonbee.internal.codec.EntityWrapperMessageCodec;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

public class TestService1EntityVerticle extends EntityVerticle {
    public static final FullQualifiedName TEST_ENTITY_SET_FQN =
            new FullQualifiedName("io.neonbee.test.TestService1", "AllPropertiesNullable");

    public static final String NULL = null;

    public static final JsonObject EXPECTED_ENTITY_DATA_1 = new JsonObject().put("KeyPropertyString", "id-0")
            .put("PropertyString", "a").put("PropertyChar", NULL)
            .put("PropertyString100",
                    "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut l")
            .put("PropertyLargeString", NULL).put("PropertyBinary", NULL).put("PropertyBinary100", NULL)
            .put("PropertyLargeBinary", NULL).put("PropertyBoolean", false).put("PropertyDate", "2014-05-24")
            .put("PropertyTime", NULL).put("PropertyDateTime", "2014-05-24T07:50:23.000005Z")
            .put("PropertyTimestamp", NULL).put("PropertyDecimal", NULL).put("PropertyDecimalFloat", NULL)
            .put("PropertyDouble", 0.15).put("PropertyUuid", NULL).put("PropertyInt32", 1).put("PropertyInt64", NULL);

    public static final JsonObject EXPECTED_ENTITY_DATA_2 = new JsonObject().put("KeyPropertyString", "id-1")
            .put("PropertyString", "c").put("PropertyChar", NULL)
            .put("PropertyString100",
                    "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean m")
            .put("PropertyLargeString", NULL).put("PropertyBinary", NULL).put("PropertyBinary100", NULL)
            .put("PropertyLargeBinary", NULL).put("PropertyBoolean", true).put("PropertyDate", "2013-04-23")
            .put("PropertyTime", NULL).put("PropertyDateTime", "2013-04-23T08:47:11.000004Z")
            .put("PropertyTimestamp", NULL).put("PropertyDecimal", NULL).put("PropertyDecimalFloat", NULL)
            .put("PropertyDouble", 1.25).put("PropertyUuid", NULL).put("PropertyInt32", 2).put("PropertyInt64", NULL);

    public static final JsonObject EXPECTED_ENTITY_DATA_3 = new JsonObject().put("KeyPropertyString", "id-2")
            .put("PropertyString", "B").put("PropertyChar", NULL)
            .put("PropertyString100",
                    "Li Europan lingues es membres del sam familie. Lor separat existentie es un myth. Por scientie, musi")
            .put("PropertyLargeString", NULL).put("PropertyBinary", NULL).put("PropertyBinary100", NULL)
            .put("PropertyLargeBinary", NULL).put("PropertyBoolean", false).put("PropertyDate", "2012-03-22")
            .put("PropertyTime", NULL).put("PropertyDateTime", "2012-03-22T09:46:15.000003Z")
            .put("PropertyTimestamp", NULL).put("PropertyDecimal", NULL).put("PropertyDecimalFloat", NULL)
            .put("PropertyDouble", 2.35).put("PropertyUuid", NULL).put("PropertyInt32", 3).put("PropertyInt64", NULL);

    public static final JsonObject EXPECTED_ENTITY_DATA_4 = new JsonObject().put("KeyPropertyString", "id.3")
            .put("PropertyString", "c").put("PropertyChar", NULL)
            .put("PropertyString100",
                    "abc def ghi jkl mno pqrs tuv wxyz ABC DEF GHI JKL MNO PQRS TUV WXYZ !\"§ $%& /() =?* '<> #|; ²³~ @`´.")
            .put("PropertyLargeString", NULL).put("PropertyBinary", NULL).put("PropertyBinary100", NULL)
            .put("PropertyLargeBinary", NULL).put("PropertyBoolean", true).put("PropertyDate", "2011-02-21")
            .put("PropertyTime", NULL).put("PropertyDateTime", "2011-02-21T10:45:51.000002Z")
            .put("PropertyTimestamp", NULL).put("PropertyDecimal", NULL).put("PropertyDecimalFloat", NULL)
            .put("PropertyDouble", Double.MAX_VALUE).put("PropertyUuid", NULL).put("PropertyInt32", 1)
            .put("PropertyInt64", NULL);

    public static final JsonObject EXPECTED_ENTITY_DATA_5 =
            new JsonObject().put("KeyPropertyString", "id-4").put("PropertyString", "D").put("PropertyChar", NULL)
                    .put("PropertyString100", "     ABCDEFGHIJKLMNOPQRSTUVWXYZ ").put("PropertyLargeString", NULL)
                    .put("PropertyBinary", NULL).put("PropertyBinary100", NULL).put("PropertyLargeBinary", NULL)
                    .put("PropertyBoolean", false).put("PropertyDate", "2010-01-20").put("PropertyTime", NULL)
                    .put("PropertyDateTime", "2010-01-20T11:30:05Z").put("PropertyTimestamp", NULL)
                    .put("PropertyDecimal", NULL).put("PropertyDecimalFloat", NULL).put("PropertyDouble", 1337.0815)
                    .put("PropertyUuid", NULL).put("PropertyInt32", 4).put("PropertyInt64", NULL);

    @Override
    public Future<Set<FullQualifiedName>> entityTypeNames() {
        return Future.succeededFuture(Set.of(TEST_ENTITY_SET_FQN));
    }

    @Override
    public Future<EntityWrapper> retrieveData(DataQuery query, DataContext context) {
        Entity entity1 = new Entity() //
                .addProperty(new Property(null, "KeyPropertyString", ValueType.PRIMITIVE, "id-0"))
                .addProperty(new Property(null, "PropertyString100", ValueType.PRIMITIVE,
                        "Lorem ipsum dolor sit amet, consetetur sadipscing elitr, sed diam nonumy eirmod tempor invidunt ut l"))
                .addProperty(new Property(null, "PropertyString", ValueType.PRIMITIVE, "a"))
                .addProperty(new Property(null, "PropertyInt32", ValueType.PRIMITIVE, 1))
                .addProperty(new Property(null, "PropertyDate", ValueType.PRIMITIVE, LocalDate.of(2014, 5, 24)))
                .addProperty(new Property(null, "PropertyDateTime", ValueType.PRIMITIVE,
                        LocalDateTime.of(2014, 5, 24, 7, 50, 23, 5000).toInstant(ZoneOffset.UTC)))
                .addProperty(new Property(null, "PropertyDouble", ValueType.PRIMITIVE, 0.15d))
                .addProperty(new Property(null, "PropertyBoolean", ValueType.PRIMITIVE, false));

        Entity entity2 = new Entity() //
                .addProperty(new Property(null, "KeyPropertyString", ValueType.PRIMITIVE, "id-1"))
                .addProperty(new Property(null, "PropertyString100", ValueType.PRIMITIVE,
                        "Lorem ipsum dolor sit amet, consectetuer adipiscing elit. Aenean commodo ligula eget dolor. Aenean m"))
                .addProperty(new Property(null, "PropertyString", ValueType.PRIMITIVE, "c"))
                .addProperty(new Property(null, "PropertyInt32", ValueType.PRIMITIVE, 2))
                .addProperty(new Property(null, "PropertyDate", ValueType.PRIMITIVE, LocalDate.of(2013, 4, 23)))
                .addProperty(new Property(null, "PropertyDateTime", ValueType.PRIMITIVE,
                        LocalDateTime.of(2013, 4, 23, 8, 47, 11, 4000).toInstant(ZoneOffset.UTC)))
                .addProperty(new Property(null, "PropertyDouble", ValueType.PRIMITIVE, 1.25d))
                .addProperty(new Property(null, "PropertyBoolean", ValueType.PRIMITIVE, true));

        Entity entity3 = new Entity() //
                .addProperty(new Property(null, "KeyPropertyString", ValueType.PRIMITIVE, "id-2"))
                .addProperty(new Property(null, "PropertyString100", ValueType.PRIMITIVE,
                        "Li Europan lingues es membres del sam familie. Lor separat existentie es un myth. Por scientie, musi"))
                .addProperty(new Property(null, "PropertyString", ValueType.PRIMITIVE, "B"))
                .addProperty(new Property(null, "PropertyInt32", ValueType.PRIMITIVE, 3))
                .addProperty(new Property(null, "PropertyDate", ValueType.PRIMITIVE, LocalDate.of(2012, 3, 22)))
                .addProperty(new Property(null, "PropertyDateTime", ValueType.PRIMITIVE,
                        LocalDateTime.of(2012, 3, 22, 9, 46, 15, 3000).toInstant(ZoneOffset.UTC)))
                .addProperty(new Property(null, "PropertyDouble", ValueType.PRIMITIVE, 2.35d))
                .addProperty(new Property(null, "PropertyBoolean", ValueType.PRIMITIVE, false));

        Entity entity4 = new Entity() //
                .addProperty(new Property(null, "KeyPropertyString", ValueType.PRIMITIVE, "id.3"))
                .addProperty(new Property(null, "PropertyString100", ValueType.PRIMITIVE,
                        "abc def ghi jkl mno pqrs tuv wxyz ABC DEF GHI JKL MNO PQRS TUV WXYZ !\"§ $%& /() =?* '<> #|; ²³~ @`´."))
                .addProperty(new Property(null, "PropertyString", ValueType.PRIMITIVE, "c"))
                .addProperty(new Property(null, "PropertyInt32", ValueType.PRIMITIVE, 1))
                .addProperty(new Property(null, "PropertyDate", ValueType.PRIMITIVE, LocalDate.of(2011, 2, 21)))
                .addProperty(new Property(null, "PropertyDateTime", ValueType.PRIMITIVE,
                        LocalDateTime.of(2011, 2, 21, 10, 45, 51, 2000).toInstant(ZoneOffset.UTC)))
                .addProperty(new Property(null, "PropertyDouble", ValueType.PRIMITIVE, Double.MAX_VALUE))
                .addProperty(new Property(null, "PropertyBoolean", ValueType.PRIMITIVE, true));

        Entity entity5 = new Entity();
        try {
            entity5 = entity5.addProperty(new Property(null, "KeyPropertyString", ValueType.PRIMITIVE, "id-4"))
                    .addProperty(new Property(null, "PropertyString100", ValueType.PRIMITIVE,
                            "     ABCDEFGHIJKLMNOPQRSTUVWXYZ "))
                    .addProperty(new Property(null, "PropertyString", ValueType.PRIMITIVE, "D"))
                    .addProperty(new Property(null, "PropertyInt32", ValueType.PRIMITIVE, 4))
                    .addProperty(new Property(null, "PropertyDate", ValueType.PRIMITIVE,
                            new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2010-01-20")))
                    .addProperty(new Property(null, "PropertyDateTime", ValueType.PRIMITIVE,
                            Instant.parse("2010-01-20T11:30:05Z")))
                    .addProperty(new Property(null, "PropertyDouble", ValueType.PRIMITIVE, Double.valueOf(1337.0815)))
                    .addProperty(new Property(null, "PropertyBoolean", ValueType.PRIMITIVE, false));

            return Future.succeededFuture(
                    new EntityWrapper(TEST_ENTITY_SET_FQN, List.of(entity1, entity2, entity3, entity4, entity5)));
        } catch (ParseException e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<EntityWrapper> createData(DataQuery query, DataContext context) {
        Optional<Buffer> bodyOptional = Optional.ofNullable(query.getBody());

        if (bodyOptional.isEmpty()) {
            return Future.failedFuture("Body is missing");
        }

        EntityWrapper ew = new EntityWrapperMessageCodec(getVertx()).decodeFromWire(0, query.getBody());
        Optional<Entity> entityOptional = Optional.ofNullable(ew.getEntity());

        if (entityOptional.isEmpty()) {
            return Future.failedFuture("EntityWrapper is empty");
        }

        return Future.succeededFuture(ew);
    }

    @Override
    public Future<EntityWrapper> deleteData(DataQuery query, DataContext context) {
        return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, (Entity) null));
    }

    @Override
    public Future<EntityWrapper> updateData(DataQuery query, DataContext context) {
        return Future.succeededFuture(new EntityWrapper(TEST_ENTITY_SET_FQN, (Entity) null));
    }

    public static Path getDeclaredEntityModel() {
        return TEST_RESOURCES.resolveRelated("TestService1.csn");
    }
}
