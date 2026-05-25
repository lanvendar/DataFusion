package com.datafusion.manager.utils;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Scheduler storage implementation utility.
 *
 * @author datafusion
 * @version 1.0.0, 2026/4/9
 * @since 1.0.0
 */
public final class ImplUtil {

    private ImplUtil() {
    }

    /**
     * Convert UUID to String.
     *
     * @param id UUID value
     * @return string representation, or null if input is null
     */
    public static String uuidToStr(UUID id) {
        return id != null ? id.toString() : null;
    }

    /**
     * Convert String to UUID.
     *
     * @param id string representation of UUID
     * @return UUID value, or null if input is blank
     */
    public static UUID strToUuid(String id) {
        return StringUtils.isNotBlank(id) ? UUID.fromString(id) : null;
    }

    /**
     * Parse comma-separated string to Set.
     *
     * @param str comma-separated string
     * @return set of strings, or null if input is blank
     */
    public static Set<String> parseCommaSet(String str) {
        if (StringUtils.isBlank(str)) {
            return null;
        }
        return new HashSet<>(Arrays.asList(str.split(",")));
    }

    /**
     * Join Set of strings with comma.
     *
     * @param set set of strings
     * @return comma-separated string, or null if input is null or empty
     */
    public static String joinCommaSet(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return null;
        }
        return String.join(",", set);
    }
}
