/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.configuration.storage;

import static java.util.concurrent.CompletableFuture.supplyAsync;

import java.io.Serializable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.ignite.configuration.annotation.ConfigurationType;
import org.apache.ignite.internal.configuration.util.ConfigurationSerializationUtil;
import org.apache.ignite.internal.future.InFlightFutures;
import org.apache.ignite.internal.logger.IgniteLogger;
import org.apache.ignite.internal.logger.Loggers;
import org.apache.ignite.internal.metastorage.MetaStorageManager;
import org.apache.ignite.internal.metastorage.client.Conditions;
import org.apache.ignite.internal.metastorage.client.Entry;
import org.apache.ignite.internal.metastorage.client.EntryEvent;
import org.apache.ignite.internal.metastorage.client.Operation;
import org.apache.ignite.internal.metastorage.client.Operations;
import org.apache.ignite.internal.metastorage.client.SimpleCondition;
import org.apache.ignite.internal.metastorage.client.WatchEvent;
import org.apache.ignite.internal.metastorage.client.WatchListener;
import org.apache.ignite.internal.thread.NamedThreadFactory;
import org.apache.ignite.internal.util.ByteUtils;
import org.apache.ignite.internal.util.Cursor;
import org.apache.ignite.internal.util.IgniteUtils;
import org.apache.ignite.internal.vault.VaultEntry;
import org.apache.ignite.internal.vault.VaultManager;
import org.apache.ignite.lang.ByteArray;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Distributed configuration storage.
 */
public class DistributedConfigurationStorage implements ConfigurationStorage {
    /** Logger. */
    private static final IgniteLogger LOG = Loggers.forClass(DistributedConfigurationStorage.class);

    /** Prefix added to configuration keys to distinguish them in the meta storage. Must end with a dot. */
    private static final String DISTRIBUTED_PREFIX = "dst-cfg.";

    /**
     * Key for CAS-ing configuration keys to meta storage.
     */
    private static final ByteArray MASTER_KEY = new ByteArray(DISTRIBUTED_PREFIX + "$master$key");

    /**
     * Vault's key for a value of previous and current configuration's MetaStorage revision.
     */
    private static final ByteArray CONFIGURATION_REVISIONS_KEY = new ByteArray("$revisions");

    /**
     * Prefix for all keys in the distributed storage. This key is expected to be the first key in lexicographical order of distributed
     * configuration keys.
     */
    private static final ByteArray DST_KEYS_START_RANGE = new ByteArray(DISTRIBUTED_PREFIX);

    /**
     * This key is expected to be the last key in lexicographical order of distributed configuration keys. It is possible because keys are
     * in lexicographical order in meta storage and adding {@code (char)('.' + 1)} to the end will produce all keys with prefix {@link
     * DistributedConfigurationStorage#DISTRIBUTED_PREFIX}
     */
    private static final ByteArray DST_KEYS_END_RANGE = new ByteArray(incrementLastChar(DISTRIBUTED_PREFIX));

    /** Meta storage manager. */
    private final MetaStorageManager metaStorageMgr;

    /** Vault manager. */
    private final VaultManager vaultMgr;

    /** Configuration changes listener. */
    private volatile ConfigurationStorageListener lsnr;

    /**
     * Currently known change id. Either matches or will soon match the Meta Storage revision of the latest configuration update. It is
     * possible that {@code changeId} is already updated but notifications are not yet handled, thus revision is valid but not applied. This
     * is fine.
     *
     * <p>Given that {@link #MASTER_KEY} is updated on every configuration change, one could assume that {@code changeId} matches the
     * revision of {@link #MASTER_KEY}.
     *
     * <p>This is true for all cases except for node restart. Key-specific revision values are lost on local vault copy after restart, so
     * stored {@link MetaStorageManager#APPLIED_REV} value is used instead. This fact has very important side effect: it's no longer
     * possible to use {@link SimpleCondition.RevisionCondition#eq} on {@link #MASTER_KEY}
     * in {@link DistributedConfigurationStorage#write(Map, long)}. {@link SimpleCondition.RevisionCondition#le(long)} must be used instead.
     *
     * @see #MASTER_KEY
     * @see MetaStorageManager#APPLIED_REV
     * @see #write(Map, long)
     */
    private final AtomicLong changeId = new AtomicLong(0L);

    private final ExecutorService threadPool = Executors.newFixedThreadPool(4, new NamedThreadFactory("dst-cfg", LOG));

    private final InFlightFutures futureTracker = new InFlightFutures();

    /**
     * Constructor.
     *
     * @param metaStorageMgr Meta storage manager.
     * @param vaultMgr       Vault manager.
     */
    public DistributedConfigurationStorage(MetaStorageManager metaStorageMgr, VaultManager vaultMgr) {
        this.metaStorageMgr = metaStorageMgr;

        this.vaultMgr = vaultMgr;
    }

    @Override
    public void close() throws Exception {
        IgniteUtils.shutdownAndAwaitTermination(threadPool, 10, TimeUnit.SECONDS);

        futureTracker.cancelInFlightFutures();
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Map<String, ? extends Serializable>> readAllLatest(String prefix) {
        return registerFuture(supplyAsync(() -> {
            var data = new HashMap<String, Serializable>();

            var rangeStart = new ByteArray(DISTRIBUTED_PREFIX + prefix);

            var rangeEnd = new ByteArray(incrementLastChar(DISTRIBUTED_PREFIX + prefix));

            try (Cursor<Entry> entries = metaStorageMgr.range(rangeStart, rangeEnd)) {
                for (Entry entry : entries) {
                    ByteArray key = entry.key();
                    byte[] value = entry.value();

                    if (entry.tombstone()) {
                        continue;
                    }

                    // Meta Storage should not return nulls as values
                    assert value != null;

                    if (key.equals(MASTER_KEY)) {
                        continue;
                    }

                    String dataKey = key.toString().substring(DISTRIBUTED_PREFIX.length());

                    data.put(dataKey, ConfigurationSerializationUtil.fromBytes(value));
                }
            } catch (Exception e) {
                throw new StorageException("Exception when closing a Meta Storage cursor", e);
            }

            return data;
        }, threadPool));
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Serializable> readLatest(String key) {
        return metaStorageMgr.get(new ByteArray(DISTRIBUTED_PREFIX + key))
                .thenApply(entry -> {
                    byte[] value = entry.value();

                    return value == null ? null : ConfigurationSerializationUtil.fromBytes(value);
                })
                .exceptionally(e -> {
                    throw new StorageException("Exception while reading data from Meta Storage", e);
                });
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Data> readDataOnRecovery() throws StorageException {
        CompletableFuture<Data> future = vaultMgr.get(MetaStorageManager.APPLIED_REV)
                .thenCombine(vaultMgr.get(CONFIGURATION_REVISIONS_KEY), this::resolveRevision)
                .thenApplyAsync(this::readDataOnRecovery0, threadPool);

        return registerFuture(future);
    }

    /**
     * Resolves current configuration revision based on the saved in the Vault revision of the metastorage and also
     * previous and current revisions of the configuration saved in the Vault.
     *
     * @param appliedRevEntry Applied revision entry.
     * @param revisionsEntry Configuration revisions entry.
     * @return Configuration revision.
     */
    private long resolveRevision(@Nullable VaultEntry appliedRevEntry, @Nullable VaultEntry revisionsEntry) {
        long appliedRevision = appliedRevEntry == null ? 0L : ByteUtils.bytesToLong(appliedRevEntry.value());

        long cfgRevision = appliedRevision;

        if (revisionsEntry != null) {
            byte[] value = revisionsEntry.value();
            long prevMasterKeyRevision = ByteUtils.bytesToLong(value, 0);
            long curMasterKeyRevision = ByteUtils.bytesToLong(value, Long.BYTES);

            // If current master key revision is higher than applied revision, then node failed
            // before applied revision changed, so we have to use previous master key revision
            cfgRevision = curMasterKeyRevision <= appliedRevision ? curMasterKeyRevision : prevMasterKeyRevision;
        }

        return cfgRevision;
    }

    private Data readDataOnRecovery0(long cfgRevision) {
        var data = new HashMap<String, Serializable>();

        try (Cursor<VaultEntry> entries = storedDistributedConfigKeys()) {
            for (VaultEntry entry : entries) {
                ByteArray key = entry.key();
                byte[] value = entry.value();

                // vault iterator should not return nulls as values
                assert value != null;

                if (key.equals(MASTER_KEY)) {
                    continue;
                }

                String dataKey = key.toString().substring(DISTRIBUTED_PREFIX.length());

                data.put(dataKey, ConfigurationSerializationUtil.fromBytes(value));
            }
        } catch (Exception e) {
            throw new StorageException("Exception when closing a Vault cursor", e);
        }

        assert data.isEmpty() || cfgRevision > 0;

        changeId.set(data.isEmpty() ? 0 : cfgRevision);

        return new Data(data, cfgRevision);
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Boolean> write(Map<String, ? extends Serializable> newValues, long curChangeId) {
        assert curChangeId <= changeId.get();
        assert lsnr != null : "Configuration listener must be initialized before write.";

        if (curChangeId < changeId.get()) {
            // This means that curChangeId is less than version and other node has already updated configuration and
            // write should be retried. Actual version will be set when watch and corresponding configuration listener
            // updates configuration.
            return CompletableFuture.completedFuture(false);
        }

        Set<Operation> operations = new HashSet<>();

        for (Map.Entry<String, ? extends Serializable> entry : newValues.entrySet()) {
            ByteArray key = new ByteArray(DISTRIBUTED_PREFIX + entry.getKey());

            if (entry.getValue() != null) {
                operations.add(Operations.put(key, ConfigurationSerializationUtil.toBytes(entry.getValue())));
            } else {
                operations.add(Operations.remove(key));
            }
        }

        operations.add(Operations.put(MASTER_KEY, ByteUtils.longToBytes(curChangeId)));

        SimpleCondition condition = curChangeId == 0L
                ? Conditions.notExists(MASTER_KEY)
                : Conditions.revision(MASTER_KEY).eq(curChangeId);

        return metaStorageMgr.invoke(condition, operations, Set.of(Operations.noop()));
    }

    /** {@inheritDoc} */
    @Override
    public synchronized void registerConfigurationListener(@NotNull ConfigurationStorageListener lsnr) {
        if (this.lsnr == null) {
            this.lsnr = lsnr;

            // TODO: registerWatchByPrefix could throw OperationTimeoutException and CompactedException and we should
            // TODO: properly handle such cases https://issues.apache.org/jira/browse/IGNITE-14604
            metaStorageMgr.registerWatchByPrefix(DST_KEYS_START_RANGE, new WatchListener() {
                @Override
                public boolean onUpdate(@NotNull WatchEvent events) {
                    Map<String, Serializable> data = new HashMap<>();

                    Entry masterKeyEntry = null;

                    for (EntryEvent event : events.entryEvents()) {
                        Entry e = event.newEntry();

                        if (e.key().equals(MASTER_KEY)) {
                            masterKeyEntry = e;
                        } else {
                            String key = e.key().toString().substring(DISTRIBUTED_PREFIX.length());

                            Serializable value = e.value() == null ? null : ConfigurationSerializationUtil.fromBytes(e.value());

                            data.put(key, value);
                        }
                    }

                    // Contract of meta storage ensures that all updates of one revision will come in one batch.
                    // Also masterKey should be updated every time when we update cfg.
                    // That means that masterKey update must be included in the batch.
                    assert masterKeyEntry != null;

                    long newChangeId = masterKeyEntry.revision();

                    assert newChangeId > changeId.get();

                    changeId.set(newChangeId);

                    lsnr.onEntriesChanged(new Data(data, newChangeId)).join();

                    return true;
                }

                @Override
                public void onError(@NotNull Throwable e) {
                    // TODO: need to handle this case and there should some mechanism for registering new watch as far as
                    // TODO: onError unregisters failed watch https://issues.apache.org/jira/browse/IGNITE-14604
                    LOG.warn("Meta storage listener issue", e);
                }
            });
        } else {
            LOG.info("Configuration listener has already been set");
        }
    }

    /** {@inheritDoc} */
    @Override
    public ConfigurationType type() {
        return ConfigurationType.DISTRIBUTED;
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Long> lastRevision() {
        return metaStorageMgr.get(MASTER_KEY).thenApply(Entry::revision);
    }

    /** {@inheritDoc} */
    @Override
    public CompletableFuture<Void> writeConfigurationRevision(long prevRevision, long currentRevision) {
        byte[] value = new byte[Long.BYTES * 2];

        ByteUtils.longToBytes(prevRevision, value, 0);
        ByteUtils.longToBytes(currentRevision, value, Long.BYTES);

        return vaultMgr.put(CONFIGURATION_REVISIONS_KEY, value);
    }

    /**
     * Method that returns all distributed configuration keys from the meta storage that were stored in the vault filtered out by the
     * current applied revision as an upper bound. Applied revision is a revision of the last successful vault update.
     *
     * <p>This is possible to distinguish cfg keys from meta storage because we add a special prefix {@link
     * DistributedConfigurationStorage#DISTRIBUTED_PREFIX} to all configuration keys that we put to the meta storage.
     *
     * @return Iterator built upon all distributed configuration entries stored in vault.
     */
    private Cursor<VaultEntry> storedDistributedConfigKeys() {
        return vaultMgr.range(DST_KEYS_START_RANGE, DST_KEYS_END_RANGE);
    }

    /**
     * Increments the last character of the given string.
     */
    private static String incrementLastChar(String str) {
        char lastChar = str.charAt(str.length() - 1);

        return str.substring(0, str.length() - 1) + (char) (lastChar + 1);
    }

    private <T> CompletableFuture<T> registerFuture(CompletableFuture<T> future) {
        futureTracker.registerFuture(future);

        return future;
    }
}
