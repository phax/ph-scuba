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

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.scuba.cli.CliRunner.CliResult;

/**
 * Test class for class {@link ScubaCli}.
 *
 * @author Philip Helger
 */
public final class ScubaCliTest
{
  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

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
  public void testHeadline () throws Exception
  {
    final String sBaseDir = m_aTempFolder.getRoot ().getAbsolutePath ();

    CliResult aResult = CliRunner.run ("exists", "--basedir", sBaseDir, "com.test:mini:1.0", "xsd");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.out (), aResult.out ().startsWith (ScubaCli.HEADLINE));

    // Not in quiet mode
    aResult = CliRunner.run ("exists", "-q", "--basedir", sBaseDir, "com.test:mini:1.0", "xsd");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertFalse (aResult.out (), aResult.out ().contains (ScubaCli.HEADLINE));
    assertFalse (aResult.err (), aResult.err ().contains (ScubaCli.HEADLINE));
  }

  @Test
  public void testIsVerboseRequested ()
  {
    assertTrue (ScubaCli.isVerboseRequested (new String [] { "upload", "-v", "a", "b" }));
    assertTrue (ScubaCli.isVerboseRequested (new String [] { "upload", "--verbose" }));
    assertTrue (ScubaCli.isVerboseRequested (new String [] { "upload", "-qv" }));
    assertFalse (ScubaCli.isVerboseRequested (new String [] { "upload", "-V" }));
    assertFalse (ScubaCli.isVerboseRequested (new String [] { "upload", "--version" }));
    assertFalse (ScubaCli.isVerboseRequested (new String [] { "ves", "deprecate", "--reason", "very old" }));
    // After the end of options
    assertFalse (ScubaCli.isVerboseRequested (new String [] { "validate", "--", "-v" }));
  }

  @Test
  public void testStackTraceOnlyInVerboseMode () throws Exception
  {
    // An unreadable repository configuration is a technical error
    final File aConfig = m_aTempFolder.newFile ("repoconfig.xml");
    Files.writeString (aConfig.toPath (), "not xml", StandardCharsets.UTF_8);
    final String sConfig = aConfig.getAbsolutePath ();

    CliResult aResult = CliRunner.run ("exists", "--config", sConfig, "--repo", "r", "com.test:mini:1.0", "xsd");
    assertEquals (ScubaCli.EXIT_ERROR, aResult.exitCode ());
    assertFalse (aResult.err (), aResult.err ().contains ("\tat "));

    aResult = CliRunner.run ("exists", "-v", "--config", sConfig, "--repo", "r", "com.test:mini:1.0", "xsd");
    assertEquals (ScubaCli.EXIT_ERROR, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("\tat "));
  }

  @Test
  public void testVesNoCommand ()
  {
    assertEquals (ScubaCli.EXIT_USAGE, CliRunner.run ("ves").exitCode ());
  }
}
