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

package org.apache.ignite.lang;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.apache.ignite.lang.ErrorGroups.Table;
import org.junit.jupiter.api.Test;

/**
 * Tests ignite exceptions.
 */
public class IgniteExceptionTest {
    @Test
    public void testWrapUncheckedException() {
        var originalEx = new CustomTestException(UUID.randomUUID(), Table.TABLE_NOT_FOUND_ERR, "Error foo bar", null);
        var wrappedEx = new CompletionException(originalEx);
        var res = IgniteException.wrap(wrappedEx);

        assertThat(res.getMessage(), containsString("Error foo bar"));
        assertEquals(originalEx.traceId(), res.traceId());
        assertEquals(originalEx.code(), res.code());
        assertEquals(originalEx.getClass(), res.getClass());
        assertSame(originalEx, res.getCause());
    }

    @Test
    public void testWrapCheckedException() {
        var originalEx = new IgniteCheckedException(Table.COLUMN_ALREADY_EXISTS_ERR, "Msg.");
        var wrappedEx = new CompletionException(originalEx);
        var res = IgniteException.wrap(wrappedEx);

        assertThat(res.getMessage(), containsString("Msg."));
        assertEquals(originalEx.traceId(), res.traceId());
        assertEquals(originalEx.code(), res.code());
        assertSame(originalEx, res.getCause());
    }

    /**
     * Custom exception for tests.
     */
    public static class CustomTestException extends IgniteException {
        public CustomTestException(UUID traceId, int code, String message, Throwable cause) {
            super(traceId, code, message, cause);
        }
    }
}
