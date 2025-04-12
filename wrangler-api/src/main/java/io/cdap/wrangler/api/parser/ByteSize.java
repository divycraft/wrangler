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
 * The ByteSize class represents a byte size value parsed from a string.
 * This class encapsulates a byte size specified in formats such as "1KB",
 * "500MB", or "2GB"
 * and converts it into a numerical value in bytes. It implements the
 * {@code Token} interface
 * to provide type information and JSON serialization for use in the wrangler
 * parser.
 *
 * <p>
 * The class stores both the original string input and the parsed byte value,
 * allowing
 * access to either representation as needed. It supports common byte size units
 * including
 * KB, MB, GB, and TB, with a default assumption of bytes if no unit is
 * specified.
 * </p>
 *
 * @see BoolList
 * @see Numeric
 * @see Text
 * @since 2019-04-12
 */
public class ByteSize implements Token {
    /**
     * The original string representation of the byte size (e.g., "1MB").
     */
    private final String raw;

    /**
     * The parsed byte size value in bytes.
     */
    private final long bytes;

    /**
     * Allocates a {@code ByteSize} object representing the byte size specified by
     * the
     * {@code value} argument. The input string is parsed to compute the equivalent
     * size in bytes.
     *
     * @param value the string representation of the byte size (e.g., "1MB",
     *              "500KB").
     */
    public ByteSize(String value) {
        this.raw = value;
        this.bytes = parseBytes(value);
    }

    /**
     * Parses a byte size string and returns the equivalent size in bytes.
     *
     * @param value the string to parse (e.g., "1MB", "500KB").
     * @return the size in bytes as a long value.
     */
    private long parseBytes(String value) {
        String num = value.replaceAll("[^0-9.]", "");
        double val = Double.parseDouble(num);
        if (value.endsWith("KB")) {
            return (long) (val * 1024);
        }
        if (value.endsWith("MB")) {
            return (long) (val * 1024 * 1024);
        }
        if (value.endsWith("GB")) {
            return (long) (val * 1024 * 1024 * 1024);
        }
        if (value.endsWith("TB")) {
            return (long) (val * 1024 * 1024 * 1024 * 1024);
        }
        return (long) val; // Assume bytes if no unit
    }

    /**
     * Returns the parsed byte size value of this {@code ByteSize} object as a long.
     *
     * @return the size in bytes.
     */
    public long getBytes() {
        return bytes;
    }

    /**
     * Returns the original string value of this {@code ByteSize} object.
     *
     * @return the original string representation of the byte size.
     */
    @Override
    public Object value() {
        return raw;
    }

    /**
     * Returns the type of this {@code ByteSize} object as a {@code TokenType} enum.
     *
     * @return the enumerated {@code TokenType} of this object.
     */
    @Override
    public TokenType type() {
        return TokenType.BYTE_SIZE;
    }

    /**
     * Returns the members of this {@code ByteSize} object as a {@code JsonElement}.
     *
     * @return JSON representation of this {@code ByteSize} object as
     *         {@code JsonElement}.
     */
    @Override
    public JsonElement toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("type", type().name()); // e.g., "BYTE_SIZE"
        json.addProperty("raw", raw); // Original string value
        json.addProperty("bytes", bytes); // Parsed bytes
        return json;
    }
}
