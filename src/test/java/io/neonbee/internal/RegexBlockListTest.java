package io.neonbee.internal;

import static com.google.common.truth.Truth.assertThat;

import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RegexBlockListTest {
    @Test
    @DisplayName("Test if an empty list allows everything")
    void testEmpty() {
        RegexBlockList blockList = new RegexBlockList();

        assertThat(blockList.isAllowed("Abc")).isTrue();
        assertThat(blockList.isAllowed("Bcd")).isTrue();
        assertThat(blockList.isAllowed("Cde")).isTrue();

        assertThat(blockList.isAllowed("Efg")).isTrue();
        assertThat(blockList.isAllowed("Fgh")).isTrue();
        assertThat(blockList.isAllowed("Ghi")).isTrue();

        assertThat(blockList.isAllowed("")).isTrue();
        assertThat(blockList.isAllowed(".*")).isTrue();
    }

    @Test
    @DisplayName("Test if the list blocks the right entries")
    void testBlockList() {
        RegexBlockList blockList = new RegexBlockList();

        blockList.block("A.*");
        blockList.block("B.*");
        blockList.block(Pattern.compile("C.*"));

        assertThat(blockList.isAllowed("Abc")).isFalse();
        assertThat(blockList.isAllowed("Bcd")).isFalse();
        assertThat(blockList.isAllowed("Cde")).isFalse();

        assertThat(blockList.isAllowed("Efg")).isTrue();
        assertThat(blockList.isAllowed("Fgh")).isTrue();
        assertThat(blockList.isAllowed("Ghi")).isTrue();

        assertThat(blockList.isAllowed("")).isTrue();
        assertThat(blockList.isAllowed(".*")).isTrue();
    }

    @Test
    @DisplayName("Test if the list allows the right entries")
    void testAllowList() {
        RegexBlockList blockList = new RegexBlockList();

        blockList.allow("A.*");
        blockList.allow("B.*");
        blockList.allow(Pattern.compile("C.*"));

        assertThat(blockList.isAllowed("Abc")).isTrue();
        assertThat(blockList.isAllowed("Bcd")).isTrue();
        assertThat(blockList.isAllowed("Cde")).isTrue();

        assertThat(blockList.isAllowed("Efg")).isFalse();
        assertThat(blockList.isAllowed("Fgh")).isFalse();
        assertThat(blockList.isAllowed("Ghi")).isFalse();

        assertThat(blockList.isAllowed("")).isFalse();
        assertThat(blockList.isAllowed(".*")).isFalse();
    }

    @Test
    @DisplayName("Test if the list block and allows the right entries")
    void testBlockAllowList() {
        RegexBlockList blockList = new RegexBlockList();

        blockList.block("A.*");
        blockList.allow("AA.*");

        blockList.block("B.*");
        blockList.allow("BB.*");

        blockList.block(Pattern.compile("C.*"));
        blockList.allow(Pattern.compile("CC.*"));

        assertThat(blockList.isAllowed("Abc")).isFalse();
        assertThat(blockList.isAllowed("Acb")).isFalse();
        assertThat(blockList.isAllowed("AAx")).isTrue();

        assertThat(blockList.isAllowed("Bcd")).isFalse();
        assertThat(blockList.isAllowed("Bdc")).isFalse();
        assertThat(blockList.isAllowed("BBx")).isTrue();

        assertThat(blockList.isAllowed("Cde")).isFalse();
        assertThat(blockList.isAllowed("Ced")).isFalse();
        assertThat(blockList.isAllowed("CCx")).isTrue();

        assertThat(blockList.isAllowed("Efg")).isTrue();
        assertThat(blockList.isAllowed("Fgh")).isTrue();
        assertThat(blockList.isAllowed("Ghi")).isTrue();

        assertThat(blockList.isAllowed("")).isTrue();
        assertThat(blockList.isAllowed(".*")).isTrue();
    }

    @Test
    @DisplayName("Test if the list can be cleared")
    void testClear() {
        RegexBlockList blockList = new RegexBlockList();

        blockList.block("A.*");
        blockList.allow("AA.*");

        assertThat(blockList.isAllowed("A")).isFalse();
        assertThat(blockList.isAllowed("AA")).isTrue();
        assertThat(blockList.isAllowed("B")).isTrue();

        blockList.clear();

        assertThat(blockList.isAllowed("A")).isTrue();
    }
}
