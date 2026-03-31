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
package com.helger.scuba.phive.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.state.EChange;
import com.helger.datetime.helper.PDTFactory;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.IRepoStorageReadItem;
import com.helger.diver.repo.RepoStorageContentByteArray;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.impl.RepoStorageInMemory;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.ves.model.v1.VESStatus1Marshaller;
import com.helger.phive.ves.v10.VesResourceType;
import com.helger.phive.ves.v10.VesStatusHistoryItemType;
import com.helger.phive.ves.v10.VesStatusHistoryType;
import com.helger.phive.ves.v10.VesStatusType;
import com.helger.phive.ves.v10.VesType;
import com.helger.phive.ves.v10.VesXsdType;
import com.helger.scuba.upload.ScubaUploader;
import com.helger.scuba.upload.ScubaUploaderSettings;

/**
 * Test class for {@link PhiveUploader}.
 *
 * @author Philip Helger
 */
public final class PhiveUploaderTest
{
  @NonNull
  private static IRepoStorageWithToc _createRepo ()
  {
    return RepoStorageInMemory.createDefault ("test", ERepoWritable.WITH_WRITE, ERepoDeletable.WITH_DELETE);
  }

  @NonNull
  private static PhiveUploader _createPhiveUploader (@NonNull final IRepoStorageWithToc aRepo)
  {
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ().setAllowOverwriteExisting (true);
    return new PhiveUploader (new ScubaUploader (aRepo, aSettings));
  }

  @NonNull
  private static DVRCoordinate _createCoord ()
  {
    return new DVRCoordinate ("com.test", "myves", DVRVersion.parseOrNull ("1.0"));
  }

  /**
   * Read back a VesStatusType from the repository for assertion purposes.
   */
  @NonNull
  private static VesStatusType _readStatus (@NonNull final IRepoStorageWithToc aRepo,
                                            @NonNull final DVRCoordinate aCoord)
  {
    final IRepoStorageReadItem aReadItem = aRepo.read (RepoStorageKeyOfArtefact.of (aCoord, VESLoader.FILE_EXT_STATUS));
    assertNotNull ("Status must exist in repo", aReadItem);
    final VesStatusType ret = new VESStatus1Marshaller ().read (aReadItem.getContent ().getBufferedInputStream ());
    assertNotNull ("Status must be readable", ret);
    return ret;
  }

  @Test
  public void testGetUploader ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final ScubaUploader aUploader = new ScubaUploader (aRepo);
    final PhiveUploader aPhiveUploader = new PhiveUploader (aUploader);
    assertEquals (aUploader, aPhiveUploader.getUploader ());
  }

  // -- addVES tests --

  @Test
  public void testAddVESWithXsdValidation () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // Step 1: Upload a valid XSD to the repo at the coordinate the VES will reference
    final DVRCoordinate aXsdCoord = new DVRCoordinate ("com.test", "mini", DVRVersion.parseOrNull ("1.0"));
    final String sXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                        "           xmlns=\"urn:test\"\n" +
                        "           targetNamespace=\"urn:test\"\n" +
                        "           elementFormDefault=\"qualified\">\n" +
                        "  <xs:element name=\"root\" type=\"xs:string\" />\n" +
                        "</xs:schema>\n";
    aRepo.write (RepoStorageKeyOfArtefact.of (aXsdCoord, ".xsd"),
                 RepoStorageContentByteArray.of (sXsd.getBytes (StandardCharsets.UTF_8)));

    // Step 2: Build a VES that uses XSD validation referencing the uploaded XSD
    final DVRCoordinate aVesCoord = new DVRCoordinate ("com.test", "myves-xsd", DVRVersion.parseOrNull ("1.0"));

    final VesResourceType aResource = new VesResourceType ();
    aResource.setGroupId (aXsdCoord.getGroupID ());
    aResource.setArtifactId (aXsdCoord.getArtifactID ());
    aResource.setVersion (aXsdCoord.getVersionString ());
    aResource.setType ("xsd");

    final VesXsdType aXsdDef = new VesXsdType ();
    aXsdDef.setResource (aResource);

    final VesType aVes = new VesType ();
    aVes.setGroupId (aVesCoord.getGroupID ());
    aVes.setArtifactId (aVesCoord.getArtifactID ());
    aVes.setVersion (aVesCoord.getVersionString ());
    aVes.setName ("Test XSD VES");
    aVes.setXsd (aXsdDef);

    // Step 3: Upload via PhiveUploader - goes through full validation pipeline
    aPhiveUploader.addVES (aVes);

    // Verify it was stored
    assertTrue (aRepo.exists (RepoStorageKeyOfArtefact.of (aVesCoord, VESLoader.FILE_EXT_VES)));

    // Delete the artefact again
    new ScubaUploader (aRepo).deleteResource (aVesCoord, VESLoader.FILE_EXT_VES);

    // Verify it was deleted again
    assertFalse (aRepo.exists (RepoStorageKeyOfArtefact.of (aVesCoord, VESLoader.FILE_EXT_VES)));
  }

  @Test
  public void testAddVESIncompleteEmpty () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // Completely empty VES - no groupId, artifactId, version, name, or content
    final VesType aVes = new VesType ();
    assertEquals (EChange.UNCHANGED, aPhiveUploader.addVES (aVes));
  }

  @Test
  public void testAddVESIncompleteNoContent () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // VES with coordinates and name but no validation content (xsd/schematron)
    final VesType aVes = new VesType ();
    aVes.setGroupId ("com.test");
    aVes.setArtifactId ("incomplete");
    aVes.setVersion ("1.0");
    aVes.setName ("Incomplete VES");
    assertEquals (EChange.UNCHANGED, aPhiveUploader.addVES (aVes));
  }

  @Test
  public void testAddVESIncompleteNoName () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // VES with coordinates but no name
    final VesType aVes = new VesType ();
    aVes.setGroupId ("com.test");
    aVes.setArtifactId ("noname");
    aVes.setVersion ("1.0");
    assertEquals (EChange.UNCHANGED, aPhiveUploader.addVES (aVes));
  }

  // -- addVESStatus tests --

  @Test
  public void testAddVESStatus () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final VesStatusType aStatus = new VesStatusType ();
    aStatus.setGroupId (aCoord.getGroupID ());
    aStatus.setArtifactId (aCoord.getArtifactID ());
    aStatus.setVersion (aCoord.getVersionString ());
    aStatus.setStatusLastModified (PDTFactory.getCurrentXMLOffsetDateTimeUTC ());
    final VesStatusHistoryType aHistory = new VesStatusHistoryType ();
    final VesStatusHistoryItemType aHistoryItem = new VesStatusHistoryItemType ();
    aHistoryItem.setChangeDateTime (PDTFactory.getCurrentXMLOffsetDateTimeUTC ());
    aHistoryItem.setAuthor ("test");
    aHistoryItem.setValue ("Initial creation");
    aHistory.addHistoryItem (aHistoryItem);
    aStatus.setHistory (aHistory);

    aPhiveUploader.addVESStatus (aStatus);

    // Verify it was stored
    assertTrue (aRepo.exists (RepoStorageKeyOfArtefact.of (aCoord, VESLoader.FILE_EXT_STATUS)));
  }

  @Test
  public void testAddVESStatusIncompleteEmpty () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // Completely empty VESStatus - no groupId, artifactId, version, or history
    final VesStatusType aStatus = new VesStatusType ();
    assertEquals (EChange.UNCHANGED, aPhiveUploader.addVESStatus (aStatus));
  }

  @Test
  public void testAddVESStatusIncompleteNoHistory () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // VESStatus with coordinates but no history (required element)
    final VesStatusType aStatus = new VesStatusType ();
    aStatus.setGroupId ("com.test");
    aStatus.setArtifactId ("nohistory");
    aStatus.setVersion ("1.0");
    aStatus.setStatusLastModified (PDTFactory.getCurrentXMLOffsetDateTimeUTC ());
    assertEquals (EChange.UNCHANGED, aPhiveUploader.addVESStatus (aStatus));
  }

  @Test
  public void testAddVESStatusIncompleteEmptyHistory () throws Exception
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);

    // VESStatus with coordinates and empty history (history has no items)
    final VesStatusType aStatus = new VesStatusType ();
    aStatus.setGroupId ("com.test");
    aStatus.setArtifactId ("emptyhistory");
    aStatus.setVersion ("1.0");
    aStatus.setStatusLastModified (PDTFactory.getCurrentXMLOffsetDateTimeUTC ());
    aStatus.setHistory (new VesStatusHistoryType ());
    assertEquals (EChange.UNCHANGED, aPhiveUploader.addVESStatus (aStatus));
  }

  // -- setVESDeprecated tests --

  @Test
  public void testSetVESDeprecatedCreatesNewStatus ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final EChange eChange = aPhiveUploader.setVESDeprecated (aCoord, "No longer maintained");
    assertEquals (EChange.CHANGED, eChange);

    // Read back and verify
    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertTrue (aStatus.isDeprecated ().booleanValue ());
    assertEquals ("No longer maintained", aStatus.getDeprecationReason ());
    assertNull (aStatus.getReplacement ());
    assertNotNull (aStatus.getStatusLastModified ());
    assertEquals (1, aStatus.getHistory ().getHistoryItem ().size ());
    assertEquals (PhiveUploader.AUTHOR_SYSTEM, aStatus.getHistory ().getHistoryItem ().get (0).getAuthor ());
  }

  @Test
  public void testSetVESDeprecatedWithReplacement ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();
    final DVRCoordinate aReplacement = new DVRCoordinate ("com.test", "myves", DVRVersion.parseOrNull ("2.0"));

    final EChange eChange = aPhiveUploader.setVESDeprecated (aCoord, "Superseded", aReplacement);
    assertEquals (EChange.CHANGED, eChange);

    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertTrue (aStatus.isDeprecated ().booleanValue ());
    assertEquals ("Superseded", aStatus.getDeprecationReason ());
    assertNotNull (aStatus.getReplacement ());
    assertEquals ("com.test", aStatus.getReplacement ().getGroupId ());
    assertEquals ("myves", aStatus.getReplacement ().getArtifactId ());
    assertEquals (aReplacement.getVersionString (), aStatus.getReplacement ().getVersion ());
  }

  @Test
  public void testSetVESDeprecatedAlreadyDeprecated ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    // First deprecation
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESDeprecated (aCoord, "old"));

    // Second deprecation should return UNCHANGED
    assertEquals (EChange.UNCHANGED, aPhiveUploader.setVESDeprecated (aCoord, "newer reason"));

    // Original deprecation reason preserved
    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertEquals ("old", aStatus.getDeprecationReason ());
  }

  @Test
  public void testSetVESDeprecatedUpdatesExistingStatus ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    // Pre-populate with a non-deprecated status that has a history item
    final VesStatusType aInitial = new VesStatusType ();
    aInitial.setGroupId (aCoord.getGroupID ());
    aInitial.setArtifactId (aCoord.getArtifactID ());
    aInitial.setVersion (aCoord.getVersionString ());
    aInitial.setHistory (new VesStatusHistoryType ());

    // First set validity dates so the status exists in repo
    aPhiveUploader.setVESValidityDate (aCoord, OffsetDateTime.of (2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC), null);

    // Now deprecate - should update existing status
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESDeprecated (aCoord, "End of life"));

    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertTrue (aStatus.isDeprecated ().booleanValue ());
    // Should have 2 history items: one from setVESValidityDate, one from setVESDeprecated
    assertEquals (2, aStatus.getHistory ().getHistoryItem ().size ());
  }

  // -- setVESValidityDate tests --

  @Test
  public void testSetVESValidityDateCreatesNewStatus ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final OffsetDateTime aFrom = OffsetDateTime.of (2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime aTo = OffsetDateTime.of (2026, 12, 31, 23, 59, 59, 0, ZoneOffset.UTC);
    final EChange eChange = aPhiveUploader.setVESValidityDate (aCoord, aFrom, aTo);
    assertEquals (EChange.CHANGED, eChange);

    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertNotNull (aStatus.getValidFrom ());
    assertNotNull (aStatus.getValidTo ());
    assertNotNull (aStatus.getStatusLastModified ());
    assertEquals (1, aStatus.getHistory ().getHistoryItem ().size ());
    assertEquals (PhiveUploader.AUTHOR_SYSTEM, aStatus.getHistory ().getHistoryItem ().get (0).getAuthor ());
    assertEquals ("Updated validity dates", aStatus.getHistory ().getHistoryItem ().get (0).getValue ());
  }

  @Test
  public void testSetVESValidityDateValidFromOnly ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final OffsetDateTime aFrom = OffsetDateTime.of (2026, 6, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESValidityDate (aCoord, aFrom, null));

    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertNotNull (aStatus.getValidFrom ());
    assertNull (aStatus.getValidTo ());
  }

  @Test
  public void testSetVESValidityDateValidToOnly ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final OffsetDateTime aTo = OffsetDateTime.of (2026, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESValidityDate (aCoord, null, aTo));

    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    assertNull (aStatus.getValidFrom ());
    assertNotNull (aStatus.getValidTo ());
  }

  @Test
  public void testSetVESValidityDateUnchangedWhenSameDates ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final OffsetDateTime aFrom = OffsetDateTime.of (2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime aTo = OffsetDateTime.of (2026, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);

    // Set initial dates
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESValidityDate (aCoord, aFrom, aTo));

    // Set same dates again
    assertEquals (EChange.UNCHANGED, aPhiveUploader.setVESValidityDate (aCoord, aFrom, aTo));
  }

  @Test
  public void testSetVESValidityDateUpdatesExistingDates ()
  {
    final IRepoStorageWithToc aRepo = _createRepo ();
    final PhiveUploader aPhiveUploader = _createPhiveUploader (aRepo);
    final DVRCoordinate aCoord = _createCoord ();

    final OffsetDateTime aFrom1 = OffsetDateTime.of (2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime aTo1 = OffsetDateTime.of (2026, 6, 30, 0, 0, 0, 0, ZoneOffset.UTC);
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESValidityDate (aCoord, aFrom1, aTo1));

    // Update with new dates
    final OffsetDateTime aFrom2 = OffsetDateTime.of (2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC);
    final OffsetDateTime aTo2 = OffsetDateTime.of (2026, 12, 31, 0, 0, 0, 0, ZoneOffset.UTC);
    assertEquals (EChange.CHANGED, aPhiveUploader.setVESValidityDate (aCoord, aFrom2, aTo2));

    final VesStatusType aStatus = _readStatus (aRepo, aCoord);
    // Should have 2 history items
    assertEquals (2, aStatus.getHistory ().getHistoryItem ().size ());
  }
}
