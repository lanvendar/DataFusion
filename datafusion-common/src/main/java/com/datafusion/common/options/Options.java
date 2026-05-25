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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiFunction;

import static com.datafusion.common.options.OptionsUtils.*;

/**
 * Options which stores key/value pairs.
 * 线程安全.
 *
 * @author lanvendar
 * @version 3.0, 2025/4/9
 * @since 2025/4/9
 */
public class Options implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * Stores the concrete key/value pairs of this configuration object.
     */
    private final HashMap<String, String> data;
    
    /**
     * Creates a new empty configuration.
     */
    public Options() {
        this.data = new HashMap<>();
    }
    
    /**
     * Creates a new configuration that is initialized with the options of the given map.
     *
     * @param map configuration object map.
     */
    public Options(Map<String, String> map) {
        this();
        map.forEach(this::setString);
    }
    
    /**
     * Creates a new configuration that is initialized with the options of the given two maps.
     *
     * @param map1 the first configuration object map.
     * @param map2 the second configuration object map.
     */
    public Options(Map<String, String> map1, Map<String, String> map2) {
        this();
        map1.forEach(this::setString);
        map2.forEach(this::setString);
    }
    
    /**
     * Creates a new configuration that is initialized with the options of the given map.
     *
     * @param map configuration object map.
     * @return a new configuration object
     */
    public static Options fromMap(Map<String, String> map) {
        return new Options(map);
    }
    
    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key   the key of the key/value pair to be added
     * @param value the value of the key/value pair to be added
     */
    public synchronized void setString(String key, String value) {
        data.put(key, value);
    }
    
    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param key   the key of the key/value pair to be added
     * @param value the value of the key/value pair to be added
     */
    public synchronized void set(String key, String value) {
        data.put(key, value);
    }
    
    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param <T>    the type of the configuration option
     * @param option the configuration option
     * @param value  the value of the key/value pair to be added
     * @return the configuration
     */
    public synchronized <T> Options set(ConfigOption<T> option, T value) {
        final boolean canBePrefixMap = canBePrefixMap(option);
        setValueInternal(option.key(), value, canBePrefixMap);
        return this;
    }
    
    /**
     * Adds the given key/value pair to the configuration object.
     *
     * @param <T>    the type of the configuration option
     * @param option the configuration option
     * @return the configuration
     */
    public synchronized <T> T get(ConfigOption<T> option) {
        return getOptional(option).orElseGet(option::defaultValue);
    }
    
    /**
     * Gets the value for the given key.
     *
     * @param key the key whose value is to be returned
     * @return the value for the given key
     */
    public synchronized String get(String key) {
        return data.get(key);
    }
    
    /**
     * ConfigOption to Optional.
     *
     * @param <T>    the type of the configuration option
     * @param option the ConfigOption configuration
     * @return the Optional configuration
     */
    public synchronized <T> Optional<T> getOptional(ConfigOption<T> option) {
        Optional<Object> rawValue = getRawValueFromOption(option);
        Class<?> clazz = option.getClazz();
        
        try {
            return rawValue.map(v -> OptionsUtils.convertValue(v, clazz));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    String.format(
                            "Could not parse value '%s' for key '%s'.",
                            rawValue.map(Object::toString).orElse(""), option.key()),
                    e);
        }
    }
    
    /**
     * Checks whether there is an entry for the given config option.
     *
     * @param configOption The configuration option
     * @return <tt>true</tt> if a valid (current or deprecated) key of the config option is stored,
     * <tt>false</tt> otherwise
     */
    public synchronized boolean contains(ConfigOption<?> configOption) {
        synchronized (this.data) {
            final BiFunction<String, Boolean, Optional<Boolean>> applier =
                    (key, canBePrefixMap) -> {
                        if (canBePrefixMap && containsPrefixMap(this.data, key)
                                || this.data.containsKey(key)) {
                            return Optional.of(true);
                        }
                        return Optional.empty();
                    };
            return applyWithOption(configOption, applier).orElse(false);
        }
    }
    
    /**
     * Returns the set of keys in this configuration.
     *
     * @return the set of keys in this configuration
     */
    public synchronized Set<String> keySet() {
        return data.keySet();
    }
    
    /**
     * Returns the map of keys and values in this configuration.
     *
     * @return the map of keys and values in this configuration
     */
    public synchronized Map<String, String> toMap() {
        return data;
    }
    
    /**
     * Removes all entries with the given prefix.
     *
     * @param prefix the prefix of the keys to be removed
     * @return a new configuration object with the removed entries
     */
    public synchronized Options removePrefix(String prefix) {
        return new Options(convertToPropertiesPrefixKey(data, prefix));
    }
    
    /**
     * Removes the given key from the configuration.
     *
     * @param key the key to be removed
     */
    public synchronized void remove(String key) {
        data.remove(key);
    }
    
    /**
     * Checks whether the given key is contained in the configuration.
     *
     * @param key the key to be checked
     * @return <tt>true</tt> if the key is contained in the configuration, <tt>false</tt> otherwise
     */
    public synchronized boolean containsKey(String key) {
        return data.containsKey(key);
    }
    
    /**
     * Adds all entries in these options to the given {@link Properties}.
     *
     * @param props the properties to which the entries are added
     */
    public synchronized void addAllToProperties(Properties props) {
        props.putAll(this.data);
    }
    
    /**
     * ConfigOption to String.
     *
     * @param option option
     * @return String
     */
    public synchronized String getString(ConfigOption<String> option) {
        return get(option);
    }
    
    /**
     * ConfigOption to String.
     *
     * @param key          configuration key
     * @param defaultValue defaultValue
     * @return String
     */
    public synchronized String getString(String key, String defaultValue) {
        return getRawValue(key).map(OptionsUtils::convertToString).orElse(defaultValue);
    }
    
    /**
     * ConfigOption to Boolean.
     *
     * @param key          configuration key
     * @param defaultValue defaultValue
     * @return Boolean
     */
    public synchronized boolean getBoolean(String key, boolean defaultValue) {
        return getRawValue(key).map(OptionsUtils::convertToBoolean).orElse(defaultValue);
    }
    
    /**
     * ConfigOption to Integer.
     *
     * @param key          configuration key
     * @param defaultValue defaultValue
     * @return Integer
     */
    public synchronized int getInteger(String key, int defaultValue) {
        return getRawValue(key).map(OptionsUtils::convertToInt).orElse(defaultValue);
    }
    
    /**
     * ConfigOption to Double.
     *
     * @param key          configuration key
     * @param defaultValue defaultValue
     * @return Double
     */
    public synchronized double getDouble(String key, double defaultValue) {
        return getRawValue(key).map(OptionsUtils::convertToDouble).orElse(defaultValue);
    }
    
    /**
     * ConfigOption to Integer.
     *
     * @param key   configuration key
     * @param value value
     */
    public synchronized void setInteger(String key, int value) {
        setValueInternal(key, value);
    }
    
    /**
     * ConfigOption to Long.
     *
     * @param key          configuration key
     * @param defaultValue defaultValue
     * @return Long
     */
    public synchronized long getLong(String key, long defaultValue) {
        return getRawValue(key).map(OptionsUtils::convertToLong).orElse(defaultValue);
    }
    
    @Override
    public synchronized boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Options options = (Options) o;
        return Objects.equals(data, options.data);
    }
    
    @Override
    public synchronized int hashCode() {
        return Objects.hash(data);
    }
    
    // -------------------------------------------------------------------------
    //                     Internal methods
    // -------------------------------------------------------------------------
    
    /**
     * Set value.
     *
     * @param key            key
     * @param value          value
     * @param canBePrefixMap canBePrefixMap
     * @param <T>            T
     */
    private <T> void setValueInternal(String key, T value, boolean canBePrefixMap) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }
        if (value == null) {
            throw new NullPointerException("Value must not be null.");
        }
        
        synchronized (this.data) {
            if (canBePrefixMap) {
                removePrefixMap(this.data, key);
            }
            this.data.put(key, OptionsUtils.convertToString(value));
        }
    }
    
    /**
     * setValueInternal.
     *
     * @param key   配置键
     * @param value 配置值
     * @param <T>   T
     */
    private <T> void setValueInternal(String key, T value) {
        setValueInternal(key, value, false);
    }
    
    /**
     * configOption to Optional.
     *
     * @param configOption 配置
     * @return Optional
     */
    private Optional<Object> getRawValueFromOption(ConfigOption<?> configOption) {
        return applyWithOption(configOption, this::getRawValue);
    }
    
    /**
     * getRawValue.
     *
     * @param key 配置键
     * @return Optional
     */
    private Optional<Object> getRawValue(String key) {
        return getRawValue(key, false);
    }
    
    /**
     * getRawValue.
     *
     * @param key            配置键
     * @param canBePrefixMap canBePrefixMap
     * @return Optional
     */
    private Optional<Object> getRawValue(String key, boolean canBePrefixMap) {
        if (key == null) {
            throw new NullPointerException("Key must not be null.");
        }
        
        synchronized (this.data) {
            final Object valueFromExactKey = this.data.get(key);
            if (!canBePrefixMap || valueFromExactKey != null) {
                return Optional.ofNullable(valueFromExactKey);
            }
            final Map<String, String> valueFromPrefixMap = convertToPropertiesPrefixed(data, key);
            if (valueFromPrefixMap.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(valueFromPrefixMap);
        }
    }
    
    /**
     * applyWithOption.
     *
     * @param option  配置
     * @param applier applier
     * @param <T>     T
     * @return Optional
     */
    private <T> Optional<T> applyWithOption(
            ConfigOption<?> option, BiFunction<String, Boolean, Optional<T>> applier) {
        final boolean canBePrefixMap = canBePrefixMap(option);
        final Optional<T> valueFromExactKey = applier.apply(option.key(), canBePrefixMap);
        if (valueFromExactKey.isPresent()) {
            return valueFromExactKey;
        } else if (option.hasFallbackKeys()) {
            // try the fallback keys
            for (FallbackKey fallbackKey : option.fallbackKeys()) {
                final Optional<T> valueFromFallbackKey =
                        applier.apply(fallbackKey.getKey(), canBePrefixMap);
                if (valueFromFallbackKey.isPresent()) {
                    return valueFromFallbackKey;
                }
            }
        }
        return Optional.empty();
    }
}
