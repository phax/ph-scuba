/*
 * Copyright (C) 2026 Philip Helger (www.helger.com)
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
package com.helger.scuba.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.scuba.cli.CliRunner.CliResult;

/**
 * Test class for class {@link ScubaCli}.
 *
 * @author Philip Helger
 */
public final class ScubaCliTest
{
  @Test
  public void testNoCommand ()
  {
    final CliResult aResult = CliRunner.run ();
    assertEquals (ScubaCli.EXIT_USAGE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("upload"));
  }

  @Test
  public void testVersion ()
  {
    final CliResult aResult = CliRunner.run ("--version");
    assertEquals (ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().startsWith ("ph-scuba "));
    assertFalse (aResult.out (), aResult.out ().contains ("undefined"));
  }

  @Test
  public void testVesNoCommand ()
  {
    assertEquals (ScubaCli.EXIT_USAGE, CliRunner.run ("ves").exitCode ());
  }
}
