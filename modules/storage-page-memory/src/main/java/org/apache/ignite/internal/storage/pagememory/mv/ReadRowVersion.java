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

import static org.apache.ignite.internal.pagememory.util.PartitionlessLinks.readPartitionlessLink;

import java.nio.ByteBuffer;
import java.util.function.Predicate;
import org.apache.ignite.hlc.HybridTimestamp;
import org.apache.ignite.internal.pagememory.datapage.PageMemoryTraversal;
import org.apache.ignite.internal.pagememory.io.DataPagePayload;
import org.apache.ignite.internal.pagememory.util.PageIdUtils;
import org.apache.ignite.internal.schema.ByteBufferRow;
import org.jetbrains.annotations.Nullable;

/**
 * Traversal for reading a row version by its link. Loads the version value conditionally.
 */
class ReadRowVersion implements PageMemoryTraversal<Predicate<HybridTimestamp>> {
    private final int partitionId;

    private RowVersion result;

    private boolean readingFirstSlot = true;

    private long firstFragmentLink;

    private @Nullable HybridTimestamp timestamp;

    private long nextLink;

    private final ReadRowVersionValue readRowVersionValue = new ReadRowVersionValue();

    ReadRowVersion(int partitionId) {
        this.partitionId = partitionId;
    }

    /** {@inheritDoc} */
    @Override
    public long consumePagePayload(long link, long pageAddr, DataPagePayload payload, Predicate<HybridTimestamp> loadValue) {
        if (readingFirstSlot) {
            readingFirstSlot = false;

            return readFullOrInitiateReadFragmented(link, pageAddr, payload, loadValue);
        } else {
            return readRowVersionValue.consumePagePayload(link, pageAddr, payload, null);
        }
    }

    private long readFullOrInitiateReadFragmented(long link, long pageAddr, DataPagePayload payload, Predicate<HybridTimestamp> loadValue) {
        firstFragmentLink = link;

        timestamp = HybridTimestamps.readTimestamp(pageAddr, payload.offset() + RowVersion.TIMESTAMP_OFFSET);
        nextLink = readPartitionlessLink(partitionId, pageAddr, payload.offset() + RowVersion.NEXT_LINK_OFFSET);

        if (!loadValue.test(timestamp)) {
            result = new RowVersion(partitionIdFromLink(link), firstFragmentLink, timestamp, nextLink, null);

            return STOP_TRAVERSAL;
        }

        return readRowVersionValue.consumePagePayload(link, pageAddr, payload, null);
    }

    private int partitionIdFromLink(long link) {
        return PageIdUtils.partitionId(PageIdUtils.pageId(link));
    }

    /** {@inheritDoc} */
    @Override
    public void finish() {
        if (result != null) {
            // we did not read the value itself, so we don't need to invoke readRowVersionValue.finish()
            return;
        }

        readRowVersionValue.finish();

        byte[] valueBytes = readRowVersionValue.result();

        ByteBuffer value = ByteBuffer.wrap(valueBytes).order(ByteBufferRow.ORDER);

        result = new RowVersion(partitionIdFromLink(firstFragmentLink), firstFragmentLink, timestamp, nextLink, value);
    }

    RowVersion result() {
        return result;
    }

    void reset() {
        result = null;
        readingFirstSlot = true;
        readRowVersionValue.reset();
    }
}
