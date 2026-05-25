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

package com.datafusion.common.utils;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.datafusion.common.utils.Preconditions.checkArgument;
import static com.datafusion.common.utils.Preconditions.checkNotNull;

/**
 * Collection of utilities about time intervals.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class TimeUtils {
    /**
     * Mapping from time unit labels to their corresponding {@link ChronoUnit}.
     */
    private static final Map<String, ChronoUnit> LABEL_TO_UNIT_MAP =
            Collections.unmodifiableMap(initMap());
    
    /**
     * Parse the given string to a java {@link Duration}. The string is in format "{length
     * value}{time unit label}", e.g. "123ms", "321 s". If no time unit label is specified, it will
     * be considered as milliseconds.
     *
     * <p>Supported time unit labels are:
     *
     * <ul>
     *   <li>DAYS： "d", "day"
     *   <li>HOURS： "h", "hour"
     *   <li>MINUTES： "m", "min", "minute"
     *   <li>SECONDS： "s", "sec", "second"
     *   <li>MILLISECONDS： "ms", "milli", "millisecond"
     *   <li>MICROSECONDS： "µs", "micro", "microsecond"
     *   <li>NANOSECONDS： "ns", "nano", "nanosecond"
     * </ul>
     *
     * @param text string to parse.
     * @return Duration.
     */
    public static Duration parseDuration(String text) {
        checkNotNull(text);
        
        final String trimmed = text.trim();
        checkArgument(!trimmed.isEmpty(), "argument is an empty- or whitespace-only string");
        
        final int len = trimmed.length();
        int pos = 0;
        
        char current;
        while (pos < len && (current = trimmed.charAt(pos)) >= '0' && current <= '9') {
            pos++;
        }
        
        final String number = trimmed.substring(0, pos);
        final String unitLabel = trimmed.substring(pos).trim().toLowerCase(Locale.US);
        
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
        
        if (unitLabel.isEmpty()) {
            return Duration.of(value, ChronoUnit.MILLIS);
        }
        
        ChronoUnit unit = LABEL_TO_UNIT_MAP.get(unitLabel);
        if (unit != null) {
            return Duration.of(value, unit);
        } else {
            throw new IllegalArgumentException(
                    "Time interval unit label '"
                            + unitLabel
                            + "' does not match any of the recognized units: "
                            + TimeUnit.getAllUnits());
        }
    }
    
    /**
     * Returns a map from time unit labels to their corresponding {@link ChronoUnit}.
     *
     * @return Map.
     */
    private static Map<String, ChronoUnit> initMap() {
        Map<String, ChronoUnit> labelToUnit = new HashMap<>();
        for (TimeUnit timeUnit : TimeUnit.values()) {
            for (String label : timeUnit.getLabels()) {
                labelToUnit.put(label, timeUnit.getUnit());
            }
        }
        return labelToUnit;
    }
    
    /**
     * Converts a duration to a string in millis.
     *
     * @param duration to convert to string
     * @return duration string in millis
     */
    public static String getStringInMillis(final Duration duration) {
        return duration.toMillis() + TimeUnit.MILLISECONDS.labels.get(0);
    }
    
    /**
     * Pretty prints the duration as a lowest granularity unit that does not lose precision.
     *
     * <p>Examples:
     *
     * <pre>{@code
     * Duration.ofMilliseconds(60000) will be printed as 1 min
     * Duration.ofHours(1).plusSeconds(1) will be printed as 3601 s
     * }</pre>
     *
     * <b>NOTE:</b> It supports only durations that fit into long.
     *
     * @param duration to format
     * @return formatted duration
     */
    public static String formatWithHighestUnit(Duration duration) {
        long nanos = duration.toNanos();
        
        List<TimeUnit> orderedUnits =
                Arrays.asList(
                        TimeUnit.NANOSECONDS,
                        TimeUnit.MICROSECONDS,
                        TimeUnit.MILLISECONDS,
                        TimeUnit.SECONDS,
                        TimeUnit.MINUTES,
                        TimeUnit.HOURS,
                        TimeUnit.DAYS);
        
        TimeUnit highestIntegerUnit =
                IntStream.range(0, orderedUnits.size())
                        .sequential()
                        .filter(
                                idx ->
                                        nanos % orderedUnits.get(idx).unit.getDuration().toNanos()
                                                != 0)
                        .boxed()
                        .findFirst()
                        .map(
                                idx -> {
                                    if (idx == 0) {
                                        return orderedUnits.get(0);
                                    } else {
                                        return orderedUnits.get(idx - 1);
                                    }
                                })
                        .orElse(TimeUnit.MILLISECONDS);
        
        return String.format(
                "%d %s",
                nanos / highestIntegerUnit.unit.getDuration().toNanos(),
                highestIntegerUnit.getLabels().get(0));
    }
    
    /**
     * Enum which defines time unit, mostly used to parse value from configuration file.
     */
    private enum TimeUnit {
        /**
         * Days.
         */
        DAYS(ChronoUnit.DAYS, singular("d"), plural("day")),
        /**
         * Hours.
         */
        HOURS(ChronoUnit.HOURS, singular("h"), plural("hour")),
        /**
         * Minutes.
         */
        MINUTES(ChronoUnit.MINUTES, singular("min"), singular("m"), plural("minute")),
        /**
         * Seconds.
         */
        SECONDS(ChronoUnit.SECONDS, singular("s"), plural("sec"), plural("second")),
        /**
         * Milliseconds.
         */
        MILLISECONDS(ChronoUnit.MILLIS, singular("ms"), plural("milli"), plural("millisecond")),
        /**
         * Microseconds.
         */
        MICROSECONDS(ChronoUnit.MICROS, singular("µs"), plural("micro"), plural("microsecond")),
        /**
         * Nanoseconds.
         */
        NANOSECONDS(ChronoUnit.NANOS, singular("ns"), plural("nano"), plural("nanosecond"));
        
        /**
         * Plural suffix.
         */
        private static final String PLURAL_SUFFIX = "s";
        
        /**
         * Labels.
         */
        private final List<String> labels;
        
        /**
         * java.time.temporal.ChronoUnit.
         */
        private final ChronoUnit unit;
        
        /**
         * Constructor.
         *
         * @param unit   java.time.temporal.ChronoUnit
         * @param labels labels
         */
        TimeUnit(ChronoUnit unit, String[]... labels) {
            this.unit = unit;
            this.labels =
                    Arrays.stream(labels)
                            .flatMap(ls -> Arrays.stream(ls))
                            .collect(Collectors.toList());
        }
        
        /**
         * the singular format of the original label.
         *
         * @param label the original label
         * @return the singular format of the original label
         */
        private static String[] singular(String label) {
            return new String[] {label};
        }
        
        /**
         * both the singular format and plural format of the original label.
         *
         * @param label the original label
         * @return both the singular format and plural format of the original label
         */
        private static String[] plural(String label) {
            return new String[] {label, label + PLURAL_SUFFIX};
        }
        
        public List<String> getLabels() {
            return labels;
        }
        
        public ChronoUnit getUnit() {
            return unit;
        }
        
        public static String getAllUnits() {
            return Arrays.stream(TimeUnit.values())
                    .map(TimeUnit::createTimeUnitString)
                    .collect(Collectors.joining(", "));
        }
        
        /**
         * Create a string representation of the time unit.
         *
         * @param timeUnit the time unit
         * @return the string representation of the time unit
         */
        private static String createTimeUnitString(TimeUnit timeUnit) {
            return timeUnit.name() + ": (" + String.join(" | ", timeUnit.getLabels()) + ")";
        }
    }
    
    /**
     * Convert java.util.concurrent.TimeUnit to java.time.temporal.ChronoUnit.
     *
     * @param timeUnit java.util.concurrent.TimeUnit
     * @return java.time.temporal.ChronoUnit
     */
    private static ChronoUnit toChronoUnit(java.util.concurrent.TimeUnit timeUnit) {
        switch (timeUnit) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
            default:
                throw new IllegalArgumentException(
                        String.format("Unsupported time unit %s.", timeUnit));
        }
    }
}
