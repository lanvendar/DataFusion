package com.datafusion.plugin.flink.table.sink;

import com.datafusion.plugin.flink.table.config.FlinkTableJobConfig.PaimonSinkConfig;
import com.datafusion.plugin.flink.table.core.FlinkTableException;
import org.apache.flink.api.connector.sink2.Committer;
import org.apache.paimon.catalog.Catalog;
import org.apache.paimon.catalog.CatalogContext;
import org.apache.paimon.catalog.CatalogFactory;
import org.apache.paimon.catalog.Identifier;
import org.apache.paimon.table.Table;
import org.apache.paimon.table.sink.CommitMessage;
import org.apache.paimon.table.sink.StreamTableCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paimon committer,按表和提交编号聚合提交.
 *
 * @author DataFusion
 * @version 1.0.0
 * @since 1.0.0
 */
public class PaimonCommitter implements Committer<PaimonCommittable> {

    /**
     * 日志对象.
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(PaimonCommitter.class);

    /**
     * sink 配置.
     */
    private final PaimonSinkConfig sink;

    /**
     * commit user.
     */
    private final String commitUser;

    /**
     * Paimon catalog.
     */
    private transient Catalog catalog;

    /**
     * 构造 committer.
     *
     * @param sink sink 配置
     * @param commitUser commit user
     */
    public PaimonCommitter(PaimonSinkConfig sink, String commitUser) {
        this.sink = sink;
        this.commitUser = commitUser;
    }

    /**
     * 提交 committables.
     *
     * @param requests commit 请求
     * @throws IOException 提交异常
     * @throws InterruptedException 中断异常
     */
    @Override
    public void commit(Collection<CommitRequest<PaimonCommittable>> requests) throws IOException, InterruptedException {
        Map<String, Map<Long, List<CommitMessage>>> grouped = groupByTableAndIdentifier(requests);
        for (Map.Entry<String, Map<Long, List<CommitMessage>>> tableEntry : grouped.entrySet()) {
            commitTable(tableEntry.getKey(), tableEntry.getValue());
        }
    }

    /**
     * 关闭 committer.
     */
    @Override
    public void close() throws Exception {
        if (catalog != null) {
            catalog.close();
        }
    }

    private Map<String, Map<Long, List<CommitMessage>>> groupByTableAndIdentifier(Collection<CommitRequest<PaimonCommittable>> requests) {
        Map<String, Map<Long, List<CommitMessage>>> grouped = new LinkedHashMap<>();
        for (CommitRequest<PaimonCommittable> request : requests) {
            PaimonCommittable committable = request.getCommittable();
            if (committable == null || committable.commitMessages == null || committable.commitMessages.isEmpty()) {
                request.signalAlreadyCommitted();
                continue;
            }
            grouped.computeIfAbsent(committable.identifier(), key -> new LinkedHashMap<>())
                    .computeIfAbsent(committable.commitIdentifier, key -> new ArrayList<>())
                    .addAll(committable.commitMessages);
        }
        return grouped;
    }

    private void commitTable(String identifierText, Map<Long, List<CommitMessage>> messagesByIdentifier) throws IOException {
        try {
            Identifier identifier = Identifier.fromString(identifierText);
            Table table = catalog().getTable(identifier);
            try (StreamTableCommit commit = table.newStreamWriteBuilder().withCommitUser(commitUser).newCommit()) {
                List<Map.Entry<Long, List<CommitMessage>>> entries = new ArrayList<>(messagesByIdentifier.entrySet());
                entries.sort(Comparator.comparingLong(Map.Entry::getKey));
                for (Map.Entry<Long, List<CommitMessage>> entry : entries) {
                    commit.commit(entry.getKey(), entry.getValue());
                    LOGGER.info("Paimon committed, identifier={}, commitIdentifier={}, commitMessages={}",
                            identifierText, entry.getKey(), entry.getValue().size());
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to commit Paimon table: " + identifierText, e);
        }
    }

    private Catalog catalog() {
        if (catalog != null) {
            return catalog;
        }
        try {
            catalog = CatalogFactory.createCatalog(CatalogContext.create(
                    PaimonTableSchemaValidator.catalogOptions(sink.globalOptions())));
            return catalog;
        } catch (Exception e) {
            throw new FlinkTableException("Failed to create Paimon catalog for committer", e);
        }
    }
}
