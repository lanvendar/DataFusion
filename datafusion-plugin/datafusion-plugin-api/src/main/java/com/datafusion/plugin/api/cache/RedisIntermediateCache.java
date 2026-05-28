package com.datafusion.plugin.api.cache;

import com.datafusion.plugin.api.config.ApiExtractJobConfig.RedisConfig;
import com.datafusion.plugin.api.core.ApiExtractException;
import com.datafusion.plugin.api.util.JsonUtils;
import com.datafusion.plugin.api.util.TextUtils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class RedisIntermediateCache implements IntermediateCache {
    /** Redis 配置信息. */
    private final RedisConfig config;

    /**
     * 构造 Redis 中间缓存.
     *
     * @param config Redis 配置
     */
    public RedisIntermediateCache(RedisConfig config) {
        this.config = config;
    }

    @Override
    public void put(String key, Object value, long ttlSeconds) {
        String payload = JsonUtils.write(value);
        if (ttlSeconds > 0) {
            command("SETEX", key, String.valueOf(ttlSeconds), payload);
        } else {
            command("SET", key, payload);
        }
    }

    @Override
    public void put(String key, Object value, long ttlSeconds, String mode) {
        String normalized = TextUtils.upper(mode, "UPSERT");
        if ("PUT".equals(normalized)) {
            Object stored = ttlSeconds > 0
                    ? command("SET", key, JsonUtils.write(value), "NX", "EX", String.valueOf(ttlSeconds))
                    : command("SET", key, JsonUtils.write(value), "NX");
            if (stored == null) {
                return;
            }
            return;
        }
        IntermediateCache.super.put(key, value, ttlSeconds, normalized);
    }

    @Override
    public void appendList(String key, Object value, long ttlSeconds) {
        command("RPUSH", key, JsonUtils.write(value));
        expire(key, ttlSeconds);
    }

    @Override
    public void putHash(String key, Object value, long ttlSeconds) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                command("HSET", key, String.valueOf(entry.getKey()), JsonUtils.write(entry.getValue()));
            }
        } else {
            command("HSET", key, "value", JsonUtils.write(value));
        }
        expire(key, ttlSeconds);
    }

    @Override
    public Object get(String key) {
        Object value = command("GET", key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        try {
            return JsonUtils.MAPPER.readValue(text, Object.class);
        } catch (Exception ignored) {
            return text;
        }
    }

    private void expire(String key, long ttlSeconds) {
        if (ttlSeconds > 0) {
            command("EXPIRE", key, String.valueOf(ttlSeconds));
        }
    }

    private Object command(String... args) {
        try (Socket socket = new Socket(config.optionString("host", "localhost"), config.optionInt("port", 6379));
                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                BufferedInputStream in = new BufferedInputStream(socket.getInputStream())) {
            setupConnection(out, in);
            return send(out, in, args);
        } catch (IOException e) {
            throw new ApiExtractException("Redis command failed", e);
        }
    }

    private void setupConnection(BufferedOutputStream out, BufferedInputStream in) throws IOException {
        String password = config.optionString("password", null);
        String passwordRef = config.optionString("passwordRef", null);
        if (TextUtils.isBlank(password) && !TextUtils.isBlank(passwordRef)) {
            password = System.getenv(passwordRef);
        }
        if (!TextUtils.isBlank(password)) {
            send(out, in, "AUTH", password);
        }
        int database = config.optionInt("database", 0);
        if (database > 0) {
            send(out, in, "SELECT", String.valueOf(database));
        }
    }

    private Object send(BufferedOutputStream out, BufferedInputStream in, String... args) throws IOException {
        out.write(encode(args));
        out.flush();
        return readReply(in);
    }

    private byte[] encode(String... args) {
        StringBuilder builder = new StringBuilder();
        builder.append("*").append(args.length).append("\r\n");
        for (String arg : args) {
            byte[] bytes = (arg == null ? "" : arg).getBytes(StandardCharsets.UTF_8);
            builder.append("$").append(bytes.length).append("\r\n")
                    .append(arg == null ? "" : arg).append("\r\n");
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Object readReply(BufferedInputStream in) throws IOException {
        int marker = in.read();
        if (marker == -1) {
            throw new IOException("Redis closed connection");
        }
        if (marker == '+') {
            return readLine(in);
        }
        if (marker == '-') {
            throw new ApiExtractException("Redis error: " + readLine(in));
        }
        if (marker == ':') {
            return Long.parseLong(readLine(in));
        }
        if (marker == '$') {
            int length = Integer.parseInt(readLine(in));
            if (length < 0) {
                return null;
            }
            byte[] bytes = in.readNBytes(length);
            in.readNBytes(2);
            return new String(bytes, StandardCharsets.UTF_8);
        }
        throw new IOException("Unsupported Redis reply marker: " + (char) marker);
    }

    private String readLine(BufferedInputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            if (previous == '\r' && current == '\n') {
                builder.setLength(builder.length() - 1);
                return builder.toString();
            }
            builder.append((char) current);
            previous = current;
        }
        throw new IOException("Redis reply line ended unexpectedly");
    }
}
