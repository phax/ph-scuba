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
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.helger.scuba.cli.CliRunner.CliResult;

/**
 * Test class for class {@link ValidateCommand}.
 *
 * @author Philip Helger
 */
public final class ValidateCommandTest
{
  @Test
  public void testValidate ()
  {
    CliResult aResult = CliRunner.run ("validate", "src/test/resources/external/valid.xsd");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().contains ("is valid"));

    aResult = CliRunner.run ("validate",
                             "src/test/resources/external/valid.xsd",
                             "src/test/resources/external/invalid.xsd");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("is not valid"));

    aResult = CliRunner.run ("validate", "src/test/resources/external/unknown.txt");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("no check"));

    assertEquals (ScubaCli.EXIT_USAGE, CliRunner.run ("validate", "does-not-exist.xsd").exitCode ());
  }
}
