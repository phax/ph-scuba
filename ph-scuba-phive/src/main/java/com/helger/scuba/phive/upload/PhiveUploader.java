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

import java.io.IOException;
import java.time.OffsetDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.equals.EqualsHelper;
import com.helger.base.state.EChange;
import com.helger.base.string.StringHelper;
import com.helger.base.system.ENewLineMode;
import com.helger.datetime.helper.PDTFactory;
import com.helger.datetime.xml.XMLOffsetDateTime;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.IRepoStorageReadItem;
import com.helger.diver.repo.RepoStorageContentByteArray;
import com.helger.diver.repo.RepoStorageKey;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.jaxb.GenericJAXBMarshaller;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.ves.model.v1.VES1Marshaller;
import com.helger.phive.ves.model.v1.VESStatus1Marshaller;
import com.helger.phive.ves.v10.VesStatusHistoryItemType;
import com.helger.phive.ves.v10.VesStatusHistoryType;
import com.helger.phive.ves.v10.VesStatusReplacementType;
import com.helger.phive.ves.v10.VesStatusType;
import com.helger.phive.ves.v10.VesType;
import com.helger.scuba.api.repo.ScubaException;
import com.helger.scuba.upload.ScubaUploader;

/**
 * Phive-specific upload convenience methods for VES and VESStatus artifacts. Wraps a
 * {@link ScubaUploader} and adds domain-specific upload logic ported from {@code CentralUploader}.
 *
 * @author Philip Helger
 */
public class PhiveUploader
{
  /** The author name used in VES status history items. */
  public static final String AUTHOR_SYSTEM = "ph-scuba-phive";

  private static final Logger LOGGER = LoggerFactory.getLogger (PhiveUploader.class);
  private final ScubaUploader m_aUploader;

  /**
   * Constructor wrapping a {@link ScubaUploader} for phive-specific upload operations.
   *
   * @param aUploader
   *        The scuba uploader to delegate to. May not be <code>null</code>.
   */
  public PhiveUploader (@NonNull final ScubaUploader aUploader)
  {
    ValueEnforcer.notNull (aUploader, "Uploader");
    m_aUploader = aUploader;
  }

  /**
   * Get the underlying scuba uploader.
   *
   * @return The scuba uploader. Never <code>null</code>.
   */
  @NonNull
  public ScubaUploader getUploader ()
  {
    return m_aUploader;
  }

  @NonNull
  private static <T extends GenericJAXBMarshaller <JAXBTYPE>, JAXBTYPE> T _getUnifiedMarshaller (@NonNull final T aMarshaller)
  {
    // Using the UNIX new line mode is crucial - it impacts the SHA-256 creation
    aMarshaller.withXMLWriterSettings (x -> x.setNewLineMode (ENewLineMode.UNIX)).setFormattedOutput (true);
    return aMarshaller;
  }

  /**
   * Upload a VES definition to the repository. The DVR coordinate is extracted from the VES object.
   *
   * @param aVes
   *        The VES to upload. May not be <code>null</code>.
   * @return {@link EChange} depending on something changed or not
   * @throws IOException
   *         On IO error
   * @throws ScubaException
   *         If some scuba constraints don't match
   */
  @NonNull
  public EChange addVES (@NonNull final VesType aVes) throws IOException, ScubaException
  {
    ValueEnforcer.notNull (aVes, "VES");

    // Serialize VES Status
    final ErrorList aXMLErrorList = new ErrorList ();
    final byte [] aStatusBytes = _getUnifiedMarshaller (new VES1Marshaller ()).setCollectErrors (aXMLErrorList)
                                                                              .getAsBytes (aVes);
    if (aStatusBytes == null)
    {
      LOGGER.error ("Failed to serialize VES as XML:");
      aXMLErrorList.forEach (x -> LOGGER.error ("  " + x.getAsStringLocaleIndepdent ()));
      return EChange.UNCHANGED;
    }

    // Take VESID from the inside
    m_aUploader.uploadResource (new DVRCoordinate (aVes.getGroupId (),
                                                   aVes.getArtifactId (),
                                                   DVRVersion.parseOrNull (aVes.getVersion ())),
                                RepoStorageContentByteArray.of (aStatusBytes),
                                VESLoader.FILE_EXT_VES);
    return EChange.CHANGED;
  }

  /**
   * Upload a VES status definition to the repository. The DVR coordinate is extracted from the
   * status object.
   *
   * @param aVESStatus
   *        The VES status to upload. May not be <code>null</code>.
   * @return {@link EChange} depending on something changed or not
   * @throws IOException
   *         On IO error
   * @throws ScubaException
   *         If some scuba constraints don't match
   */
  @NonNull
  public EChange addVESStatus (@NonNull final VesStatusType aVESStatus) throws IOException, ScubaException
  {
    ValueEnforcer.notNull (aVESStatus, "VESStatus");

    // Serialize VES Status
    final ErrorList aXMLErrorList = new ErrorList ();
    final byte [] aStatusBytes = _getUnifiedMarshaller (new VESStatus1Marshaller ()).setCollectErrors (aXMLErrorList)
                                                                                    .getAsBytes (aVESStatus);
    if (aStatusBytes == null)
    {
      LOGGER.error ("Failed to serialize uploaded status as XML:");
      aXMLErrorList.forEach (x -> LOGGER.error ("  " + x.getAsStringLocaleIndepdent ()));
      return EChange.UNCHANGED;
    }

    m_aUploader.uploadResource (new DVRCoordinate (aVESStatus.getGroupId (),
                                                   aVESStatus.getArtifactId (),
                                                   DVRVersion.parseOrNull (aVESStatus.getVersion ())),
                                RepoStorageContentByteArray.of (aStatusBytes),
                                VESLoader.FILE_EXT_STATUS);
    return EChange.CHANGED;
  }

  /**
   * Mark a VES as deprecated. Reads the existing status (or creates a new one), sets the deprecated
   * flag and optionally a deprecation reason and replacement VESID, then writes it back.
   *
   * @param aVESID
   *        The VESID to deprecate. Must be a static version. May not be <code>null</code>.
   * @param sDeprecationReason
   *        Optional deprecation reason. May be <code>null</code>.
   * @param aReplacementVESID
   *        Optional replacement VESID. May be <code>null</code>.
   * @return {@link EChange#CHANGED} if the deprecation was applied, {@link EChange#UNCHANGED} if
   *         already deprecated or write failed.
   */
  @NonNull
  public EChange setVESDeprecated (@NonNull final DVRCoordinate aVESID,
                                   @Nullable final String sDeprecationReason,
                                   @Nullable final DVRCoordinate aReplacementVESID)
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    ValueEnforcer.isTrue (aVESID.getVersionObj ().isStaticVersion (), "Cannot set a pseudo version as deprecated");

    final RepoStorageKey aStatusKey = RepoStorageKeyOfArtefact.of (aVESID, VESLoader.FILE_EXT_STATUS);
    final IRepoStorageReadItem aOldReadStatus = m_aUploader.getRepoStorage ().read (aStatusKey);
    final VesStatusType aStatus;
    if (aOldReadStatus != null)
    {
      aStatus = new VESStatus1Marshaller ().read (aOldReadStatus.getContent ().getBufferedInputStream ());
      if (aStatus == null)
        throw new IllegalStateException ("Old status could not be read!");

      if (aStatus.isDeprecated () != null && aStatus.isDeprecated ().booleanValue ())
      {
        LOGGER.warn ("VESID '" + aVESID.getAsSingleID () + "' is already deprecated");
        return EChange.UNCHANGED;
      }
    }
    else
    {
      aStatus = new VesStatusType ();
      aStatus.setGroupId (aVESID.getGroupID ());
      aStatus.setArtifactId (aVESID.getArtifactID ());
      aStatus.setVersion (aVESID.getVersionString ());
      aStatus.setHistory (new VesStatusHistoryType ());
    }

    final XMLOffsetDateTime aNow = PDTFactory.getCurrentXMLOffsetDateTimeUTC ();
    aStatus.setStatusLastModified (aNow);

    aStatus.setDeprecated (Boolean.TRUE);
    if (StringHelper.isNotEmpty (sDeprecationReason))
      aStatus.setDeprecationReason (sDeprecationReason);

    if (aReplacementVESID != null)
    {
      final VesStatusReplacementType aReplacement = new VesStatusReplacementType ();
      aReplacement.setGroupId (aReplacementVESID.getGroupID ());
      aReplacement.setArtifactId (aReplacementVESID.getArtifactID ());
      aReplacement.setVersion (aReplacementVESID.getVersionString ());
      aStatus.setReplacement (aReplacement);
    }

    // Add history item
    final VesStatusHistoryItemType aHistoryItem = new VesStatusHistoryItemType ();
    aHistoryItem.setChangeDateTime (aNow);
    aHistoryItem.setAuthor (AUTHOR_SYSTEM);
    aHistoryItem.setValue ("Marked as deprecated");
    aStatus.getHistory ().addHistoryItem (aHistoryItem);

    // Serialize VES Status
    final ErrorList aXMLErrorList = new ErrorList ();
    final byte [] aStatusBytes = _getUnifiedMarshaller (new VESStatus1Marshaller ()).setCollectErrors (aXMLErrorList)
                                                                                    .getAsBytes (aStatus);
    if (aStatusBytes == null)
    {
      LOGGER.error ("Failed to serialize uploaded status as XML:");
      aXMLErrorList.forEach (x -> LOGGER.error ("  " + x.getAsStringLocaleIndepdent ()));
      return EChange.UNCHANGED;
    }

    // Upload updated version
    if (m_aUploader.getRepoStorage ().write (aStatusKey, RepoStorageContentByteArray.of (aStatusBytes)).isFailure ())
    {
      LOGGER.error ("Failed to upload updated status to repository");
      return EChange.UNCHANGED;
    }

    return EChange.CHANGED;
  }

  /**
   * Mark a VES as deprecated without specifying a replacement.
   *
   * @param aVESID
   *        The VESID to deprecate. Must be a static version. May not be <code>null</code>.
   * @param sDeprecationReason
   *        Optional deprecation reason. May be <code>null</code>.
   * @return {@link EChange#CHANGED} if the deprecation was applied, {@link EChange#UNCHANGED} if
   *         already deprecated or write failed.
   */
  @NonNull
  public EChange setVESDeprecated (@NonNull final DVRCoordinate aVESID, @Nullable final String sDeprecationReason)
  {
    return setVESDeprecated (aVESID, sDeprecationReason, (DVRCoordinate) null);
  }

  @Nullable
  private static OffsetDateTime _toODT (@Nullable final XMLOffsetDateTime aODT)
  {
    return aODT == null ? null : aODT.toOffsetDateTime ();
  }

  /**
   * Set the validity dates of a VES. Reads the existing status (or creates a new one), updates the
   * validFrom/validTo dates, then writes it back.
   *
   * @param aVESID
   *        The VESID to update. Must be a static version. May not be <code>null</code>.
   * @param aValidFrom
   *        The validity start date. May be <code>null</code> but at least one of validFrom/validTo
   *        must be set.
   * @param aValidTo
   *        The validity end date. May be <code>null</code> but at least one of validFrom/validTo
   *        must be set.
   * @return {@link EChange#CHANGED} if the dates were updated, {@link EChange#UNCHANGED} if
   *         unchanged or write failed.
   */
  @NonNull
  public EChange setVESValidityDate (@NonNull final DVRCoordinate aVESID,
                                     @Nullable final OffsetDateTime aValidFrom,
                                     @Nullable final OffsetDateTime aValidTo)
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    ValueEnforcer.isTrue (aVESID.getVersionObj ().isStaticVersion (), "Cannot set a pseudo version as deprecated");
    ValueEnforcer.isTrue (aValidFrom != null || aValidTo != null, "ValidFrom or ValidTo must be set");
    if (aValidFrom != null && aValidTo != null)
      ValueEnforcer.isTrue (aValidFrom.compareTo (aValidTo) <= 0, "ValidFrom must not be after ValidTo");

    final RepoStorageKey aStatusKey = RepoStorageKeyOfArtefact.of (aVESID, VESLoader.FILE_EXT_STATUS);
    final IRepoStorageReadItem aOldReadStatus = m_aUploader.getRepoStorage ().read (aStatusKey);
    final VesStatusType aStatus;
    if (aOldReadStatus != null)
    {
      aStatus = new VESStatus1Marshaller ().read (aOldReadStatus.getContent ().getBufferedInputStream ());
      if (aStatus == null)
        throw new IllegalStateException ("Old status could not be read!");

      final boolean bSameValidFrom = EqualsHelper.equals (aValidFrom, _toODT (aStatus.getValidFrom ()));
      final boolean bSameValidTo = EqualsHelper.equals (aValidTo, _toODT (aStatus.getValidTo ()));
      if (bSameValidFrom && bSameValidTo)
      {
        LOGGER.warn ("VESID '" + aVESID.getAsSingleID () + "' already has the provided validity dates");
        return EChange.UNCHANGED;
      }
    }
    else
    {
      aStatus = new VesStatusType ();
      aStatus.setGroupId (aVESID.getGroupID ());
      aStatus.setArtifactId (aVESID.getArtifactID ());
      aStatus.setVersion (aVESID.getVersionString ());
      aStatus.setHistory (new VesStatusHistoryType ());
    }

    final XMLOffsetDateTime aNow = PDTFactory.getCurrentXMLOffsetDateTimeUTC ();
    aStatus.setStatusLastModified (aNow);

    if (aValidFrom != null)
      aStatus.setValidFrom (XMLOffsetDateTime.from (aValidFrom));
    if (aValidTo != null)
      aStatus.setValidTo (XMLOffsetDateTime.from (aValidTo));

    // Add history item
    final VesStatusHistoryItemType aHistoryItem = new VesStatusHistoryItemType ();
    aHistoryItem.setChangeDateTime (aNow);
    aHistoryItem.setAuthor (AUTHOR_SYSTEM);
    aHistoryItem.setValue ("Updated validity dates");
    aStatus.getHistory ().addHistoryItem (aHistoryItem);

    // Serialize VES Status
    final ErrorList aXMLErrorList = new ErrorList ();
    final byte [] aStatusBytes = _getUnifiedMarshaller (new VESStatus1Marshaller ()).setCollectErrors (aXMLErrorList)
                                                                                    .getAsBytes (aStatus);
    if (aStatusBytes == null)
    {
      LOGGER.error ("Failed to serialize uploaded status as XML:");
      aXMLErrorList.forEach (x -> LOGGER.error ("  " + x.getAsStringLocaleIndepdent ()));
      return EChange.UNCHANGED;
    }

    // Upload updated version
    if (m_aUploader.getRepoStorage ().write (aStatusKey, RepoStorageContentByteArray.of (aStatusBytes)).isFailure ())
    {
      LOGGER.error ("Failed to upload updated status to repository");
      return EChange.UNCHANGED;
    }

    return EChange.CHANGED;
  }
}
