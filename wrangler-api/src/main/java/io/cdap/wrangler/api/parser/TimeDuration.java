/**
 * Copyright © 2019 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.cdap.wrangler.api.parser;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * The TimeDuration class represents a time duration value parsed from a string.
 * This class encapsulates a time duration specified in formats such as "100ms",
 * "5s", "2m",
 * or "1h" and converts it into a numerical value in nanoseconds. It implements
 * the
 * {@code Token} interface to provide type information and JSON serialization
 * for use
 * in the wrangler parser.
 *
 * <p>
 * The class stores both the original string input and the parsed nanosecond
 * value,
 * allowing access to either representation as needed. It supports common time
 * duration
 * units including milliseconds (ms), seconds (s), minutes (m), and hours (h),
 * with a
 * default assumption of nanoseconds if no unit is specified.
 * </p>
 *
 * @see BoolList
 * @see ByteSize
 * @see Numeric
 * @since 2019-04-12
 */
public class TimeDuration implements Token {
    /**
     * The original string representation of the time duration (e.g., "100ms").
     */
    private final String raw;

    /**
     * The parsed time duration value in nanoseconds.
     */
    private final long nanos;

    /**
     * Allocates a {@code TimeDuration} object representing the time duration
     * specified by
     * the {@code value} argument. The input string is parsed to compute the
     * equivalent
     * duration in nanoseconds.
     *
     * @param value the string representation of the time duration (e.g., "100ms",
     *              "5s").
     */
    public TimeDuration(String value) {
        this.raw = value;
        this.nanos = parseNanos(value);
    }

    /**
     * Parses a time duration string and returns the equivalent duration in
     * nanoseconds.
     *
     * @param value the string to parse (e.g., "100ms", "5s").
     * @return the duration in nanoseconds as a long value.
     */
    private long parseNanos(String value) {
        String num = value.replaceAll("[^0-9.]", "");
        double val = Double.parseDouble(num);
        if (value.endsWith("ns")) {
            return (long) val;
        }
        if (value.endsWith("ms")) {
            return (long) (val * 1_000_000);
        }
        if (value.endsWith("s")) {
            return (long) (val * 1_000_000_000);
        }
        if (value.endsWith("m")) {
            return (long) (val * 60 * 1_000_000_000);
        }
        if (value.endsWith("h")) {
            return (long) (val * 3600 * 1_000_000_000);
        }
        return (long) val; // Assume ns if no unit
    }

    /**
     * Returns the parsed time duration value of this {@code TimeDuration} object as
     * a long.
     *
     * @return the duration in nanoseconds.
     */
    public long getNanos() {
        return nanos;
    }

    /**
     * Returns the original string value of this {@code TimeDuration} object.
     *
     * @return the original string representation of the time duration.
     */
    @Override
    public Object value() {
        return raw;
    }

    /**
     * Returns the type of this {@code TimeDuration} object as a {@code TokenType}
     * enum.
     *
     * @return the enumerated {@code TokenType} of this object.
     */
    @Override
    public TokenType type() {
        return TokenType.TIME_DURATION;
    }

    /**
     * Returns the members of this {@code TimeDuration} object as a
     * {@code JsonElement}.
     *
     * @return JSON representation of this {@code TimeDuration} object as
     *         {@code JsonElement}.
     */
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type().name()); // e.g., "TIME_DURATION"
        json.addProperty("raw", raw); // Original string value
        json.addProperty("nanos", nanos); // Parsed nanoseconds
        return json;
    }
}
