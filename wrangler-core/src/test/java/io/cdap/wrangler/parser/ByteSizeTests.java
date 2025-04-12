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

package io.cdap.wrangler.parser;

import io.cdap.wrangler.api.parser.ByteSize;
import org.junit.Assert;
import org.junit.Test;

public class ByteSizeTests {

  @Test
  public void testByteSizeParsing() {
    ByteSize bs1 = new ByteSize("10B");
    Assert.assertEquals(10, bs1.getBytes());

    ByteSize bs2 = new ByteSize("1.5KB");
    Assert.assertEquals(1536, bs2.getBytes());

    ByteSize bs3 = new ByteSize("2MB");
    Assert.assertEquals(2 * 1024 * 1024, bs3.getBytes());

    ByteSize bs4 = new ByteSize("3GB");
    Assert.assertEquals(3L * 1024 * 1024 * 1024, bs4.getBytes());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidByteSize() {
    new ByteSize("invalid");
  }
}
