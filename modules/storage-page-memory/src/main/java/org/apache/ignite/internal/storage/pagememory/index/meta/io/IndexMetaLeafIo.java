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

package org.apache.ignite.internal.storage.pagememory.index.meta.io;

import org.apache.ignite.internal.pagememory.io.IoVersions;
import org.apache.ignite.internal.pagememory.tree.BplusTree;
import org.apache.ignite.internal.pagememory.tree.io.BplusIo;
import org.apache.ignite.internal.pagememory.tree.io.BplusLeafIo;
import org.apache.ignite.internal.storage.pagememory.index.IndexPageTypes;
import org.apache.ignite.internal.storage.pagememory.index.meta.IndexMeta;
import org.apache.ignite.internal.storage.pagememory.index.meta.IndexMetaTree;

/**
 * IO routines for {@link IndexMetaTree} leaf pages.
 */
public class IndexMetaLeafIo extends BplusLeafIo<IndexMeta> implements IndexMetaIo {
    /** I/O versions. */
    public static final IoVersions<IndexMetaLeafIo> VERSIONS = new IoVersions<>(new IndexMetaLeafIo(1));

    /**
     * Constructor.
     *
     * @param ver Page format version.
     */
    private IndexMetaLeafIo(int ver) {
        super(IndexPageTypes.T_INDEX_META_LEAF_IO, ver, SIZE_IN_BYTES);
    }

    /** {@inheritDoc} */
    @Override
    public void store(long dstPageAddr, int dstIdx, BplusIo<IndexMeta> srcIo, long srcPageAddr, int srcIdx) {
        IndexMetaIo.super.store(dstPageAddr, dstIdx, srcPageAddr, srcIdx);
    }

    /** {@inheritDoc} */
    @Override
    public void storeByOffset(long pageAddr, int off, IndexMeta row) {
        IndexMetaIo.super.storeByOffset(pageAddr, off, row);
    }

    /** {@inheritDoc} */
    @Override
    public IndexMeta getLookupRow(BplusTree<IndexMeta, ?> tree, long pageAddr, int idx) {
        return getRow(pageAddr, idx);
    }
}
