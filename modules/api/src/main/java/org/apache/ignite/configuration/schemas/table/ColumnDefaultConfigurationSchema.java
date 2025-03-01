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

package org.apache.ignite.configuration.schemas.table;

import org.apache.ignite.configuration.annotation.PolymorphicConfig;
import org.apache.ignite.configuration.annotation.PolymorphicId;

/**
 * Configuration of default value for table column.
 */
@PolymorphicConfig
public class ColumnDefaultConfigurationSchema {
    /** Default value is not specified or specified as null explicitly. */
    public static final String NULL_VALUE_TYPE = "NULL";

    /** Default value is non-null constant. */
    public static final String CONSTANT_VALUE_TYPE = "CONSTANT";

    /** Default value provided by a function call. */
    public static final String FUNCTION_CALL_TYPE = "FUNCTION";

    /** Type of the default value provider. */
    @PolymorphicId(hasDefault = true)
    public String type = NULL_VALUE_TYPE;
}
