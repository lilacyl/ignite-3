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

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.apache.ignite.configuration.annotation.ConfigurationType;

/**
 * Common interface for configuration storage.
 */
public interface ConfigurationStorage extends AutoCloseable {
    /**
     * Reads all configuration values and current storage version during the recovery phase.
     *
     * @return Future that resolves into extracted values and version or a {@link StorageException} if the data could not be read.
     */
    CompletableFuture<Data> readDataOnRecovery();

    /**
     * Retrieves the most recent values which keys start with the given prefix, regardless of the current storage version.
     *
     * @param prefix Key prefix.
     * @return Future that resolves into extracted values or a {@link StorageException} if the data could not be read.
     */
    CompletableFuture<Map<String, ? extends Serializable>> readAllLatest(String prefix);

    /**
     * Retrieves the most recent value associated with the key, regardless of the current storage version.
     *
     * @param key Key.
     * @return Future that resolves into extracted value or a {@link StorageException} if the data could not be read.
     */
    CompletableFuture<Serializable> readLatest(String key);

    /**
     * Write key-value pairs into the storage with last known version.
     *
     * @param newValues Key-value pairs.
     * @param ver       Last known version.
     * @return Future that gives you {@code true} if successfully written, {@code false} if version of the storage is different from the
     *      passed argument and {@link StorageException} if failed to write data.
     */
    CompletableFuture<Boolean> write(Map<String, ? extends Serializable> newValues, long ver);

    /**
     * Add listener to the storage that notifies of data changes.
     *
     * @param lsnr Listener. Cannot be null.
     */
    void registerConfigurationListener(ConfigurationStorageListener lsnr);

    /**
     * Returns type of this configuration storage.
     *
     * @return Type of this configuration storage.
     */
    ConfigurationType type();

    /**
     * Returns a future that will be completed when the latest revision of the storage is received.
     */
    CompletableFuture<Long> lastRevision();

    /**
     * Writes previous and current configuration's MetaStorage revision for recovery.
     * We need previous and current for the fail-safety: in case if node fails before changing master key on configuration update,
     * MetaStorage's applied revision will be lower than {@code currentRevision} and we will be using previous revision.
     *
     * @param prevRevision Previous revision.
     * @param currentRevision Current revision.
     * @return A future that will be completed when revisions are written to the storage.
     */
    CompletableFuture<Void> writeConfigurationRevision(long prevRevision, long currentRevision);
}
