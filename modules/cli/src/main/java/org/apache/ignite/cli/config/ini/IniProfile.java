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

package org.apache.ignite.cli.config.ini;

import org.apache.ignite.cli.config.Config;
import org.apache.ignite.cli.config.Profile;

/**
 * Implementation of {@link Profile} based on {@link IniSection}.
 */
public class IniProfile implements Profile {
    private final IniSection section;
    private final IniConfig config;

    public IniProfile(IniSection section, Runnable saveAction) {
        this.section = section;
        this.config = new IniConfig(section, saveAction);
    }

    /** {@inheritDoc} */
    @Override
    public String getName() {
        return section.getName();
    }

    /** {@inheritDoc} */
    @Override
    public Config getConfig() {
        return config;
    }
}
