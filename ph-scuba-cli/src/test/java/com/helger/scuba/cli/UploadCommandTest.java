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
import java.util.Locale;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.scuba.cli.CliRunner.CliResult;

/**
 * Test class for class {@link UploadCommand}.
 *
 * @author Philip Helger
 */
public final class UploadCommandTest
{
  private static final String VALID_XSD = "src/test/resources/external/valid.xsd";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  @Test
  public void testUploadBaseDir () throws Exception
  {
    final String sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();

    CliResult aResult = CliRunner.run ("upload", "--basedir", sBaseDir, "com.test:mini:1.0", VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().contains ("Uploaded"));
    assertTrue (new File (sBaseDir, "com/test/mini/1/mini-1.xsd").isFile ());
    assertTrue (new File (sBaseDir, "com/test/mini/1/mini-1.xsd.sha256").isFile ());

    // Already exists
    aResult = CliRunner.run ("upload", "--basedir", sBaseDir, "com.test:mini:1.0", VALID_XSD);
    assertEquals (ScubaCli.EXIT_CONFLICT, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("already exists"));

    // Overwrite explicitly
    aResult = CliRunner.run ("upload", "--basedir", sBaseDir, "--overwrite", "com.test:mini:1.0", VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());

    assertEquals (ScubaCli.EXIT_OK,
                  CliRunner.run ("exists", "--basedir", sBaseDir, "com.test:mini:1.0", "xsd").exitCode ());
    assertEquals (ScubaCli.EXIT_FAILURE,
                  CliRunner.run ("exists", "--basedir", sBaseDir, "com.test:mini:2.0", ".xsd").exitCode ());
  }

  @Test
  public void testUploadExpectedSha256 () throws Exception
  {
    final String sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();
    final String sSha256 = CliHelper.getSha256Hex (new File (VALID_XSD));

    CliResult aResult = CliRunner.run ("upload",
                                       "--basedir",
                                       sBaseDir,
                                       "--expected-sha256",
                                       "00" + sSha256.substring (2),
                                       "com.test:mini:1.0",
                                       VALID_XSD);
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("was expected"));
    assertEquals (ScubaCli.EXIT_FAILURE,
                  CliRunner.run ("exists", "--basedir", sBaseDir, "com.test:mini:1.0", "xsd").exitCode ());

    aResult = CliRunner.run ("upload",
                             "--basedir",
                             sBaseDir,
                             "--expected-sha256",
                             sSha256.toUpperCase (Locale.ROOT),
                             "com.test:mini:1.0",
                             VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
  }

  @Test
  public void testUploadInvalid () throws Exception
  {
    final String sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();

    CliResult aResult = CliRunner.run ("upload",
                                       "--basedir",
                                       sBaseDir,
                                       "com.test:mini:1.0",
                                       "src/test/resources/external/invalid.xsd");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("nothing was uploaded"));

    // Unknown extension
    aResult = CliRunner.run ("upload",
                             "--basedir",
                             sBaseDir,
                             "com.test:text:1.0",
                             "src/test/resources/external/unknown.txt");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("--allow-unknown-extension"));

    aResult = CliRunner.run ("upload",
                             "--basedir",
                             sBaseDir,
                             "--allow-unknown-extension",
                             "com.test:text:1.0",
                             "src/test/resources/external/unknown.txt");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
  }

  @Test
  public void testUsageErrors () throws Exception
  {
    final String sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();

    // No repository
    assertEquals (ScubaCli.EXIT_USAGE, CliRunner.run ("upload", "com.test:mini:1.0", VALID_XSD).exitCode ());
    // Repository directory does not exist
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--basedir", sBaseDir + "/missing", "com.test:mini:1.0", VALID_XSD)
                           .exitCode ());
    assertFalse (new File (sBaseDir, "missing").exists ());
    // Pseudo version
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--basedir", sBaseDir, "com.test:mini:latest", VALID_XSD).exitCode ());
    // Invalid coordinate
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--basedir", sBaseDir, "com.test", VALID_XSD).exitCode ());
    // File does not exist
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--basedir", sBaseDir, "com.test:mini:1.0", "does-not-exist.xsd")
                           .exitCode ());
    // Invalid top-level ToC type
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--basedir", sBaseDir, "--toptoc", "foo", "com.test:mini:1.0", VALID_XSD)
                           .exitCode ());
    // Mixed repository options
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--basedir", sBaseDir, "--repo", "x", "com.test:mini:1.0", VALID_XSD)
                           .exitCode ());
  }

  @Test
  public void testUploadConfig () throws Exception
  {
    final File aDir = m_aTempFolder.newFolder ("config");
    final File aConfig = new File (aDir, "repoconfig.xml");
    Files.writeString (aConfig.toPath (),
                       "<repositories xmlns=\"urn:fdc:com:helger:diver:repoconfig:v1.0\">\n" +
                                          "  <repository id=\"rw\" type=\"localfs\" writable=\"true\" toptoc=\"diver-xml\">\n" +
                                          "    <property name=\"basedir\">rw-repo</property>\n" +
                                          "  </repository>\n" +
                                          "  <repository id=\"ro\" type=\"localfs\">\n" +
                                          "    <property name=\"basedir\">ro-repo</property>\n" +
                                          "  </repository>\n" +
                                          "</repositories>\n",
                       StandardCharsets.UTF_8);
    final String sConfig = aConfig.getAbsolutePath ();

    CliResult aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "rw", "com.test:mini:1.0", VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (new File (aDir, "rw-repo/com/test/mini/1/mini-1.xsd").isFile ());
    assertTrue (new File (aDir, "rw-repo/toptoc-diver.xml").isFile ());

    // Not writable
    aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "ro", "com.test:mini:1.0", VALID_XSD);
    assertEquals (ScubaCli.EXIT_USAGE, aResult.exitCode ());
    assertTrue (aResult.err ().contains ("not writable"));

    // Unknown repository
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--config", sConfig, "--repo", "foo", "com.test:mini:1.0", VALID_XSD)
                           .exitCode ());
    // Missing --repo
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("upload", "--config", sConfig, "com.test:mini:1.0", VALID_XSD).exitCode ());
  }

  @Test
  public void testUploadDryRun () throws Exception
  {
    final String sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();

    CliResult aResult = CliRunner.run ("upload", "--basedir", sBaseDir, "--dry-run", "com.test:mini:1.0", VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().contains ("Dry run"));
    assertFalse (new File (sBaseDir, "com/test/mini/1/mini-1.xsd").exists ());

    // The checks are performed
    aResult = CliRunner.run ("upload",
                             "--basedir",
                             sBaseDir,
                             "--dry-run",
                             "com.test:mini:1.0",
                             "src/test/resources/external/invalid.xsd");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());

    // Including the conflict check
    assertEquals (ScubaCli.EXIT_OK,
                  CliRunner.run ("upload", "--basedir", sBaseDir, "com.test:mini:1.0", VALID_XSD).exitCode ());
    aResult = CliRunner.run ("upload", "--basedir", sBaseDir, "--dry-run", "com.test:mini:1.0", VALID_XSD);
    assertEquals (ScubaCli.EXIT_CONFLICT, aResult.exitCode ());
  }

  @Test
  public void testUploadNoFileExtension () throws Exception
  {
    final String sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();
    final File aFile = m_aTempFolder.newFile ("noext");
    Files.writeString (aFile.toPath (), "data", StandardCharsets.UTF_8);

    final CliResult aResult = CliRunner.run ("upload",
                                             "--basedir",
                                             sBaseDir,
                                             "--allow-unknown-extension",
                                             "com.test:noext:1",
                                             aFile.getAbsolutePath ());
    assertEquals (ScubaCli.EXIT_USAGE, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("has no file extension"));
    assertFalse (new File (sBaseDir, "com/test/noext/1").exists ());
  }
}
