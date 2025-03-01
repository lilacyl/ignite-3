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

package org.apache.ignite.internal.runner.app.jdbc;

import java.sql.Statement;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;

/**
 * Abstract jdbc test.
 */
public abstract class ItJdbcAbstractStatementSelfTest extends AbstractJdbcSelfTest {
    /** SQL query to create a table. */
    private static final String CREATE_TABLE_SQL = "CREATE TABLE public.person(id INTEGER PRIMARY KEY, sid VARCHAR,"
            + " firstname VARCHAR NOT NULL, lastname VARCHAR NOT NULL, age INTEGER NOT NULL)";

    /** SQL query to populate table. */
    private static final String ITEMS_SQL = "INSERT INTO public.person(sid, id, firstname, lastname, age) VALUES "
            + "('p1', 1, 'John', 'White', 25), "
            + "('p2', 2, 'Joe', 'Black', 35), "
            + "('p3', 3, 'Mike', 'Green', 40)";

    /** SQL query to clear table. */
    private static final String DROP_SQL = "DELETE FROM public.person;";

    @BeforeEach
    public void refillTable() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate(DROP_SQL);
            s.executeUpdate(ITEMS_SQL);
        }
    }

    @BeforeAll
    public static void createTable() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute(CREATE_TABLE_SQL);
        }
    }

    @AfterAll
    public static void drop() throws Exception {
        try (Statement s = conn.createStatement()) {
            s.execute("DROP TABLE public.person;");
        }
    }
}
