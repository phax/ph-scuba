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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;

import org.jspecify.annotations.NonNull;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.datetime.helper.PDTFactory;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.IRepoStorageReadItem;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.impl.RepoStorageLocalFileSystem;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.ves.model.v1.VES1Marshaller;
import com.helger.phive.ves.model.v1.VESStatus1Marshaller;
import com.helger.phive.ves.v10.VesResourceType;
import com.helger.phive.ves.v10.VesStatusHistoryItemType;
import com.helger.phive.ves.v10.VesStatusHistoryType;
import com.helger.phive.ves.v10.VesStatusType;
import com.helger.phive.ves.v10.VesType;
import com.helger.phive.ves.v10.VesXsdType;
import com.helger.scuba.cli.CliRunner.CliResult;

/**
 * Test class for the commands of {@link VesCommand}.
 *
 * @author Philip Helger
 */
public final class VesCommandTest
{
  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  private String m_sBaseDir;

  @Before
  public void before () throws Exception
  {
    m_sBaseDir = m_aTempFolder.newFolder ("repo").getAbsolutePath ();
    // The XSD referenced by all VES
    final CliResult aResult = CliRunner.run ("upload",
                                             "--basedir",
                                             m_sBaseDir,
                                             "com.test:mini:1.0",
                                             "src/test/resources/external/valid.xsd");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
  }

  @NonNull
  private File _createVesFile (@NonNull final String sVersion) throws Exception
  {
    final VesResourceType aResource = new VesResourceType ();
    aResource.setGroupId ("com.test");
    aResource.setArtifactId ("mini");
    aResource.setVersion ("1.0");
    aResource.setType ("xsd");

    final VesXsdType aXsdDef = new VesXsdType ();
    aXsdDef.setResource (aResource);

    final VesType aVes = new VesType ();
    aVes.setGroupId ("com.test");
    aVes.setArtifactId ("myves");
    aVes.setVersion (sVersion);
    aVes.setName ("Test VES " + sVersion);
    aVes.setXsd (aXsdDef);

    final File ret = m_aTempFolder.newFile ("myves-" + sVersion + ".ves");
    assertTrue (new VES1Marshaller ().write (aVes, ret).isSuccess ());
    return ret;
  }

  @NonNull
  private String _addVes (@NonNull final String sVersion) throws Exception
  {
    final CliResult aResult = CliRunner.run ("ves",
                                             "add",
                                             "--basedir",
                                             m_sBaseDir,
                                             _createVesFile (sVersion).getAbsolutePath ());
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    return DVRCoordinate.parseOrNull ("com.test:myves:" + sVersion).getAsSingleID ();
  }

  @NonNull
  private VesStatusType _readStatus (@NonNull final String sVESID)
  {
    final RepoStorageLocalFileSystem aRepo = new RepoStorageLocalFileSystem (new File (m_sBaseDir),
                                                                             "test",
                                                                             ERepoWritable.WITHOUT_WRITE,
                                                                             ERepoDeletable.WITHOUT_DELETE);
    final IRepoStorageReadItem aItem = aRepo.read (RepoStorageKeyOfArtefact.of (DVRCoordinate.parseOrNull (sVESID),
                                                                               VESLoader.FILE_EXT_STATUS));
    assertNotNull (aItem);
    final VesStatusType ret = new VESStatus1Marshaller ().read (aItem.getContent ().getBufferedInputStream ());
    assertNotNull (ret);
    return ret;
  }

  @Test
  public void testAdd () throws Exception
  {
    final File aVesFile = _createVesFile ("1.0");
    CliResult aResult = CliRunner.run ("ves", "add", "--basedir", m_sBaseDir, aVesFile.getAbsolutePath ());
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());

    // Already exists
    aResult = CliRunner.run ("ves", "add", "--basedir", m_sBaseDir, aVesFile.getAbsolutePath ());
    assertEquals (ScubaCli.EXIT_CONFLICT, aResult.exitCode ());

    // Not a VES
    aResult = CliRunner.run ("ves", "add", "--basedir", m_sBaseDir, "src/test/resources/external/valid.xsd");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());
  }

  @Test
  public void testAddStatus () throws Exception
  {
    final String sVESID = _addVes ("1.0");

    final VesStatusType aStatus = new VesStatusType ();
    aStatus.setGroupId ("com.test");
    aStatus.setArtifactId ("myves");
    aStatus.setVersion ("1.0");
    aStatus.setStatusLastModified (PDTFactory.getCurrentXMLOffsetDateTimeUTC ());
    final VesStatusHistoryItemType aHistoryItem = new VesStatusHistoryItemType ();
    aHistoryItem.setChangeDateTime (PDTFactory.getCurrentXMLOffsetDateTimeUTC ());
    aHistoryItem.setAuthor ("test");
    aHistoryItem.setValue ("Initial creation");
    aStatus.setHistory (new VesStatusHistoryType ());
    aStatus.getHistory ().addHistoryItem (aHistoryItem);
    final File aStatusFile = m_aTempFolder.newFile ("myves.status");
    assertTrue (new VESStatus1Marshaller ().write (aStatus, aStatusFile).isSuccess ());

    CliResult aResult = CliRunner.run ("ves", "add-status", "--basedir", m_sBaseDir, aStatusFile.getAbsolutePath ());
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertEquals ("test", _readStatus (sVESID).getHistory ().getHistoryItem ().get (0).getAuthor ());

    // Already exists
    aResult = CliRunner.run ("ves", "add-status", "--basedir", m_sBaseDir, aStatusFile.getAbsolutePath ());
    assertEquals (ScubaCli.EXIT_CONFLICT, aResult.exitCode ());
  }

  @Test
  public void testDeprecate () throws Exception
  {
    final String sVESID = _addVes ("1.0");

    // Author is required
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("ves", "deprecate", "--basedir", m_sBaseDir, sVESID).exitCode ());

    // Replacement does not exist
    CliResult aResult = CliRunner.run ("ves",
                                       "deprecate",
                                       "--basedir",
                                       m_sBaseDir,
                                       "--author",
                                       "Jane Doe",
                                       "--replacement",
                                       "com.test:myves:2.0",
                                       sVESID);
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());

    // VES does not exist
    aResult = CliRunner.run ("ves", "deprecate", "--basedir", m_sBaseDir, "--author", "Jane Doe", "com.test:other:1.0");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());

    final String sReplacement = _addVes ("2.0");
    aResult = CliRunner.run ("ves",
                             "deprecate",
                             "--basedir",
                             m_sBaseDir,
                             "--author",
                             "Jane Doe",
                             "--reason",
                             "Superseded",
                             "--replacement",
                             sReplacement,
                             sVESID);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    final VesStatusType aStatus = _readStatus (sVESID);
    assertTrue (aStatus.isDeprecated ().booleanValue ());
    assertEquals ("Superseded", aStatus.getDeprecationReason ());
    assertEquals ("Jane Doe", aStatus.getHistory ().getHistoryItem ().get (0).getAuthor ());

    // Already deprecated
    aResult = CliRunner.run ("ves", "deprecate", "--basedir", m_sBaseDir, "--author", "Jane Doe", sVESID);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (aResult.out ().contains ("already"));
  }

  @Test
  public void testValidity () throws Exception
  {
    final String sVESID = _addVes ("1.0");

    // At least one date
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("ves", "validity", "--basedir", m_sBaseDir, "--author", "Jane Doe", sVESID)
                           .exitCode ());
    // Wrong order
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("ves",
                                 "validity",
                                 "--basedir",
                                 m_sBaseDir,
                                 "--author",
                                 "Jane Doe",
                                 "--from",
                                 "2027-01-01T00:00:00Z",
                                 "--to",
                                 "2026-01-01T00:00:00Z",
                                 sVESID)
                           .exitCode ());

    final CliResult aResult = CliRunner.run ("ves",
                                             "validity",
                                             "--basedir",
                                             m_sBaseDir,
                                             "--author",
                                             "Jane Doe",
                                             "--from",
                                             "2026-01-01T00:00:00Z",
                                             sVESID);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertNotNull (_readStatus (sVESID).getValidFrom ());
  }

  @Test
  public void testLatest () throws Exception
  {
    // Nothing there yet
    CliResult aResult = CliRunner.run ("ves", "latest", "--basedir", m_sBaseDir, "com.test:myves");
    assertEquals (ScubaCli.EXIT_FAILURE, aResult.exitCode ());

    _addVes ("1.0");
    final String sVESID11 = _addVes ("1.1");
    final String sVESID20 = _addVes ("2.0-SNAPSHOT");

    aResult = CliRunner.run ("ves", "latest", "--basedir", m_sBaseDir, "com.test:myves");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertEquals (sVESID20, aResult.out ().trim ());

    aResult = CliRunner.run ("ves", "latest-release", "--basedir", m_sBaseDir, "com.test:myves");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertEquals (sVESID11, aResult.out ().trim ());

    // No status - all are active
    aResult = CliRunner.run ("ves", "latest-active", "--basedir", m_sBaseDir, "com.test:myves");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertEquals (sVESID20, aResult.out ().trim ());

    // The snapshot only becomes valid in the future
    aResult = CliRunner.run ("ves",
                             "validity",
                             "--basedir",
                             m_sBaseDir,
                             "--author",
                             "Jane Doe",
                             "--from",
                             "2099-01-01T00:00:00Z",
                             sVESID20);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());

    aResult = CliRunner.run ("ves", "latest-active", "--basedir", m_sBaseDir, "com.test:myves");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertEquals (sVESID11, aResult.out ().trim ());

    aResult = CliRunner.run ("ves",
                             "latest-active",
                             "--basedir",
                             m_sBaseDir,
                             "--at",
                             "2100-01-01T00:00:00Z",
                             "com.test:myves");
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertEquals (sVESID20, aResult.out ().trim ());

    // Invalid group and artifact
    assertEquals (ScubaCli.EXIT_USAGE,
                  CliRunner.run ("ves", "latest", "--basedir", m_sBaseDir, "com.test:myves:1.0").exitCode ());
  }
}
