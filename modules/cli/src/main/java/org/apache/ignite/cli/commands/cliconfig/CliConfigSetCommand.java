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

package org.apache.ignite.cli.commands.cliconfig;

import static org.apache.ignite.cli.commands.OptionsConstants.PROFILE_OPTION;
import static org.apache.ignite.cli.commands.OptionsConstants.PROFILE_OPTION_DESC;
import static org.apache.ignite.cli.commands.OptionsConstants.PROFILE_OPTION_SHORT;

import jakarta.inject.Inject;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.ignite.cli.call.cliconfig.CliConfigSetCall;
import org.apache.ignite.cli.call.cliconfig.CliConfigSetCallInput;
import org.apache.ignite.cli.commands.BaseCommand;
import org.apache.ignite.cli.core.call.CallExecutionPipeline;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

/**
 * Command to set CLI configuration parameters.
 */
@Command(name = "set", description = "Sets configuration parameters")
public class CliConfigSetCommand extends BaseCommand implements Callable<Integer> {
    @Parameters(arity = "1..*", description = "Key-value pairs")
    private Map<String, String> parameters;

    @Option(names = {PROFILE_OPTION, PROFILE_OPTION_SHORT}, description = PROFILE_OPTION_DESC)
    private String profileName;

    @Inject
    private CliConfigSetCall call;

    @Override
    public Integer call() {
        return CallExecutionPipeline.builder(call)
                .inputProvider(CliConfigSetCallInput.builder()
                        .parameters(parameters)
                        .profileName(profileName)::build)
                .output(spec.commandLine().getOut())
                .errOutput(spec.commandLine().getErr())
                .build()
                .runPipeline();
    }
}
