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

package org.apache.ignite.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.util.BitSet;
import java.util.Collections;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.apache.ignite.internal.client.proto.ClientDataType;
import org.apache.ignite.internal.client.table.ClientColumn;
import org.apache.ignite.internal.client.table.ClientSchema;
import org.apache.ignite.internal.client.table.ClientTuple;
import org.apache.ignite.lang.ColumnNotFoundException;
import org.apache.ignite.table.Tuple;
import org.junit.jupiter.api.Test;

/**
 * Tests client tuple builder implementation.
 *
 * <p>Should be in sync with org.apache.ignite.internal.table.TupleBuilderImplTest.
 */
public class ClientTupleTest {
    private static final ClientSchema SCHEMA = new ClientSchema(1, new ClientColumn[]{
            new ClientColumn("ID", ClientDataType.INT64, false, true, 0),
            new ClientColumn("NAME", ClientDataType.STRING, false, false, 1)
    });

    @Test
    public void testValueReturnsValueByName() {
        assertEquals(3L, (Long) getTuple().value("id"));
        assertEquals("Shirt", getTuple().value("name"));
    }

    @Test
    public void testValueReturnsValueByIndex() {
        assertEquals(3L, (Long) getTuple().value(0));
        assertEquals("Shirt", getTuple().value(1));
    }

    @Test
    public void testValueOrDefaultReturnsValueByName() {
        assertEquals(3L, getTuple().valueOrDefault("id", -1L));
        assertEquals("Shirt", getTuple().valueOrDefault("name", "y"));
    }

    @Test
    public void testValueOrDefaultReturnsDefaultWhenColumnIsNotPresent() {
        assertEquals("foo", getBuilder().valueOrDefault("x", "foo"));
    }

    @Test
    public void testValueOrDefaultReturnsDefaultWhenColumnIsPresentButNotSet() {
        assertEquals("foo", getBuilder().valueOrDefault("name", "foo"));
    }

    @Test
    public void testValueOrDefaultReturnsNullWhenColumnIsSetToNull() {
        var tuple = getBuilder().set("name", null);

        assertNull(tuple.valueOrDefault("name", "foo"));
    }

    @Test
    public void testEmptySchemaThrows() {
        assertThrows(AssertionError.class, () -> new ClientTuple(new ClientSchema(1, new ClientColumn[0])));
    }

    @Test
    public void testSetThrowsWhenColumnIsNotPresent() {
        var ex = assertThrows(ColumnNotFoundException.class, () -> getBuilder().set("x", "y"));
        assertThat(ex.getMessage(), containsString("Column 'X' does not exist"));
    }

    @Test
    public void testValueThrowsWhenColumnIsNotPresent() {
        var ex = assertThrows(ColumnNotFoundException.class, () -> getBuilder().value("x"));
        assertThat(ex.getMessage(), containsString("Column 'X' does not exist"));

        var ex2 = assertThrows(IndexOutOfBoundsException.class, () -> getBuilder().value(100));
        assertThat(ex2.getMessage(), containsString("Index 100 out of bounds for length 2"));
    }

    @Test
    public void testColumnCountReturnsSchemaSize() {
        assertEquals(SCHEMA.columns().length, getTuple().columnCount());
    }

    @Test
    public void testColumnNameReturnsNameByIndex() {
        assertEquals("ID", getTuple().columnName(0));
        assertEquals("NAME", getTuple().columnName(1));
    }

    @Test
    public void testColumnNameThrowsOnInvalidIndex() {
        var ex = assertThrows(IndexOutOfBoundsException.class, () -> getTuple().columnName(-1));
        assertEquals("Index -1 out of bounds for length 2", ex.getMessage());
    }

    @Test
    public void testColumnIndexReturnsIndexByName() {
        assertEquals(0, getTuple().columnIndex("id"));
        assertEquals(1, getTuple().columnIndex("name"));
    }

    @Test
    public void testColumnIndexForMissingColumns() {
        assertEquals(-1, getTuple().columnIndex("foo"));
    }

    @Test
    public void testTypedGetters() {
        var schema = new ClientSchema(100, new ClientColumn[]{
                new ClientColumn("I8", ClientDataType.INT8, false, false, 0),
                new ClientColumn("I16", ClientDataType.INT16, false, false, 1),
                new ClientColumn("I32", ClientDataType.INT32, false, false, 2),
                new ClientColumn("I64", ClientDataType.INT64, false, false, 3),
                new ClientColumn("FLOAT", ClientDataType.FLOAT, false, false, 4),
                new ClientColumn("DOUBLE", ClientDataType.DOUBLE, false, false, 5),
                new ClientColumn("UUID", ClientDataType.UUID, false, false, 6),
                new ClientColumn("STR", ClientDataType.STRING, false, false, 7),
                new ClientColumn("BITS", ClientDataType.BITMASK, false, false, 8),
                new ClientColumn("TIME", ClientDataType.TIME, false, false, 9),
                new ClientColumn("DATE", ClientDataType.DATE, false, false, 10),
                new ClientColumn("DATETIME", ClientDataType.DATETIME, false, false, 11),
                new ClientColumn("TIMESTAMP", ClientDataType.TIMESTAMP, false, false, 12)
        });

        var uuid = UUID.randomUUID();

        var date = LocalDate.of(1995, Month.MAY, 23);
        var time = LocalTime.of(17, 0, 1, 222_333_444);
        var datetime = LocalDateTime.of(1995, Month.MAY, 23, 17, 0, 1, 222_333_444);
        var timestamp = Instant.now();

        var tuple = new ClientTuple(schema)
                .set("i8", (byte) 1)
                .set("i16", (short) 2)
                .set("i32", (int) 3)
                .set("i64", (long) 4)
                .set("float", (float) 5.5)
                .set("double", (double) 6.6)
                .set("uuid", uuid)
                .set("str", "8")
                .set("bits", new BitSet(3))
                .set("date", date)
                .set("time", time)
                .set("datetime", datetime)
                .set("timestamp", timestamp);

        assertEquals(1, tuple.byteValue(0));
        assertEquals(1, tuple.byteValue("i8"));

        assertEquals(2, tuple.shortValue(1));
        assertEquals(2, tuple.shortValue("i16"));

        assertEquals(3, tuple.intValue(2));
        assertEquals(3, tuple.intValue("i32"));

        assertEquals(4, tuple.longValue(3));
        assertEquals(4, tuple.longValue("i64"));

        assertEquals(5.5, tuple.floatValue(4));
        assertEquals(5.5, tuple.floatValue("float"));

        assertEquals(6.6, tuple.doubleValue(5));
        assertEquals(6.6, tuple.doubleValue("double"));

        assertEquals(uuid, tuple.uuidValue(6));
        assertEquals(uuid, tuple.uuidValue("uuid"));

        assertEquals("8", tuple.stringValue(7));
        assertEquals("8", tuple.stringValue("str"));

        assertEquals(0, tuple.bitmaskValue(8).length());
        assertEquals(0, tuple.bitmaskValue("bits").length());

        assertEquals(date, tuple.dateValue("date"));
        assertEquals(time, tuple.timeValue("time"));
        assertEquals(datetime, tuple.datetimeValue("datetime"));
        assertEquals(timestamp, tuple.timestampValue("timestamp"));
    }

    @Test
    public void testBasicTupleEquality() {
        var tuple = new ClientTuple(SCHEMA);
        var tuple2 = new ClientTuple(SCHEMA);

        assertEquals(tuple, tuple);
        assertEquals(tuple, tuple2);
        assertEquals(tuple.hashCode(), tuple2.hashCode());

        assertNotEquals(new ClientTuple(SCHEMA), new ClientTuple(new ClientSchema(1, new ClientColumn[]{
                new ClientColumn("id", ClientDataType.INT64, false, true, 0)})));

        assertEquals(new ClientTuple(SCHEMA).set("name", null), new ClientTuple(SCHEMA).set("name", null));
        assertEquals(new ClientTuple(SCHEMA).set("name", null).hashCode(), new ClientTuple(SCHEMA).set("name", null).hashCode());

        assertEquals(new ClientTuple(SCHEMA).set("name", "bar"), new ClientTuple(SCHEMA).set("name", "bar"));
        assertEquals(new ClientTuple(SCHEMA).set("name", "bar").hashCode(), new ClientTuple(SCHEMA).set("name", "bar").hashCode());

        assertNotEquals(new ClientTuple(SCHEMA).set("name", "foo"), new ClientTuple(SCHEMA).set("id", 1));
        assertNotEquals(new ClientTuple(SCHEMA).set("name", "foo"), new ClientTuple(SCHEMA).set("name", "bar"));

        tuple = new ClientTuple(SCHEMA);
        tuple2 = new ClientTuple(SCHEMA);

        tuple.set("name", "bar");

        assertEquals(tuple, tuple);
        assertNotEquals(tuple, tuple2);
        assertNotEquals(tuple2, tuple);

        tuple2.set("name", "baz");

        assertNotEquals(tuple, tuple2);
        assertNotEquals(tuple2, tuple);

        tuple2.set("name", "bar");

        assertEquals(tuple, tuple2);
        assertEquals(tuple2, tuple);
    }

    @Test
    public void testTupleEquality() {
        var schema = new ClientSchema(100, new ClientColumn[]{
                new ClientColumn("I8", ClientDataType.INT8, false, false, 0),
                new ClientColumn("I16", ClientDataType.INT16, false, false, 1),
                new ClientColumn("I32", ClientDataType.INT32, false, false, 2),
                new ClientColumn("I64", ClientDataType.INT64, false, false, 3),
                new ClientColumn("FLOAT", ClientDataType.FLOAT, false, false, 4),
                new ClientColumn("DOUBLE", ClientDataType.DOUBLE, false, false, 5),
                new ClientColumn("UUID", ClientDataType.UUID, false, false, 6),
                new ClientColumn("STR", ClientDataType.STRING, false, false, 7),
                new ClientColumn("BITS", ClientDataType.BITMASK, false, false, 8),
                new ClientColumn("TIME", ClientDataType.TIME, false, false, 9),
                new ClientColumn("DATE", ClientDataType.DATE, false, false, 10),
                new ClientColumn("DATETIME", ClientDataType.DATETIME, false, false, 11),
                new ClientColumn("TIMESTAMP", ClientDataType.TIMESTAMP, false, false, 12)
        });

        var uuid = UUID.randomUUID();

        var date = LocalDate.of(1995, Month.MAY, 23);
        var time = LocalTime.of(17, 0, 1, 222_333_444);
        var datetime = LocalDateTime.of(1995, Month.MAY, 23, 17, 0, 1, 222_333_444);
        var timestamp = Instant.now();

        var tuple = new ClientTuple(schema)
                .set("i8", (byte) 1)
                .set("i16", (short) 2)
                .set("i32", (int) 3)
                .set("i64", (long) 4)
                .set("float", (float) 5.5)
                .set("double", (double) 6.6)
                .set("uuid", uuid)
                .set("str", "8")
                .set("bits", new BitSet(3))
                .set("date", date)
                .set("time", time)
                .set("datetime", datetime)
                .set("timestamp", timestamp);
        var randomIdx = IntStream.range(0, tuple.columnCount()).boxed().collect(Collectors.toList());

        Collections.shuffle(randomIdx);

        var shuffledTuple = new ClientTuple(schema);

        for (Integer i : randomIdx) {
            shuffledTuple.set(tuple.columnName(i), tuple.value(i));
        }

        assertEquals(tuple, shuffledTuple);
        assertEquals(tuple.hashCode(), shuffledTuple.hashCode());
    }

    @Test
    public void testTupleEqualityCompatibility() {
        var schema = new ClientSchema(100, new ClientColumn[]{
                new ClientColumn("I8", ClientDataType.INT8, false, false, 0),
                new ClientColumn("I16", ClientDataType.INT16, false, false, 1),
                new ClientColumn("I32", ClientDataType.INT32, false, false, 2),
                new ClientColumn("I64", ClientDataType.INT64, false, false, 3),
                new ClientColumn("FLOAT", ClientDataType.FLOAT, false, false, 4),
                new ClientColumn("DOUBLE", ClientDataType.DOUBLE, false, false, 5),
                new ClientColumn("UUID", ClientDataType.UUID, false, false, 6),
                new ClientColumn("STR", ClientDataType.STRING, false, false, 7),
                new ClientColumn("BITS", ClientDataType.BITMASK, false, false, 8),
                new ClientColumn("TIME", ClientDataType.TIME, false, false, 9),
                new ClientColumn("DATE", ClientDataType.DATE, false, false, 10),
                new ClientColumn("DATETIME", ClientDataType.DATETIME, false, false, 11),
                new ClientColumn("TIMESTAMP", ClientDataType.TIMESTAMP, false, false, 12)
        });

        var uuid = UUID.randomUUID();

        var date = LocalDate.of(1995, Month.MAY, 23);
        var time = LocalTime.of(17, 0, 1, 222_333_444);
        var datetime = LocalDateTime.of(1995, Month.MAY, 23, 17, 0, 1, 222_333_444);
        var timestamp = Instant.now();

        var clientTuple = new ClientTuple(schema)
                .set("i8", (byte) 1)
                .set("i16", (short) 2)
                .set("i32", (int) 3)
                .set("i64", (long) 4)
                .set("float", (float) 5.5)
                .set("double", (double) 6.6)
                .set("uuid", uuid)
                .set("str", "8")
                .set("bits", new BitSet(3))
                .set("date", date)
                .set("time", time)
                .set("datetime", datetime)
                .set("timestamp", timestamp);

        var tuple = Tuple.create();

        for (int i = 0; i < clientTuple.columnCount(); i++) {
            tuple.set(clientTuple.columnName(i), clientTuple.value(i));
        }

        assertEquals(clientTuple, tuple);
        assertEquals(clientTuple.hashCode(), tuple.hashCode());
    }

    private static ClientTuple getBuilder() {
        return new ClientTuple(SCHEMA);
    }

    private static Tuple getTuple() {
        return new ClientTuple(SCHEMA)
                .set("id", 3L)
                .set("name", "Shirt");
    }
}
