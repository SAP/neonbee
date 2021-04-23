package io.neonbee.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * A regex based block list, which primarily checks if an entry is blocked, but has a fallback to allow certain entries
 * if they are blocked by the block list.
 */
public class RegexBlockList {
    private List<Pattern> blockList = new ArrayList<>(), allowList = new ArrayList<>();

    /**
     * Create a empty (all allowing) RegexBlockList
     */
    public RegexBlockList() {
        // nothing to initialize here
    }

    /**
     * This method is able to handle a JSON value and will perform initialization of the {@link RegexBlockList}
     * according to the type of object passed to the method:
     * <ul>
     * <li>In case {@code null} is passed, an initial list will be created, allowing all checked elements.
     * <li>In case a plain {@link String} is passed, the string is interpreted as the only regex describing what this
     * list will allow.
     * <li>In case a {@link JsonArray} is passed, it is assumed its items of type {@link String} are describing the
     * allow list of this block list, meaning that only listed items will be allowed by this list.
     * <li>In case a {@link JsonObject} is passed, the "allow" and "block" properties as {@link JsonArray} or
     * {@link String} describe what is to be blocked / allowed by this list.
     * <li>In case of any other type initialization will fail with a {@code IllegalArgumentException}
     * </ul>
     *
     * @param json the JSON value to parse either a {@link JsonArray}, a {@link JsonObject} or a {@link String})
     * @return a fully initialized {@link RegexBlockList}
     * @throws IllegalArgumentException when a JSON type is passed which cannot be parsed
     */
    public static RegexBlockList fromJson(Object json) {
        RegexBlockList blockList = new RegexBlockList();

        if (json == null) {
            return blockList;
        } else if (json instanceof String) {
            blockList.allow((String) json);
        } else if (json instanceof JsonArray) {
            ((JsonArray) json).stream().map(String.class::cast).forEach(blockList::allow);
        } else if (json instanceof JsonObject) {
            Object jsonBlock = ((JsonObject) json).getValue("block");
            if (jsonBlock instanceof String) {
                blockList.block((String) jsonBlock);
            } else if (jsonBlock instanceof JsonArray) {
                ((JsonArray) jsonBlock).stream().map(String.class::cast).forEach(blockList::block);
            } else if (jsonBlock != null) {
                throw new IllegalArgumentException("Cannot parse blocked type of JSON to initialize RegexBlockList");
            }

            Object jsonAllow = ((JsonObject) json).getValue("allow");
            if (jsonAllow instanceof String) {
                blockList.allow((String) jsonAllow);
            } else if (jsonAllow instanceof JsonArray) {
                ((JsonArray) jsonAllow).stream().map(String.class::cast).forEach(blockList::allow);
            } else if (jsonAllow != null) {
                throw new IllegalArgumentException("Cannot parse allowed type of JSON to initialize RegexBlockList");
            }
        } else if (json != null) {
            throw new IllegalArgumentException("Cannot parse type of JSON to initialize RegexBlockList");
        }

        return blockList;
    }

    /**
     * Block a certain regex pattern.
     *
     * @param regex the regex pattern to block
     */
    public void block(String regex) {
        block(Pattern.compile(regex));
    }

    /**
     * Block a certain regex pattern.
     *
     * @param pattern the regex pattern to block
     */
    public void block(Pattern pattern) {
        blockList.add(pattern);
    }

    /**
     * Allow a certain regex pattern.
     *
     * @param regex the regex pattern to allow
     */
    public void allow(String regex) {
        allow(Pattern.compile(regex));
    }

    /**
     * Allow a certain regex pattern.
     *
     * @param pattern the regex pattern to allow
     */
    public void allow(Pattern pattern) {
        allowList.add(pattern);
    }

    /**
     * Clear the block / allow lists.
     */
    public void clear() {
        blockList.clear();
        allowList.clear();
    }

    /**
     * Check if a given input string is allowed, based on the currently populated block / allow list.
     * <p>
     * This method will first check whether the given input string is blocked. If so the allow list can revoke the block
     * in a second check. In case only the allow list is populated, this method assumes everything is blocked and only
     * the elements on the allow list are allowed.
     *
     * @param input the input string to check if it is allowed
     * @return true if the element is allowed
     */
    public boolean isAllowed(String input) {
        boolean allowed = true;

        // special case: if only allow list is filled, assume also only those entries are permitted
        if ((blockList.isEmpty() && !allowList.isEmpty()) || isOnList(blockList, input)) {
            allowed = false;
        }

        // allow list always "overrules" the block list, meaning if an entry is blocked, it can anyways be allowed if
        // specifically listed on the allow list
        if (!allowed && isOnList(allowList, input)) {
            allowed = true;
        }

        return allowed;
    }

    private static boolean isOnList(List<Pattern> list, String input) {
        return list.stream().map(pattern -> pattern.matcher(input)).anyMatch(Matcher::matches);
    }
}
