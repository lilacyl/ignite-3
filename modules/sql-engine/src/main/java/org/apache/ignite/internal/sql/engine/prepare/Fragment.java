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

package org.apache.ignite.internal.sql.engine.prepare;

import static org.apache.ignite.internal.sql.engine.externalize.RelJsonWriter.toJson;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.ignite.internal.sql.engine.metadata.ColocationMappingException;
import org.apache.ignite.internal.sql.engine.metadata.FragmentMapping;
import org.apache.ignite.internal.sql.engine.metadata.FragmentMappingException;
import org.apache.ignite.internal.sql.engine.metadata.IgniteMdFragmentMapping;
import org.apache.ignite.internal.sql.engine.metadata.MappingService;
import org.apache.ignite.internal.sql.engine.metadata.NodeMappingException;
import org.apache.ignite.internal.sql.engine.rel.IgniteReceiver;
import org.apache.ignite.internal.sql.engine.rel.IgniteRel;
import org.apache.ignite.internal.sql.engine.rel.IgniteSender;
import org.apache.ignite.internal.sql.engine.trait.IgniteDistributions;
import org.apache.ignite.internal.tostring.IgniteToStringExclude;
import org.apache.ignite.internal.tostring.S;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fragment of distributed query.
 */
public class Fragment {
    private final long id;

    private final IgniteRel root;

    /** Serialized root representation. */
    @IgniteToStringExclude
    private final String rootSer;

    private final FragmentMapping mapping;

    private final List<IgniteReceiver> remotes;

    /**
     * Constructor.
     *
     * @param id      Fragment id.
     * @param root    Root node of the fragment.
     * @param remotes Remote sources of the fragment.
     */
    public Fragment(long id, IgniteRel root, List<IgniteReceiver> remotes) {
        this(id, root, remotes, null, null);
    }

    /**
     * Constructor.
     * TODO Documentation https://issues.apache.org/jira/browse/IGNITE-15859
     */
    Fragment(long id, IgniteRel root, List<IgniteReceiver> remotes, @Nullable String rootSer, @Nullable FragmentMapping mapping) {
        this.id = id;
        this.root = root;
        this.remotes = List.copyOf(remotes);
        this.rootSer = rootSer != null ? rootSer : toJson(root);
        this.mapping = mapping;
    }

    /**
     * Get fragment ID.
     */
    public long fragmentId() {
        return id;
    }

    /**
     * Get root node.
     */
    public IgniteRel root() {
        return root;
    }

    /**
     * Lazy serialized root representation.
     *
     * @return Serialized form.
     */
    public String serialized() {
        return rootSer;
    }

    public FragmentMapping mapping() {
        return mapping;
    }

    private FragmentMapping mapping(MappingQueryContext ctx, RelMetadataQuery mq, Supplier<List<String>> nodesSource) {
        try {
            FragmentMapping mapping = IgniteMdFragmentMapping.fragmentMappingForMetadataQuery(root, mq, ctx);

            if (rootFragment()) {
                mapping = FragmentMapping.create(ctx.localNodeId()).colocate(mapping);
            }

            if (single() && mapping.nodeIds().size() > 1) {
                // this is possible when the fragment contains scan of a replicated cache, which brings
                // several nodes (actually all containing nodes) to the colocation group, but this fragment
                // supposed to be executed on a single node, so let's choose one wisely
                mapping = FragmentMapping.create(mapping.nodeIds()
                        .get(ThreadLocalRandom.current().nextInt(mapping.nodeIds().size()))).colocate(mapping);
            }

            return mapping.finalize(nodesSource);
        } catch (NodeMappingException e) {
            throw new FragmentMappingException("Failed to calculate physical distribution", this, e.node(), e);
        } catch (ColocationMappingException e) {
            throw new FragmentMappingException("Failed to calculate physical distribution", this, root, e);
        }
    }

    /**
     * Get fragment remote sources.
     */
    public List<IgniteReceiver> remotes() {
        return remotes;
    }

    public boolean rootFragment() {
        return !(root instanceof IgniteSender);
    }

    public Fragment attach(RelOptCluster cluster) {
        return root.getCluster() == cluster ? this : new Cloner(cluster).go(this);
    }

    /**
     * Maps the fragment to its data location.
     *
     * @param ctx Planner context.
     * @param mq  Metadata query.
     */
    Fragment map(MappingService mappingSrvc, MappingQueryContext ctx, RelMetadataQuery mq) throws FragmentMappingException {
        if (mapping != null) {
            return this;
        }

        return new Fragment(id, root, remotes, rootSer, mapping(ctx, mq, nodesSource(mappingSrvc, ctx)));
    }

    @NotNull
    private Supplier<List<String>> nodesSource(MappingService mappingSrvc, MappingQueryContext ctx) {
        return () -> mappingSrvc.executionNodes(single(), null);
    }

    private boolean single() {
        return root instanceof IgniteSender
                && ((IgniteSender) root).sourceDistribution().satisfies(IgniteDistributions.single());
    }

    /** {@inheritDoc} */
    @Override
    public String toString() {
        return S.toString(Fragment.class, this, "root", RelOptUtil.toString(root));
    }
}
