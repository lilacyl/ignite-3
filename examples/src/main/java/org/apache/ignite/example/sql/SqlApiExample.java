/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
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

package org.apache.ignite.example.sql;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.apache.ignite.client.IgniteClient;
import org.apache.ignite.sql.BatchedArguments;
import org.apache.ignite.sql.ResultSet;
import org.apache.ignite.sql.Session;
import org.apache.ignite.sql.SqlRow;
import org.apache.ignite.sql.Statement;
import org.apache.ignite.sql.async.AsyncResultSet;

/**
 * Examples of using SQL API.
 */
public class SqlApiExample {
    /**
     * Main method of the example.
     *
     * @param args The command line arguments.
     * @throws Exception If failed.
     */
    public static void main(String[] args) throws Exception {
        //--------------------------------------------------------------------------------------
        //
        // Creating a client to connect to the cluster.
        //
        //--------------------------------------------------------------------------------------

        System.out.println("\nConnecting to server...");

        try (IgniteClient client = IgniteClient.builder()
                .addresses("127.0.0.1:10800")
                .build()
        ) {
            //--------------------------------------------------------------------------------------
            //
            // Creating tables.
            //
            //--------------------------------------------------------------------------------------

            try (Session ses = client.sql().createSession()) {
                ses.execute(
                        null,
                        "CREATE TABLE CITIES ("
                                + "ID   INT PRIMARY KEY,"
                                + "NAME VARCHAR)"
                ).close(); // Ignore result.

                ses.execute(
                        null,
                        "CREATE TABLE ACCOUNTS ("
                                + "    ACCOUNT_ID INT PRIMARY KEY,"
                                + "    CITY_ID    INT,"
                                + "    FIRST_NAME VARCHAR,"
                                + "    LAST_NAME  VARCHAR,"
                                + "    BALANCE    DOUBLE)"
                ).close();

                //--------------------------------------------------------------------------------------
                //
                // Populating 'CITIES' table.
                //
                //--------------------------------------------------------------------------------------

                System.out.println("\nPopulating 'CITIES' table...");

                try (Statement stmt = client.sql().createStatement("INSERT INTO CITIES (ID, NAME) VALUES (?, ?)")) {
                    long rowsAdded = 0;

                    try (ResultSet rs = ses.execute(null, stmt, 1, "Forest Hill")) {
                        rowsAdded += rs.affectedRows();
                    }
                    try (ResultSet rs = ses.execute(null, stmt, 2, "Denver")) {
                        rowsAdded += rs.affectedRows();
                    }
                    try (ResultSet rs = ses.execute(null, stmt, 3, "St. Petersburg")) {
                        rowsAdded += rs.affectedRows();
                    }

                    System.out.println("\nAdded cities: " + rowsAdded);
                }

                //--------------------------------------------------------------------------------------
                //
                // Populating 'ACCOUNTS' table.
                //
                //--------------------------------------------------------------------------------------

                System.out.println("\nPopulating 'ACCOUNTS' table...");

                long rowsAdded = Arrays.stream(ses.executeBatch(null,
                                "INSERT INTO ACCOUNTS (ACCOUNT_ID, CITY_ID, FIRST_NAME, LAST_NAME, BALANCE) values (?, ?, ?, ?, ?)",
                                BatchedArguments.of(1, 1, "John", "Doe", 1000.0d)
                                        .add(2, 1, "Jane", "Roe", 2000.0d)
                                        .add(3, 1, "Mary", "Major", 1500.0d)
                                        .add(4, 1, "Richard", "Miles", 1450.0d)))
                        .sum();

                System.out.println("\nAdded accounts: " + rowsAdded);

                //--------------------------------------------------------------------------------------
                //
                // Requesting information about all account owners.
                //
                //--------------------------------------------------------------------------------------

                System.out.println("\nAll accounts:");

                try (ResultSet rs = ses.execute(null,
                        "SELECT a.FIRST_NAME, a.LAST_NAME, c.NAME FROM ACCOUNTS a "
                                + "INNER JOIN CITIES c on c.ID = a.CITY_ID ORDER BY a.ACCOUNT_ID")) {
                    while (rs.hasNext()) {
                        SqlRow row = rs.next();

                        System.out.println("    "
                                + row.stringValue(1) + ", "
                                + row.stringValue(2) + ", "
                                + row.stringValue(3));
                    }
                }

                //--------------------------------------------------------------------------------------
                //
                // Requesting accounts with balances lower than 1,500.
                //
                //--------------------------------------------------------------------------------------

                System.out.println("\nAccounts with balance lower than 1,500:");

                try (ResultSet rs = ses.execute(null,
                        "SELECT a.FIRST_NAME, a.LAST_NAME, a.BALANCE FROM ACCOUNTS a WHERE a.BALANCE < 1500.0 "
                                + "ORDER BY a.ACCOUNT_ID")) {
                    while (rs.hasNext()) {
                        SqlRow row = rs.next();

                        System.out.println("    "
                                + row.stringValue(1) + ", "
                                + row.stringValue(2) + ", "
                                + row.stringValue(3));
                    }
                }

                //--------------------------------------------------------------------------------------
                //
                // Deleting one of the accounts.
                //
                //--------------------------------------------------------------------------------------

                System.out.println("\nDeleting one of the accounts...");

                try (ResultSet rs = ses.execute(null, "DELETE FROM ACCOUNTS WHERE ACCOUNT_ID = ?", 1)) {
                    System.out.println("\n Removed accounts: " + rs.affectedRows());
                }

                //--------------------------------------------------------------------------------------
                //
                // Requesting information about all account owners once again
                // to verify that the account was actually deleted.
                //
                //--------------------------------------------------------------------------------------

                System.out.println("\nAll accounts:");

                // Async way.
                Statement stmt = client.sql().statementBuilder()
                        .query("SELECT a.FIRST_NAME, a.LAST_NAME, c.NAME FROM ACCOUNTS a "
                                + "INNER JOIN CITIES c on c.ID = a.CITY_ID ORDER BY a.ACCOUNT_ID")
                        .pageSize(1)
                        .build();

                ses.executeAsync(null, stmt)
                        .thenCompose(SqlApiExample::fetchAllRowsInto)
                        .get();

                stmt.close();

                System.out.println("\nDropping the tables...");

                ses.execute(null, "DROP TABLE ACCOUNTS").close();
                ses.execute(null, "DROP TABLE CITIES").close();
            }
        }
    }

    /**
     * Fetch full result set asynchronously.
     *
     * @param resultSet Async result set.
     * @return Operation future.
     */
    private static CompletionStage<Void> fetchAllRowsInto(AsyncResultSet resultSet) {
        //
        // Process current page.
        //
        for (var row : resultSet.currentPage()) {
            System.out.println("    "
                    + row.stringValue(1) + ", "
                    + row.stringValue(2) + ", "
                    + row.stringValue(3));
        }

        //
        // Finish if no more data.
        //
        if (!resultSet.hasMorePages()) {
            return CompletableFuture.completedFuture(null);
        }

        //
        // Request for the next page in async way, then subscribe to the response.
        //
        return resultSet.fetchNextPage().thenCompose(SqlApiExample::fetchAllRowsInto);
    }
}
