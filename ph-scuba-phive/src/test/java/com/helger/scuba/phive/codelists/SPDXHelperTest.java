/*
 * Copyright (C) 2024-2026 Philip Helger (www.helger.com)
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.scuba.phive.codelists;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

public final class SPDXHelperTest
{
  private static void _testOnlyOneInSet (@NonNull final String s, @NonNull final Set <String> aSet)
  {
    assertNotNull (aSet);
    assertEquals (1, aSet.size ());
    assertTrue (aSet.contains (s));
  }

  @Test
  public void testBasic ()
  {
    assertTrue (SPDXHelper.isSpdxIDValid ("Apache-2.0"));
    assertTrue (SPDXHelper.isSpdxIDValid ("apache-2.0"));
    assertTrue (SPDXHelper.isSpdxIDValid ("APACHE-2.0"));
    assertEquals ("Apache License 2.0", SPDXHelper.getSpdxNameOfID ("Apache-2.0"));
    assertEquals ("Apache License 2.0", SPDXHelper.getSpdxNameOfID ("apache-2.0"));
    assertEquals ("Apache License 2.0", SPDXHelper.getSpdxNameOfID ("APACHE-2.0"));

    assertTrue (SPDXHelper.isSpdxNameValid ("Apache License 2.0"));
    assertTrue (SPDXHelper.isSpdxNameValid ("apache license 2.0"));
    assertTrue (SPDXHelper.isSpdxNameValid ("APACHE LICENSE 2.0"));
    _testOnlyOneInSet ("Apache-2.0", SPDXHelper.getAllSpdxIDsOfName ("Apache License 2.0"));
    _testOnlyOneInSet ("Apache-2.0", SPDXHelper.getAllSpdxIDsOfName ("apache license 2.0"));
    _testOnlyOneInSet ("Apache-2.0", SPDXHelper.getAllSpdxIDsOfName ("APACHE LICENSE 2.0"));
  }
}
