/*
 * Copyright © 2017-2019 Cask Data, Inc.
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

import io.cdap.wrangler.api.Arguments;
import io.cdap.wrangler.api.Directive;
import io.cdap.wrangler.api.DirectiveExecutionException;
import io.cdap.wrangler.api.DirectiveParseException;
import io.cdap.wrangler.api.ExecutorContext;
import io.cdap.wrangler.api.Row;
import io.cdap.wrangler.api.TransientVariableScope;
import io.cdap.wrangler.api.parser.ByteSize;
import io.cdap.wrangler.api.parser.ColumnName;
import io.cdap.wrangler.api.parser.TimeDuration;
import io.cdap.wrangler.api.parser.TokenType;
import io.cdap.wrangler.api.parser.UsageDefinition;

import java.util.List;

/**
 * A directive that aggregates statistics for byte sizes and time durations
 * across rows.
 * This class processes a list of {@code Row} objects, extracting byte size and
 * time duration
 * values from specified columns, accumulating their totals, and outputting the
 * results in
 * designated columns. It supports byte size units (B, KB, MB, GB, TB, PB) and
 * time duration
 * units (ms, s, m, h, d), storing aggregated stats in a transient store for
 * persistence
 * across executions.
 *
 * <p>
 * When the input list of rows is empty, the directive returns a single row
 * containing
 * the total size in megabytes, total time in seconds, and the count of
 * processed rows.
 * Otherwise, it returns the input rows unchanged after updating the aggregated
 * stats.
 * </p>
 *
 * @see Directive
 * @see Row
 * @see ExecutorContext
 * @since 2017-01-01
 */
public class AggregateStats implements Directive {
    /**
     * The name of this directive.
     */
    public static final String NAME = "aggregate-stats";

    /**
     * The name of the column containing byte size values.
     */
    private String sizeColumn;

    /**
     * The name of the column containing time duration values.
     */
    private String timeColumn;

    /**
     * The name of the column to store the total size in megabytes.
     */
    private String totalSizeColumn;

    /**
     * The name of the column to store the total time in seconds.
     */
    private String totalTimeColumn;

    /**
     * Defines the usage syntax for this directive.
     *
     * @return the {@code UsageDefinition} specifying the expected arguments.
     */
    @Override
    public UsageDefinition define() {
        UsageDefinition.Builder builder = UsageDefinition.builder(NAME);
        builder.define("size_column", TokenType.COLUMN_NAME);
        builder.define("time_column", TokenType.COLUMN_NAME);
        builder.define("total_size_column", TokenType.COLUMN_NAME);
        builder.define("total_time_column", TokenType.COLUMN_NAME);
        return builder.build();
    }

    /**
     * Initializes the directive with the provided arguments.
     *
     * @param args the {@code Arguments} containing the column names.
     * @throws DirectiveParseException if the arguments are invalid.
     */
    @Override
    public void initialize(Arguments args) throws DirectiveParseException {
        this.sizeColumn = ((ColumnName) args.value("size_column")).value();
        this.timeColumn = ((ColumnName) args.value("time_column")).value();
        this.totalSizeColumn = ((ColumnName) args.value("total_size_column")).value();
        this.totalTimeColumn = ((ColumnName) args.value("total_time_column")).value();
    }

    /**
     * Executes the directive, processing rows to aggregate byte size and time
     * duration statistics.
     *
     * <p>
     * For each row, the directive extracts byte size and time duration values from
     * the
     * specified columns, converts them to bytes and nanoseconds, respectively, and
     * updates
     * the aggregated statistics in a transient store. If the input list is empty,
     * it returns
     * a single row with the total size in megabytes, total time in seconds, and row
     * count.
     * </p>
     *
     * @param rows    the list of {@code Row} objects to process.
     * @param context the {@code ExecutorContext} providing access to the transient
     *                store.
     * @return the processed list of {@code Row} objects, or a single result row if
     *         the input is empty.
     * @throws DirectiveExecutionException if an error occurs during execution.
     */
    @Override
    public List<Row> execute(List<Row> rows, ExecutorContext context) throws DirectiveExecutionException {
        // Get the store from context
        String storeKey = NAME + "-" + sizeColumn + "-" + timeColumn;
        AggregateStatsStore store = (AggregateStatsStore) context.getTransientStore().get(storeKey);

        if (store == null) {
            store = new AggregateStatsStore();
            context.getTransientStore().set(TransientVariableScope.GLOBAL, storeKey, store);
        }

        // Process each row
        for (Row row : rows) {
            if (row.find(sizeColumn) != -1 && row.find(timeColumn) != -1) {
                Object sizeObj = row.getValue(sizeColumn);
                Object timeObj = row.getValue(timeColumn);

                // Handle byte size
                long bytes = 0;
                if (sizeObj instanceof ByteSize) {
                    bytes = ((ByteSize) sizeObj).getBytes();
                } else if (sizeObj instanceof String) {
                    try {
                        bytes = new ByteSize((String) sizeObj).getBytes();
                    } catch (Exception e) {
                        // Skip if not parseable
                        continue;
                    }
                } else if (sizeObj instanceof Number) {
                    bytes = ((Number) sizeObj).longValue();
                }

                // Handle time duration
                long nanos = 0;
                if (timeObj instanceof TimeDuration) {
                    nanos = ((TimeDuration) timeObj).getNanos();
                } else if (timeObj instanceof String) {
                    try {
                        nanos = new TimeDuration((String) timeObj).getNanos();
                    } catch (Exception e) {
                        // Skip if not parseable
                        continue;
                    }
                } else if (timeObj instanceof Number) {
                    nanos = ((Number) timeObj).longValue() * 1_000_000; // Assume milliseconds
                }

                store.addStats(bytes, nanos);
            }
        }

        // Output results if no more rows
        if (rows.isEmpty()) {
            Row result = new Row();
            double totalSizeMB = store.getTotalBytes() / (1024.0 * 1024.0);
            result.add(totalSizeColumn, totalSizeMB);
            double totalTimeSec = store.getTotalNanos() / 1_000_000_000.0;
            result.add(totalTimeColumn, totalTimeSec);
            result.add("row_count", store.getRowCount());
            return List.of(result);
        }

        return rows;
    }

    /**
     * Destroys the directive, releasing any resources.
     * <p>
     * This implementation has no resources to clean up.
     * </p>
     */
    @Override
    public void destroy() {
        // No resources to clean up
    }

    /**
     * A store for aggregating byte size and time duration statistics.
     * This class maintains running totals of bytes, nanoseconds, and row counts,
     * allowing the {@code AggregateStats} directive to persist statistics across
     * multiple executions.
     */
    public static class AggregateStatsStore {
        /**
         * The total number of bytes accumulated.
         */
        private long totalBytes = 0;

        /**
         * The total number of nanoseconds accumulated.
         */
        private long totalNanos = 0;

        /**
         * The total number of rows processed.
         */
        private int rowCount = 0;

        /**
         * Adds byte size and time duration statistics to the store.
         *
         * @param bytes the byte size to add.
         * @param nanos the time duration in nanoseconds to add.
         */
        public void addStats(long bytes, long nanos) {
            totalBytes += bytes;
            totalNanos += nanos;
            rowCount++;
        }

        /**
         * Returns the total number of bytes accumulated.
         *
         * @return the total bytes.
         */
        public long getTotalBytes() {
            return totalBytes;
        }

        /**
         * Returns the total number of nanoseconds accumulated.
         *
         * @return the total nanoseconds.
         */
        public long getTotalNanos() {
            return totalNanos;
        }

        /**
         * Returns the total number of rows processed.
         *
         * @return the row count.
         */
        public int getRowCount() {
            return rowCount;
        }
    }
}
