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

package org.apache.ignite.internal.table.distributed.command;

import java.util.Collection;
import java.util.UUID;
import org.apache.ignite.internal.schema.BinaryRow;
import org.apache.ignite.raft.client.WriteCommand;
import org.jetbrains.annotations.NotNull;

/**
 * The command puts a batch rows.
 */
public class UpsertAllCommand extends MultiKeyCommand implements WriteCommand {
    /**
     * Creates a new instance of UpsertAllCommand with the given rows to be upserted. The {@code rows} should not be {@code null} or empty.
     *
     * @param rows Binary rows.
     * @param txId The transaction id.
     *
     * @see TransactionalCommand
     */
    public UpsertAllCommand(@NotNull Collection<BinaryRow> rows, @NotNull UUID txId) {
        super(rows, txId);
    }
}
