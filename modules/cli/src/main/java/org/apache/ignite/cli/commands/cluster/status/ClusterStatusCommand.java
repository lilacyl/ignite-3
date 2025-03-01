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

package org.apache.ignite.cli.commands.cluster.status;

import jakarta.inject.Inject;
import java.util.concurrent.Callable;
import org.apache.ignite.cli.call.cluster.status.ClusterStatusCall;
import org.apache.ignite.cli.commands.BaseCommand;
import org.apache.ignite.cli.commands.cluster.ClusterUrlOptions;
import org.apache.ignite.cli.core.call.CallExecutionPipeline;
import org.apache.ignite.cli.core.call.StatusCallInput;
import org.apache.ignite.cli.decorators.ClusterStatusDecorator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;

/**
 * Command that prints status of ignite cluster.
 */
@Command(name = "status",
        aliases = "cluster show", //TODO: https://issues.apache.org/jira/browse/IGNITE-17102
        description = "Prints status of the cluster")
public class ClusterStatusCommand extends BaseCommand implements Callable<Integer> {
    /** Cluster endpoint URL option. */
    @Mixin
    private ClusterUrlOptions clusterUrl;

    @Inject
    private ClusterStatusCall clusterStatusCall;

    /** {@inheritDoc} */
    @Override
    public Integer call() {
        return CallExecutionPipeline.builder(clusterStatusCall)
                .inputProvider(() -> new StatusCallInput(clusterUrl.getClusterUrl()))
                .output(spec.commandLine().getOut())
                .errOutput(spec.commandLine().getErr())
                .decorator(new ClusterStatusDecorator())
                .build()
                .runPipeline();
    }
}
