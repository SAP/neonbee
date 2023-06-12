package io.neonbee.test.base;

import static com.google.common.truth.Truth.assertThat;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.apache.olingo.commons.api.edm.FullQualifiedName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ODataRequestTest {
    private final Map<String, Object> compositeMap = new HashMap<>();

    private static final String EXPECTED_SERVICE_ROOT_URL = "my-namespace";

    private ODataRequest odataRequest;

    @BeforeEach
    @DisplayName("Setup the base OData request")
    void setUp() {
        FullQualifiedName fqn = new FullQualifiedName("my-namespace", "my-entity");
        odataRequest = new ODataRequest(fqn);
        compositeMap.clear();
    }

    @Test
    @DisplayName("with $count set")
    void testGetUriWithCount() {
        odataRequest.setCount();
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity/$count");
        odataRequest.setCount().setKey(1).setProperty("ignore");
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity/$count");
    }

    @Test
    @DisplayName("with namespace and entity name")
    void testGetUriNamespace() {
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity");
    }

    @Test
    @DisplayName("with single string key")
    void testGetUriSingleStringKey() {
        odataRequest.setKey("0123");
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity('0123')");
    }

    @Test
    @DisplayName("with single long key")
    void testGetUriSingleLongKey() {
        odataRequest.setKey(1234);
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity(1234)");
    }

    @Test
    @DisplayName("with single date key")
    void testGetUriSingleDateKey() {
        odataRequest.setKey(LocalDate.of(2020, 2, 22));
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity(2020-02-22)");
    }

    @Test
    @DisplayName("with multiple valid keys")
    void testGetUriMultipleValidKeys() {
        compositeMap.put("ID", 123L);
        compositeMap.put("Name", "cheese");
        compositeMap.put("Description", "something");
        compositeMap.put("date", LocalDate.parse("2020-02-22").atStartOfDay().toLocalDate());
        odataRequest.setKey(compositeMap);
        String uri = odataRequest.getUri();

        assertThat(uri).containsMatch("^my-namespace/my-entity\\(.*\\)$");
        assertThat(uri).containsMatch("Description='something'");
        assertThat(uri).containsMatch("Name='cheese'");
        assertThat(uri).containsMatch("ID=123");
        assertThat(uri).containsMatch("date=2020-02-22");
    }

    @Test
    @DisplayName("with multiple invalid keys")
    void testGetUriMultipleInvalidKeys() {
        compositeMap.put("ID", 123L);
        compositeMap.put("", "cheese");
        odataRequest.setKey(compositeMap);
        try {
            odataRequest.getUri();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
            assertThat(e).hasMessageThat().isEqualTo("For multi-part keys the full key predicate is required.");
        }
    }

    @Test
    @DisplayName("with wrong type")
    void testGetUriWrongType() {
        compositeMap.put("ID", '1');
        try {
            odataRequest.setKey(compositeMap);
        } catch (Exception e) {
            assertThat(e).isInstanceOf(IllegalArgumentException.class);
            assertThat(e).hasMessageThat()
                    .isEqualTo("Expecting either type String or Long as key, but received java.lang.Character");
        }
    }

    @Test
    @DisplayName("with multiple invocations of setKey")
    void testGetUriMultipleInvocationsOfSetKey() {
        compositeMap.put("ID", 123L);
        odataRequest.setKey(compositeMap).setKey(123L).setKey("surprise");
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity('surprise')");
    }

    @Test
    @DisplayName("with property set")
    void testGetUriWithProperty() {
        odataRequest.setProperty("my-property");
        assertThat(odataRequest.getUri()).isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity/my-property");
    }

    @Test
    @DisplayName("with expand single attribute")
    void testExpand() {
        odataRequest.setKey("0123").setExpandQuery("ExpandItem");
        assertThat(odataRequest.getUri())
                .isEqualTo(EXPECTED_SERVICE_ROOT_URL + "/my-entity('0123')?$expand=ExpandItem");
    }

    @Test
    @DisplayName("Verify self() returns correct instance")
    void testGetSelf() {
        assertThat(odataRequest.self()).isEqualTo(odataRequest);
    }
}
