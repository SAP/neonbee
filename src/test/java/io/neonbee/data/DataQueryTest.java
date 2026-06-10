package io.neonbee.data;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
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
    @DisplayName("getParameter should work")
    void testSetRawQuery() {
        query.setRawQuery("q%3D%26%C3%A4=%C3%A4%26=q&$filter=char%20=%20%27%26%27");
        assertThat(query.getParameter("q=&ä")).isEqualTo("ä&=q");
        assertThat(query.getParameter("$filter")).isEqualTo("char = '&'");
    }

    @Test
    @DisplayName("getParameter should return the first value for a given parameter")
    void testGetFirstParameter() {
        query.addParameter("Hodor", "Hodor", "Hodor2");
        query.addParameter("Jon", "Snow");
        String expected = "Hodor";
        assertThat(query.getParameter("Hodor")).isEqualTo(expected);
    }

    @Test
    @DisplayName("getParameter should return null if the parameter value is null")
    void testGetNonExistingParameter() {
        ArrayList<String> list = new ArrayList<>();
        list.add(null);
        query.getParameters().put("Hodor", list);
        assertThat(query.getParameters()).hasSize(1);
        assertThat(query.getParameter("Hodor")).isNull();
        assertThat(query.getParameter("Hodor", "default")).isEqualTo("default");
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
    @DisplayName("removeParameter should remove a given parameter with all its value(s)")
    void testRemoveParameter() {
        query.addParameter("Hodor", "Hodor", "Hodor2");
        query.addParameter("Jon", "Snow", "Know", "Nothing");

        Map<String, List<String>> expected = Map.of("Jon", List.of("Snow", "Know", "Nothing"));
        assertThat(query.removeParameter("Hodor").parameters).isEqualTo(expected);
    }

    @Test
    @DisplayName("test that setRawQuery from getRawQuery create equal DataQuery objects")
    void testGetSetRawQuery() {
        query.parameters = Map.of("q=&ä", List.of("ä&=q", "=&"), "$filter", List.of("char = '&'"));
        DataQuery query2 = new DataQuery().setRawQuery(query.getRawQuery());

        assertThat(query2).isEqualTo(query);
    }

    @Test
    @DisplayName("test that setRawQuery from getRawQuery create equal DataQuery objects")
    void testgetRawQuery() {
        ArrayList<String> nullValueList = new ArrayList<>();
        nullValueList.add(null);
        query.parameters = Map.of("name", nullValueList);

        assertThat(query.getRawQuery()).isEqualTo("name=");
    }

    @Test
    @DisplayName("parseQueryString should parse a query string correct")
    void testParseQueryString() {
        Map<String, List<String>> expected = Map.of("Hodor", List.of(""));
        assertThat(DataQuery.parseEncodedQueryString("Hodor")).containsExactlyEntriesIn(expected);
        assertThat(DataQuery.parseEncodedQueryString("Hodor=")).containsExactlyEntriesIn(expected);

        expected = Map.of("Hodor", List.of("Hodor"));
        assertThat(DataQuery.parseEncodedQueryString("Hodor=Hodor")).containsExactlyEntriesIn(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"));
        assertThat(DataQuery.parseEncodedQueryString("Hodor=Hodor&Hodor=Hodor2")).containsExactlyEntriesIn(expected);

        expected = Map.of("Hodor", List.of("Hodor", "Hodor2"), "Jon", List.of("Snow"));
        assertThat(DataQuery.parseEncodedQueryString("Hodor=Hodor&Jon=Snow&Hodor=Hodor2"))
                .containsExactlyEntriesIn(expected);
    }

    @Test
    @DisplayName("setUriPath should not work if it contains a query")
    void testSetUriPathWithQuery() {
        DataQuery dataQuery = new DataQuery();
        Exception thrownException = assertThrows(IllegalArgumentException.class, () -> {
            dataQuery.setUriPath("/raw/MyDataVerticle?param=value");
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
        assertThat(query.getHeaders()).isEqualTo(Map.of());
    }

    @Test
    @DisplayName("DataQuery should return null if the header value is null")
    void testNullHeader() {
        ArrayList<String> strings = new ArrayList<>();
        strings.add(null);
        query.getHeaders().put("foo", strings);
        assertThat(query.getHeader("foo")).isNull();
    }

    @Test
    @DisplayName("Equals should return false with different bodies")
    void testEqualsWithDifferentBodies() {
        DataQuery query1 = new DataQuery(DataAction.CREATE, "uri", Buffer.buffer("payload1"));
        assertThat(query1.copy().setBody(Buffer.buffer("payload2"))).isNotEqualTo(query1);
    }

    @Test
    @DisplayName("DataQuery should have case-insensitive headers")
    void testCaseInsensitivityOfHeaders() {
        query.addHeader("header1", "value1");
        assertThat(query.getHeaderValues("Header1")).containsExactly("value1");
    }

    @Test
    @DisplayName("Get DataQuery header should return null when no match in case-insensitive headers")
    void testNonMatchOfHeaders() {
        query.addHeader("header1", "value1");
        assertThat(query.getHeader("Header2")).isNull();
    }

    @Test
    @DisplayName("setHeaders should override already set headers (case-insensitive)")
    void testSetHeaders() {
        query.addHeader("header1", "value1");
        query.setHeaders(Map.of("Header1", List.of("value2")));
        assertThat(query.getHeaderValues("header1")).containsExactly("value2");
    }

    @Test
    @DisplayName("Add DataQuery headers should add new value to existing headers")
    void testAddToExistingHeaders() {
        query.setHeaders(Map.of("header1", List.of("value1")));
        query.addHeader("Header1", "value2");
        assertThat(query.getHeaderValues("header1")).containsExactly("value1", "value2");
    }

    @Test
    @DisplayName("Add DataQuery header should create new header when it does not already exist")
    void testAddNewHeaders() {
        query.addHeader("header2", "value2");
        assertThat(query.getHeaderValues("header2")).containsExactly("value2");
    }

    @Test
    @DisplayName("Modifying headers in copied DataQuery should not modify headers in the original one")
    void testHeadersChangeInCopiedQuery() {
        DataQuery query1 = new DataQuery().addHeader("header1", "value1");
        assertThat(query1.getHeaderValues("header1")).hasSize(1);
        DataQuery query2 = query1.copy();
        query2.getHeaderValues("header1").add("value2");
        assertThat(query1.getHeaderValues("header1")).hasSize(1);
    }
}
