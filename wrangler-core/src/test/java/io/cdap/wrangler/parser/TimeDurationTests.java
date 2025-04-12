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

import io.cdap.wrangler.api.parser.TimeDuration;
import org.junit.Assert;
import org.junit.Test;

public class TimeDurationTests {

  @Test
  public void testTimeDurationParsing() {
    TimeDuration td1 = new TimeDuration("10ns");
    Assert.assertEquals(10, td1.getNanos());

    TimeDuration td2 = new TimeDuration("1.5ms");
    Assert.assertEquals(1_500_000, td2.getNanos());

    TimeDuration td3 = new TimeDuration("2s");
    Assert.assertEquals(2_000_000_000, td3.getNanos());

    TimeDuration td4 = new TimeDuration("3m");
    Assert.assertEquals(3L * 60 * 1_000_000_000, td4.getNanos());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testInvalidTimeDuration() {
    new TimeDuration("invalid");
  }
}
