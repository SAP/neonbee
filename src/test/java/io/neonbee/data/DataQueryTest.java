package io.neonbee.data;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.vertx.core.buffer.Buffer;

class DataQueryTest {

    private DataQuery query;

    @BeforeEach
    void setUp() {
        query = new DataQuery();
    }

    @Test
    @DisplayName("setQuery should set a query and reset parameters to null")
    void testSetQuery() {
        query.parameters = Collections.emptyMap();
        query.setQuery("name=Hodor");
        assertThat(query.getQuery()).isEqualTo("name=Hodor");
        assertThat(query.parameters).isNotNull();
    }

    @Test
    @DisplayName("getQuery should return the parameters joined to a String if parameters is non null")
    void testGetQuery2() {
        query.parameters = Map.of("Hodor", List.of("Hodor"));
        assertThat(query.getQuery()).isEqualTo("Hodor=Hodor");

        query.parameters = Map.of("Hodor", List.of("Hodor", "Hodor2"));
        assertThat(query.getQuery()).isEqualTo("Hodor=Hodor&Hodor=Hodor2");
    }

    @Test
    @DisplayName("getParameters should return a Map with the query parameters")
    void testGetParameters() {
        query.setQuery("Hodor=Hodor&Jon=Snow&Hodor=Hodor2");
        Map<String, List<String>> expected = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow"));
        assertThat(query.getParameters()).containsExactlyEntriesIn(expected);
    }

    @Test
    @DisplayName("getParameterValues should return a List with the values for a given parameter")
    void testGetParameterValues() {
        query.setQuery("Hodor=Hodor&Jon=Snow&Hodor=Hodor2");
        List<String> expected = List.of("Hodor", "Hodor2");
        assertThat(query.getParameterValues("Hodor")).containsExactlyElementsIn(expected);
    }

    @Test
    @DisplayName("getParameter should return the value for a given parameter")
    void testGetParameter() {
        query.setQuery("Hodor=Hodor&Jon=Snow&Hodor=Hodor2&Some=Data&Empty=&AlsoEmpty&Test=123");
        assertThat(query.getParameter("Hodor")).isEqualTo("Hodor");
        assertThat(query.getParameter("Jon")).isEqualTo("Snow");
        assertThat(query.getParameter("Some")).isEqualTo("Data");
        assertThat(query.getParameter("Empty")).isEqualTo("");
        assertThat(query.getParameter("AlsoEmpty")).isEqualTo("");
        assertThat(query.getParameter("Test")).isEqualTo("123");
    }

    @Test
    @DisplayName("getParameter should return the first value for a given parameter")
    void testGetFirstParameter() {
        query.setQuery("Hodor=Hodor&Jon=Snow&Hodor=Hodor2");
        String expected = "Hodor";
        assertThat(query.getParameter("Hodor")).isEqualTo(expected);
    }

    @Test
    @DisplayName("addParameter should add a given parameter with value(s)")
    void testAddParameter() {
        Map<String, List<String>> expected = Map.of("Jon", List.of("Snow"));
        assertThat(query.addParameter("Jon", "Snow").parameters).isEqualTo(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow"));
        assertThat(query.addParameter("Hodor", "Hodor", "Hodor2").parameters).isEqualTo(expected);
    }

    @Test
    @DisplayName("setParameter should set a given parameter with value(s)")
    void testSetParameter() {
        Map<String, List<String>> expected = Map.of("Jon", List.of("Snow"));
        assertThat(query.setParameter("Jon", "Snow").parameters).isEqualTo(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow"));
        assertThat(query.setParameter("Hodor", "Hodor", "Hodor2").parameters).isEqualTo(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow", "Know", "Nothing"));
        assertThat(query.setParameter("Jon", "Snow", "Know", "Nothing").parameters).isEqualTo(expected);
    }

    @Test
    @DisplayName("setParameter should set a given parameter with value(s)")
    void testRemoveParameter() {
        query.parameters = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow", "Know", "Nothing"));
        query.setQuery(query.getQuery());

        Map<String, List<String>> expected = Map.of("Hodor", List.of("Hodor", "Hodor2"));
        assertThat(query.removeParameter("Jon").parameters).isEqualTo(expected);
    }

    @Test
    @DisplayName("parseQueryString should parse a query string correct")
    void testParseQueryString() {
        Map<String, List<String>> expected = Map.of("Hodor", List.of(""));
        assertThat(DataQuery.parseQueryString("Hodor")).containsExactlyEntriesIn(expected);
        assertThat(DataQuery.parseQueryString("Hodor=")).containsExactlyEntriesIn(expected);

        expected = Map.of("Hodor", List.of("Hodor"));
        assertThat(DataQuery.parseQueryString("Hodor=Hodor")).containsExactlyEntriesIn(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"));
        assertThat(DataQuery.parseQueryString("Hodor=Hodor&Hodor=Hodor2")).containsExactlyEntriesIn(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow"));
        assertThat(DataQuery.parseQueryString("Hodor=Hodor&Jon=Snow&Hodor=Hodor2")).containsExactlyEntriesIn(expected);
    }

    @Test
    @DisplayName("setUriPath should not work if it contains a query")
    void testSetUriPathWithQuery() {
        Exception thrownException = assertThrows(IllegalArgumentException.class, () -> {
            new DataQuery().setUriPath("/raw/MyDataVerticle?param=value");
        });
        assertThat(thrownException.getMessage()).isEqualTo("uriPath must not contain a query");
    }

    @Test
    @DisplayName("DataQuery creation should not work if it uriPath contains a query")
    void testDataQueryCreation() {
        Exception thrownException = assertThrows(IllegalArgumentException.class, () -> {
            new DataQuery("/odata/MyNamespace.MyService/MyEntitySet?$param=value");
        });
        assertThat(thrownException.getMessage()).isEqualTo("uriPath must not contain a query");
    }

    @Test
    @DisplayName("DataQuery should have empty map when no header is passed")
    void testEmptyHeader() {
        DataQuery query = new DataQuery("uri", "name=Hodor");
        assertThat(query.getHeaders()).isEqualTo(Map.of());
    }

    @Test
    @DisplayName("Equals should return false with different bodies")
    void testEqualsWithDifferentBodies() {
        DataQuery query1 = new DataQuery(DataAction.CREATE, "uri", "name=Hodor", Map.of("header1", List.of("value1")),
                Buffer.buffer("payload1"));
        assertThat(query1.copy().setBody(Buffer.buffer("payload2"))).isNotEqualTo(query1);
    }
}
