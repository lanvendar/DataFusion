package com.datafusion.plugin.kafka.json.sink;

import org.apache.flink.core.io.SimpleVersionedSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Paimon committable 序列化器.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonCommittableSerializer implements SimpleVersionedSerializer<PaimonCommittable> {

    /**
     * 序列化版本.
     */
    private static final int VERSION = 1;

    /**
     * 获取序列化版本.
     *
     * @return 序列化版本
     */
    @Override
    public int getVersion() {
        return VERSION;
    }

    /**
     * 序列化 committable.
     *
     * @param committable 待提交消息
     * @return 字节数组
     * @throws IOException 序列化异常
     */
    @Override
    public byte[] serialize(PaimonCommittable committable) throws IOException {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                DataOutputStream data = new DataOutputStream(bytes);
                ObjectOutputStream objects = new ObjectOutputStream(data)) {
            data.writeUTF(committable.database);
            data.writeUTF(committable.tableName);
            data.writeLong(committable.commitIdentifier);
            objects.writeObject(committable.commitMessages);
            objects.flush();
            data.flush();
            return bytes.toByteArray();
        }
    }

    /**
     * 反序列化 committable.
     *
     * @param version 序列化版本
     * @param serialized 序列化字节
     * @return 待提交消息
     * @throws IOException 反序列化异常
     */
    @Override
    @SuppressWarnings("unchecked")
    public PaimonCommittable deserialize(int version, byte[] serialized) throws IOException {
        if (version != VERSION) {
            throw new IOException("Unsupported Paimon committable version: " + version);
        }
        try (ByteArrayInputStream bytes = new ByteArrayInputStream(serialized);
                DataInputStream data = new DataInputStream(bytes);
                ObjectInputStream objects = new ObjectInputStream(data)) {
            PaimonCommittable committable = new PaimonCommittable();
            committable.database = data.readUTF();
            committable.tableName = data.readUTF();
            committable.commitIdentifier = data.readLong();
            committable.commitMessages = (java.util.List<org.apache.paimon.table.sink.CommitMessage>) objects.readObject();
            return committable;
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize Paimon commit messages", e);
        }
    }
}
