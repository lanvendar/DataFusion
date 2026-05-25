/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.datafusion.common.options;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.IntStream;

import static com.datafusion.common.utils.Preconditions.checkArgument;
import static com.datafusion.common.utils.Preconditions.checkNotNull;

/**
 * MemorySize is a representation of a number of bytes, viewable in different units.
 *
 * <h2>Parsing</h2>
 *
 * <p>The size can be parsed from a text expression. If the expression is a pure number, the value
 * will be interpreted as bytes.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class MemorySize implements java.io.Serializable, Comparable<MemorySize> {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * The zero size.
     */
    public static final MemorySize ZERO = new MemorySize(0L);
    
    /**
     * The maximum value of a MemorySize.
     */
    public static final MemorySize MAX_VALUE = new MemorySize(Long.MAX_VALUE);
    
    /**
     * The ordered list of memory units.
     */
    private static final List<MemoryUnit> ORDERED_UNITS =
            Arrays.asList(MemoryUnit.BYTES, MemoryUnit.KILO_BYTES, MemoryUnit.MEGA_BYTES, MemoryUnit.GIGA_BYTES, MemoryUnit.TERA_BYTES);
    
    // ------------------------------------------------------------------------
    
    /**
     * The memory size, in bytes.
     */
    private final long bytes;
    
    /**
     * The memorized value returned by toString().
     */
    private transient String stringified;
    
    /**
     * The memorized value returned by toHumanReadableString().
     */
    private transient String humanReadableStr;
    
    /**
     * Constructs a new MemorySize.
     *
     * @param bytes The size, in bytes. Must be zero or larger.
     */
    public MemorySize(long bytes) {
        checkArgument(bytes >= 0, "bytes must be >= 0");
        this.bytes = bytes;
    }
    
    /**
     * Mb to bytes.
     *
     * @param mebiBytes Mb.
     * @return bytes
     */
    public static MemorySize ofMebiBytes(long mebiBytes) {
        return new MemorySize(mebiBytes << 20);
    }
    
    /**
     * Kb to bytes.
     *
     * @param kibiBytes Kb.
     * @return bytes
     */
    public static MemorySize ofKibiBytes(long kibiBytes) {
        return new MemorySize(kibiBytes << 10);
    }
    
    /**
     * Bytes to bytes.
     *
     * @param bytes Bytes.
     * @return bytes
     */
    public static MemorySize ofBytes(long bytes) {
        return new MemorySize(bytes);
    }
    
    // ------------------------------------------------------------------------
    
    /**
     * Gets the memory size in bytes.
     *
     * @return long
     */
    public long getBytes() {
        return bytes;
    }
    
    /**
     * Gets the memory size in Kibibytes (= 1024 bytes).
     *
     * @return long
     */
    public long getKibiBytes() {
        return bytes >> 10;
    }
    
    /**
     * Gets the memory size in Mebibytes (= 1024 Kibibytes).
     *
     * @return long
     */
    public int getMebiBytes() {
        return (int) (bytes >> 20);
    }
    
    /**
     * Gets the memory size in Gibibytes (= 1024 Mebibytes).
     *
     * @return long
     */
    public long getGibiBytes() {
        return bytes >> 30;
    }
    
    /**
     * Gets the memory size in Tebibytes (= 1024 Gibibytes).
     *
     * @return long
     */
    public long getTebiBytes() {
        return bytes >> 40;
    }
    
    // ------------------------------------------------------------------------
    
    @Override
    public int hashCode() {
        return (int) (bytes ^ (bytes >>> 32));
    }
    
    @Override
    public boolean equals(Object obj) {
        return obj == this
                || (obj != null
                && obj.getClass() == this.getClass()
                && ((MemorySize) obj).bytes == this.bytes);
    }
    
    @Override
    public String toString() {
        if (stringified == null) {
            stringified = formatToString();
        }
        
        return stringified;
    }
    
    /**
     * Formats the memory size as a human-readable string.
     *
     * @return String
     */
    private String formatToString() {
        MemoryUnit highestIntegerUnit =
                IntStream.range(0, ORDERED_UNITS.size())
                        .sequential()
                        .filter(idx -> bytes % ORDERED_UNITS.get(idx).getMultiplier() != 0)
                        .boxed()
                        .findFirst()
                        .map(
                                idx -> {
                                    if (idx == 0) {
                                        return ORDERED_UNITS.get(0);
                                    } else {
                                        return ORDERED_UNITS.get(idx - 1);
                                    }
                                })
                        .orElse(MemoryUnit.BYTES);
        
        return String.format(
                "%d %s",
                bytes / highestIntegerUnit.getMultiplier(), highestIntegerUnit.getUnits()[1]);
    }
    
    /**
     * Formats the memory size as a human-readable string.
     *
     * @return String
     */
    public String toHumanReadableString() {
        if (humanReadableStr == null) {
            humanReadableStr = formatToHumanReadableString();
        }
        
        return humanReadableStr;
    }
    
    /**
     * Formats the memory size as a human-readable string.
     *
     * @return String
     */
    private String formatToHumanReadableString() {
        MemoryUnit highestUnit =
                IntStream.range(0, ORDERED_UNITS.size())
                        .sequential()
                        .filter(idx -> bytes > ORDERED_UNITS.get(idx).getMultiplier())
                        .boxed()
                        .max(Comparator.naturalOrder())
                        .map(ORDERED_UNITS::get)
                        .orElse(MemoryUnit.BYTES);
        
        if (highestUnit == MemoryUnit.BYTES) {
            return String.format("%d %s", bytes, MemoryUnit.BYTES.getUnits()[1]);
        } else {
            double approximate = 1.0 * bytes / highestUnit.getMultiplier();
            return String.format(
                    Locale.ROOT,
                    "%.3f%s (%d bytes)",
                    approximate,
                    highestUnit.getUnits()[1],
                    bytes);
        }
    }
    
    @Override
    public int compareTo(MemorySize that) {
        return Long.compare(this.bytes, that.bytes);
    }
    
    // ------------------------------------------------------------------------
    //  Calculations
    // ------------------------------------------------------------------------
    
    /**
     * Adds two memory sizes.
     *
     * @param that The other memory size.
     * @return The sum of the two memory sizes.
     */
    public MemorySize add(MemorySize that) {
        return new MemorySize(Math.addExact(this.bytes, that.bytes));
    }
    
    /**
     * Subtracts two memory sizes.
     *
     * @param that The other memory size.
     * @return The difference of the two memory sizes.
     */
    public MemorySize subtract(MemorySize that) {
        return new MemorySize(Math.subtractExact(this.bytes, that.bytes));
    }
    
    /**
     * Multiplies a memory size with a double value.
     *
     * @param multiplier The multiplier.
     * @return The product of the memory size and the multiplier.
     */
    public MemorySize multiply(double multiplier) {
        checkArgument(multiplier >= 0, "multiplier must be >= 0");
        
        BigDecimal product =
                BigDecimal.valueOf(this.bytes).multiply(BigDecimal.valueOf(multiplier));
        if (product.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) {
            throw new ArithmeticException("long overflow");
        }
        return new MemorySize(product.longValue());
    }
    
    /**
     * Divides a memory size by a long value.
     *
     * @param by The divisor.
     * @return The quotient of the memory size and the divisor.
     */
    public MemorySize divide(long by) {
        checkArgument(by >= 0, "divisor must be >= 0");
        return new MemorySize(bytes / by);
    }
    
    // ------------------------------------------------------------------------
    //  Parsing
    // ------------------------------------------------------------------------
    
    /**
     * Parses the given string as as MemorySize.
     *
     * @param text The string to parse
     * @return The parsed MemorySize
     * @throws IllegalArgumentException Thrown, if the expression cannot be parsed.
     */
    public static MemorySize parse(String text) throws IllegalArgumentException {
        return new MemorySize(parseBytes(text));
    }
    
    /**
     * Parses the given string with a default unit.
     *
     * @param text        The string to parse.
     * @param defaultUnit specify the default unit.
     * @return The parsed MemorySize.
     * @throws IllegalArgumentException Thrown, if the expression cannot be parsed.
     */
    public static MemorySize parse(String text, MemoryUnit defaultUnit)
            throws IllegalArgumentException {
        if (!MemoryUnit.hasUnit(text)) {
            return parse(text + defaultUnit.getUnits()[0]);
        }
        
        return parse(text);
    }
    
    /**
     * Parses the given string as bytes. The supported expressions are listed under {@link
     * MemorySize}.
     *
     * @param text The string to parse
     * @return The parsed size, in bytes.
     * @throws IllegalArgumentException Thrown, if the expression cannot be parsed.
     */
    public static long parseBytes(String text) throws IllegalArgumentException {
        checkNotNull(text, "text");
        
        final String trimmed = text.trim();
        checkArgument(!trimmed.isEmpty(), "argument is an empty- or whitespace-only string");
        
        final int len = trimmed.length();
        int pos = 0;
        
        char current;
        while (pos < len && (current = trimmed.charAt(pos)) >= '0' && current <= '9') {
            pos++;
        }
        
        final String number = trimmed.substring(0, pos);
        final String unit = trimmed.substring(pos).trim().toLowerCase(Locale.US);
        
        if (number.isEmpty()) {
            throw new NumberFormatException("text does not start with a number");
        }
        
        final long value;
        try {
            value = Long.parseLong(number); // this throws a NumberFormatException on overflow
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(
                    "The value '"
                            + number
                            + "' cannot be re represented as 64bit number (numeric overflow).");
        }
        
        final long multiplier = parseUnit(unit).map(MemoryUnit::getMultiplier).orElse(1L);
        final long result = value * multiplier;
        
        // check for overflow
        if (result / multiplier != value) {
            throw new IllegalArgumentException(
                    "The value '"
                            + text
                            + "' cannot be re represented as 64bit number of bytes (numeric overflow).");
        }
        
        return result;
    }
    
    /**
     * Parses the given string as a memory unit.
     *
     * @param unit unit
     * @return Optional
     */
    private static Optional<MemoryUnit> parseUnit(String unit) {
        if (matchesAny(unit, MemoryUnit.BYTES)) {
            return Optional.of(MemoryUnit.BYTES);
        } else if (matchesAny(unit, MemoryUnit.KILO_BYTES)) {
            return Optional.of(MemoryUnit.KILO_BYTES);
        } else if (matchesAny(unit, MemoryUnit.MEGA_BYTES)) {
            return Optional.of(MemoryUnit.MEGA_BYTES);
        } else if (matchesAny(unit, MemoryUnit.GIGA_BYTES)) {
            return Optional.of(MemoryUnit.GIGA_BYTES);
        } else if (matchesAny(unit, MemoryUnit.TERA_BYTES)) {
            return Optional.of(MemoryUnit.TERA_BYTES);
        } else if (!unit.isEmpty()) {
            throw new IllegalArgumentException(
                    "Memory size unit '"
                            + unit
                            + "' does not match any of the recognized units: "
                            + MemoryUnit.getAllUnits());
        }
        
        return Optional.empty();
    }
    
    /**
     * Check whether the given string matches any of the given memory units.
     *
     * @param str  The string to check.
     * @param unit The memory unit.
     * @return True if the string matches any of the given memory units.
     */
    private static boolean matchesAny(String str, MemoryUnit unit) {
        for (String s : unit.getUnits()) {
            if (s.equals(str)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Enum which defines memory unit, mostly used to parse value from configuration file.
     *
     * <p>To make larger values more compact, the common size suffixes are supported:
     *
     * <ul>
     *   <li>1b or 1bytes (bytes)
     *   <li>1k or 1kb or 1kibibytes (interpreted as kibibytes = 1024 bytes)
     *   <li>1m or 1mb or 1mebibytes (interpreted as mebibytes = 1024 kibibytes)
     *   <li>1g or 1gb or 1gibibytes (interpreted as gibibytes = 1024 mebibytes)
     *   <li>1t or 1tb or 1tebibytes (interpreted as tebibytes = 1024 gibibytes)
     * </ul>
     */
    public enum MemoryUnit {
        /**
         * B.
         */
        BYTES(new String[] {"b", "bytes"}, 1L),
        /**
         * KB.
         */
        KILO_BYTES(new String[] {"k", "kb", "kibibytes"}, 1024L),
        /**
         * MB.
         */
        MEGA_BYTES(new String[] {"m", "mb", "mebibytes"}, 1024L * 1024L),
        /**
         * GB.
         */
        GIGA_BYTES(new String[] {"g", "gb", "gibibytes"}, 1024L * 1024L * 1024L),
        /**
         * TB.
         */
        TERA_BYTES(new String[] {"t", "tb", "tebibytes"}, 1024L * 1024L * 1024L * 1024L);
        
        /**
         * 单位.
         */
        private final String[] units;
        
        /**
         * 倍数.
         */
        private final long multiplier;
        
        /**
         * Constructor.
         *
         * @param units      units
         * @param multiplier multiplier
         */
        MemoryUnit(String[] units, long multiplier) {
            this.units = units;
            this.multiplier = multiplier;
        }
        
        public String[] getUnits() {
            return units;
        }
        
        public long getMultiplier() {
            return multiplier;
        }
        
        public static String getAllUnits() {
            return concatenateUnits(
                    BYTES.getUnits(),
                    KILO_BYTES.getUnits(),
                    MEGA_BYTES.getUnits(),
                    GIGA_BYTES.getUnits(),
                    TERA_BYTES.getUnits());
        }
        
        /**
         * Check whether the given string matches any of the given memory units.
         *
         * @param text The string to check.
         * @return True if the string matches any of the given memory units.
         */
        public static boolean hasUnit(String text) {
            checkNotNull(text, "text");
            
            final String trimmed = text.trim();
            checkArgument(!trimmed.isEmpty(), "argument is an empty- or whitespace-only string");
            
            final int len = trimmed.length();
            int pos = 0;
            
            char current;
            while (pos < len && (current = trimmed.charAt(pos)) >= '0' && current <= '9') {
                pos++;
            }
            
            final String unit = trimmed.substring(pos).trim().toLowerCase(Locale.US);
            
            return unit.length() > 0;
        }
        
        /**
         * Concatenate all units into a string.
         *
         * @param allUnits all units
         * @return concatenate all units into a string
         */
        private static String concatenateUnits(final String[]... allUnits) {
            final StringBuilder builder = new StringBuilder(128);
            
            for (String[] units : allUnits) {
                builder.append('(');
                
                for (String unit : units) {
                    builder.append(unit);
                    builder.append(" | ");
                }
                
                builder.setLength(builder.length() - 3);
                builder.append(") / ");
            }
            
            builder.setLength(builder.length() - 3);
            return builder.toString();
        }
    }
}
