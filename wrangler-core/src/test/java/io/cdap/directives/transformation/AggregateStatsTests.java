/*
 * Copyright © 2025 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.directives.transformation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.cdap.directives.transformation.AggregateStats.AggregateStatsStore;
import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientStore;
import io.cdap.wrangler.api.TransientVariableScope;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.Token;
import io.cdap.wrangler.api.parser.TokenType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unit tests for the {@link AggregateStats} directive.
*/
public class AggregateStatsTests {

    private AggregateStats directive;
    private ExecutorContext context;
    private TransientStore store;
    private Arguments args;

    @Before
    public void setUp() {
        directive = new AggregateStats();
        context = Mockito.mock(ExecutorContext.class);
        store = new InMemoryTransientStore();
        Mockito.when(context.getTransientStore()).thenReturn(store);

        // Initialize arguments
        args = new TestArguments(
                Map.of(
                        "size_column", new ColumnName("size"),
                        "time_column", new ColumnName("time"),
                        "total_size_column", new ColumnName("total_size_mb"),
                        "total_time_column", new ColumnName("total_time_sec")
                )
        );
    }

    @Test
    public void testInitialize() throws DirectiveParseException {
        directive.initialize(args);
        Assert.assertEquals("size", getField("sizeColumn"));
        Assert.assertEquals("time", getField("timeColumn"));
        Assert.assertEquals("total_size_mb", getField("totalSizeColumn"));
        Assert.assertEquals("total_time_sec", getField("totalTimeColumn"));
    }

    @Test
    public void testExecuteSingleRow() throws Exception {
        directive.initialize(args);
        List<Row> rows = new ArrayList<>();
        Row row = new Row();
        row.add("size", new ByteSize("1MB"));
        row.add("time", new TimeDuration("1s"));
        rows.add(row);

        List<Row> result = directive.execute(rows, context);
        Assert.assertEquals(1, result.size());
        Assert.assertSame(rows, result);

        // Check store
        String storeKey = AggregateStats.NAME + "-size-time";
        AggregateStatsStore statsStore = (AggregateStatsStore) store.get(storeKey);
        Assert.assertEquals(1024 * 1024, statsStore.getTotalBytes());
        Assert.assertEquals(1_000_000_000L, statsStore.getTotalNanos());
        Assert.assertEquals(1, statsStore.getRowCount());
    }

    @Test
    public void testExecuteMultipleRows() throws Exception {
        directive.initialize(args);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("size", new ByteSize("2MB")).add("time", new TimeDuration("500ms")));
        rows.add(new Row("size", new ByteSize("3MB")).add("time", new TimeDuration("1.5s")));

        List<Row> result = directive.execute(rows, context);
        Assert.assertEquals(2, result.size());
        Assert.assertSame(rows, result);

        // Check store
        String storeKey = AggregateStats.NAME + "-size-time";
        AggregateStatsStore statsStore = (AggregateStatsStore) store.get(storeKey);
        Assert.assertEquals(5 * 1024 * 1024, statsStore.getTotalBytes());
        Assert.assertEquals(2_000_000_000L, statsStore.getTotalNanos());
        Assert.assertEquals(2, statsStore.getRowCount());
    }

    @Test
    public void testExecuteEmptyRows() throws Exception {
        directive.initialize(args);
        // Add some data to store first
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("size", new ByteSize("1GB")).add("time", new TimeDuration("1m")));
        directive.execute(rows, context);

        // Now test empty input
        List<Row> result = directive.execute(new ArrayList<>(), context);
        Assert.assertEquals(1, result.size());
        Row resultRow = result.get(0);
        Assert.assertEquals(1024.0, (Double) resultRow.getValue("total_size_mb"), 0.001);
        Assert.assertEquals(60.0, (Double) resultRow.getValue("total_time_sec"), 0.001);
        Assert.assertEquals(1, resultRow.getValue("row_count"));
    }

    @Test
    public void testExecuteInvalidData() throws Exception {
        directive.initialize(args);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("size", "invalid").add("time", "also invalid"));
        rows.add(new Row("size", new ByteSize("1KB")).add("time", new TimeDuration("100ms")));

        List<Row> result = directive.execute(rows, context);
        Assert.assertEquals(2, result.size());

        // Only valid row should be counted
        String storeKey = AggregateStats.NAME + "-size-time";
        AggregateStatsStore statsStore = (AggregateStatsStore) store.get(storeKey);
        Assert.assertEquals(1024, statsStore.getTotalBytes());
        Assert.assertEquals(100_000_000L, statsStore.getTotalNanos());
        Assert.assertEquals(1, statsStore.getRowCount());
    }

    @Test
    public void testExecuteNumericInputs() throws Exception {
        directive.initialize(args);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("size", 1024L).add("time", 1000L)); // 1024 bytes, 1000ms

        List<Row> result = directive.execute(rows, context);
        Assert.assertEquals(1, result.size());

        String storeKey = AggregateStats.NAME + "-size-time";
        AggregateStatsStore statsStore = (AggregateStatsStore) store.get(storeKey);
        Assert.assertEquals(1024, statsStore.getTotalBytes());
        Assert.assertEquals(1_000_000_000L, statsStore.getTotalNanos());
        Assert.assertEquals(1, statsStore.getRowCount());
    }

    @Test
    public void testExecuteNoMatchingColumns() throws Exception {
        directive.initialize(args);
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("other_column", "value"));

        List<Row> result = directive.execute(rows, context);
        Assert.assertEquals(1, result.size());
    }

    @Test
    public void testDestroy() {
        directive.destroy(); // Should not throw any exceptions
    }

    /**
     * Helper method to access private fields for testing.
     */
    private String getField(String fieldName) {
        try {
            java.lang.reflect.Field field = AggregateStats.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return (String) field.get(directive);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to access field: " + fieldName, e);
        }
    }

    /**
     * Concrete implementation of Arguments for testing.
     */
    private static class TestArguments implements Arguments {
        private final Map<String, Token> argMap;

        TestArguments(Map<String, Token> argMap) {
            this.argMap = argMap;
        }

        @SuppressWarnings("unchecked")
        @Override
        public Token value(String name) {
            return argMap.get(name);
        }

        @Override
        public boolean contains(String name) {
            return argMap.containsKey(name);
        }

        @Override
        public JsonElement toJson() {
            return new JsonObject(); // Minimal implementation for testing
        }

        @Override
        public int column() {
            return -1; // Not used in directive
        }

        @Override
        public int line() {
            return 0; // Not used in directive
        }

        @Override
        public int size() {
            return argMap.size();
        }

        @Override
        public TokenType type(String name) {
            Token token = argMap.get(name);
            return token != null ? token.type() : null;
        }

        @Override
        public String source() {
            return null; // Not used in directive
        }
    }

    /**
     * In-memory implementation of TransientStore for testing.
     */
    private static class InMemoryTransientStore implements TransientStore {
        private final Map<String, Object> store = new HashMap<>();

        @Override
        public void set(TransientVariableScope scope, String name, Object value) {
            store.put(name, value);
        }

        @Override
        public <T> T get(String name) {
            @SuppressWarnings("unchecked")
            T result = (T) store.get(name);
            return result;
        }

        @Override
        public void reset(TransientVariableScope scope) {
            store.clear();
        }

        @Override
        public void increment(TransientVariableScope scope, String name, long value) {
            // Not used in this directive
        }

        @Override
        public Set<String> getVariables() {
            return store.keySet();
        }
    }
}
