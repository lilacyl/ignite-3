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

package org.apache.ignite.internal.storage.pagememory.mv;

import static org.apache.ignite.internal.pagememory.util.PageIdUtils.NULL_LINK;
import static org.apache.ignite.internal.pagememory.util.PartitionlessLinks.readPartitionlessLink;

import org.apache.ignite.hlc.HybridTimestamp;
import org.apache.ignite.internal.pagememory.datapage.PageMemoryTraversal;
import org.apache.ignite.internal.pagememory.io.DataPagePayload;
import org.apache.ignite.internal.schema.ByteBufferRow;
import org.jetbrains.annotations.Nullable;

/**
 * Traversal that scans Version Chain until first version visible at the given timestamp is found; then the version
 * is converted to {@link ByteBufferRow} and finally made available via {@link #result()}.
 *
 * <p>NB: this traversal first traverses starting data slots of the Version Chain one after another; when it finds the
 * version it needs, it switches to traversing the slots comprising the version (because it might be fragmented).
 */
class ScanVersionChainByTimestamp implements PageMemoryTraversal<HybridTimestamp> {
    private final int partitionId;

    /** Contains the result when the traversal ends. */
    private @Nullable ByteBufferRow result;

    /**
     * First it's {@code true} (this means that we traverse first slots of versions of the Version Chain using NextLink);
     * then it's {@code false} (when we found the version we need and we read its value).
     */
    private boolean lookingForVersion = true;

    private final ReadRowVersionValue readRowVersionValue = new ReadRowVersionValue();

    ScanVersionChainByTimestamp(int partitionId) {
        this.partitionId = partitionId;
    }

    /** {@inheritDoc} */
    @Override
    public long consumePagePayload(long link, long pageAddr, DataPagePayload payload, HybridTimestamp timestamp) {
        if (lookingForVersion) {
            HybridTimestamp rowVersionTs = HybridTimestamps.readTimestamp(pageAddr, payload.offset() + RowVersion.TIMESTAMP_OFFSET);

            if (rowTimestampMatches(rowVersionTs, timestamp)) {
                return readFullyOrStartReadingFragmented(link, pageAddr, payload);
            } else {
                return advanceToNextVersion(pageAddr, payload);
            }
        } else {
            // We are continuing reading a fragmented row.
            return readNextFragment(link, pageAddr, payload);
        }
    }

    private boolean rowTimestampMatches(HybridTimestamp rowVersionTs, HybridTimestamp timestamp) {
        return rowVersionTs != null && rowVersionTs.compareTo(timestamp) <= 0;
    }

    private long readFullyOrStartReadingFragmented(long link, long pageAddr, DataPagePayload payload) {
        lookingForVersion = false;

        return readRowVersionValue.consumePagePayload(link, pageAddr, payload, null);
    }

    private long advanceToNextVersion(long pageAddr, DataPagePayload payload) {
        long nextLink = readPartitionlessLink(partitionId, pageAddr, payload.offset() + RowVersion.NEXT_LINK_OFFSET);

        if (nextLink == NULL_LINK) {
            return STOP_TRAVERSAL;
        }

        return nextLink;
    }

    private long readNextFragment(long link, long pageAddr, DataPagePayload payload) {
        return readRowVersionValue.consumePagePayload(link, pageAddr, payload, null);
    }

    /** {@inheritDoc} */
    @Override
    public void finish() {
        if (lookingForVersion) {
            // we never found the version -> we hever tried to read its value AND the result is null
            result = null;
            return;
        }

        readRowVersionValue.finish();

        byte[] valueBytes = readRowVersionValue.result();

        if (RowVersion.isTombstone(valueBytes)) {
            result = null;
        } else {
            result = new ByteBufferRow(valueBytes);
        }
    }

    @Nullable ByteBufferRow result() {
        return result;
    }

    void reset() {
        result = null;
        lookingForVersion = true;
        readRowVersionValue.reset();
    }
}
