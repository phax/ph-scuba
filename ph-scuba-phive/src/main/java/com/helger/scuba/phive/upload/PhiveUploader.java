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
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.OffsetDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
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
import com.helger.diver.repo.ERepoHashState;
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
  /** The default author name used in VES status history items. */
  public static final String AUTHOR_SYSTEM = "ph-scuba-phive";
  /**
   * Default: only warn if the hash value of an existing VES status does not match
   *
   * @since 1.2.0
   */
  public static final boolean DEFAULT_FAIL_ON_STATUS_HASH_MISMATCH = false;

  private static final Logger LOGGER = LoggerFactory.getLogger (PhiveUploader.class);
  private final ScubaUploader m_aUploader;
  private String m_sAuthor = AUTHOR_SYSTEM;
  private boolean m_bFailOnStatusHashMismatch = DEFAULT_FAIL_ON_STATUS_HASH_MISMATCH;

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

  /**
   * @return The author name used in the VES status history items created by this uploader. Never
   *         <code>null</code>. Defaults to {@link #AUTHOR_SYSTEM}.
   * @since 1.2.0
   */
  @NonNull
  @Nonempty
  public final String getAuthor ()
  {
    return m_sAuthor;
  }

  /**
   * Set the author name used in the VES status history items created by this uploader, so that the
   * history shows who made a change.
   *
   * @param sAuthor
   *        The author name. May neither be <code>null</code> nor empty.
   * @return this for chaining
   * @since 1.2.0
   */
  @NonNull
  public final PhiveUploader setAuthor (@NonNull @Nonempty final String sAuthor)
  {
    ValueEnforcer.notEmpty (sAuthor, "Author");
    m_sAuthor = sAuthor;
    return this;
  }

  /**
   * @return {@code true} if modifying an existing VES status is refused when its hash value does not
   *         match its content, {@code false} if only a warning is logged. Default is
   *         {@value #DEFAULT_FAIL_ON_STATUS_HASH_MISMATCH}.
   * @since 1.2.0
   */
  public final boolean isFailOnStatusHashMismatch ()
  {
    return m_bFailOnStatusHashMismatch;
  }

  /**
   * Set whether modifying an existing VES status is refused when its hash value does not match its
   * content. Without this, a tampered status would be modified and stored with a new, matching hash
   * value.
   *
   * @param bFail
   *        {@code true} to refuse, {@code false} to only log a warning.
   * @return this for chaining
   * @since 1.2.0
   */
  @NonNull
  public final PhiveUploader setFailOnStatusHashMismatch (final boolean bFail)
  {
    m_bFailOnStatusHashMismatch = bFail;
    return this;
  }

  @NonNull
  private static <T extends GenericJAXBMarshaller <JAXBTYPE>, JAXBTYPE> T _getUnifiedMarshaller (@NonNull final T aMarshaller)
  {
    // Using the UNIX new line mode is crucial - it impacts the SHA-256 creation
    aMarshaller.withXMLWriterSettings (x -> x.setNewLineMode (ENewLineMode.UNIX)).setFormattedOutput (true);
    // Avoid logging all details
    aMarshaller.writeExceptionCallbacks ().removeAll ();
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
    final var m = _getUnifiedMarshaller (new VESStatus1Marshaller ());
    final ErrorList aXMLErrorList = new ErrorList ();
    final byte [] aStatusBytes = m.setCollectErrors (aXMLErrorList).getAsBytes (aVESStatus);
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
   * Read the existing VES status from the repository.
   *
   * @param aStatusKey
   *        The repository key of the status. May not be <code>null</code>.
   * @return <code>null</code> if no status exists.
   * @throws IllegalStateException
   *         If the existing status cannot be read, or if its hash value was not verified as matching
   *         and {@link #isFailOnStatusHashMismatch()} is enabled.
   */
  @Nullable
  private VesStatusType _readExistingStatus (@NonNull final RepoStorageKey aStatusKey)
  {
    final IRepoStorageReadItem aOldReadStatus = m_aUploader.getRepoStorage ().read (aStatusKey);
    if (aOldReadStatus == null)
      return null;

    final ERepoHashState eHashState = aOldReadStatus.getHashState ();
    if (eHashState == ERepoHashState.VERIFIED_NON_MATCHING)
    {
      final String sMsg = "The hash value of the existing status '" +
                          aStatusKey.getPath () +
                          "' does not match its content - it may have been tampered with";
      if (m_bFailOnStatusHashMismatch)
      {
        LOGGER.error (sMsg);
        throw new IllegalStateException (sMsg);
      }
      LOGGER.warn (sMsg);
    }
    else
      if (eHashState != ERepoHashState.VERIFIED_MATCHING && m_bFailOnStatusHashMismatch)
      {
        // E.g. the hash file is missing
        final String sMsg = "The hash value of the existing status '" +
                            aStatusKey.getPath () +
                            "' could not be verified (" +
                            eHashState.getDisplayName () +
                            ") - it may have been tampered with";
        LOGGER.error (sMsg);
        throw new IllegalStateException (sMsg);
      }

    final VesStatusType ret = new VESStatus1Marshaller ().read (aOldReadStatus.getContent ().getBufferedInputStream ());
    if (ret == null)
      throw new IllegalStateException ("Old status could not be read!");
    return ret;
  }

  /**
   * Make sure the VES of the provided VESID exists, so that no status is written for a VES that is
   * not in the repository. Writing the status would otherwise add the version to the table of
   * contents.
   *
   * @param aVESID
   *        The VESID to check. May not be <code>null</code>.
   * @throws IllegalStateException
   *         If the VES does not exist.
   */
  private void _checkVESExists (@NonNull final DVRCoordinate aVESID)
  {
    if (!m_aUploader.getRepoStorage ().exists (RepoStorageKeyOfArtefact.of (aVESID, VESLoader.FILE_EXT_VES)))
    {
      final String sMsg = "The VESID '" + aVESID.getAsSingleID () + "' does not exist in the repository";
      LOGGER.error (sMsg);
      throw new IllegalStateException (sMsg);
    }
  }

  /**
   * Serialize the modified VES status, check it with the content validator registered for
   * {@link VESLoader#FILE_EXT_STATUS} and write it to the repository.
   *
   * @param aVESID
   *        The VESID the status belongs to. May not be <code>null</code>.
   * @param aStatusKey
   *        The repository key of the status. May not be <code>null</code>.
   * @param aStatus
   *        The status to write. May not be <code>null</code>.
   * @throws IllegalStateException
   *         If serializing, checking or writing failed.
   */
  private void _writeStatus (@NonNull final DVRCoordinate aVESID,
                             @NonNull final RepoStorageKey aStatusKey,
                             @NonNull final VesStatusType aStatus)
  {
    // Serialize VES Status
    final ErrorList aXMLErrorList = new ErrorList ();
    final byte [] aStatusBytes = _getUnifiedMarshaller (new VESStatus1Marshaller ()).setCollectErrors (aXMLErrorList)
                                                                                    .getAsBytes (aStatus);
    if (aStatusBytes == null)
    {
      LOGGER.error ("Failed to serialize uploaded status as XML:");
      aXMLErrorList.forEach (x -> LOGGER.error ("  " + x.getAsStringLocaleIndepdent ()));
      throw new IllegalStateException ("Failed to serialize the status of VESID '" +
                                       aVESID.getAsSingleID () +
                                       "' as XML - see log for details");
    }

    // Check the content like an upload does
    final RepoStorageContentByteArray aContent = RepoStorageContentByteArray.of (aStatusBytes);
    final ErrorList aErrorList = new ErrorList ();
    final boolean bValid;
    try (final InputStream aIS = aContent.getBufferedInputStream ())
    {
      bValid = m_aUploader.getContentValidator ()
                          .validateContent (aVESID.getAsSingleID (), VESLoader.FILE_EXT_STATUS, aIS, aErrorList);
    }
    catch (final IOException ex)
    {
      throw new UncheckedIOException (ex);
    }
    if (!bValid)
    {
      for (final var aError : aErrorList.getAllErrors ())
        LOGGER.error (aError.getAsStringLocaleIndepdent ());
      throw new IllegalStateException ("The updated status of VESID '" +
                                       aVESID.getAsSingleID () +
                                       "' is not valid - see log for details");
    }

    // Upload updated version
    if (m_aUploader.getRepoStorage ().write (aStatusKey, aContent).isFailure ())
    {
      final String sMsg = "Failed to upload updated status of VESID '" +
                          aVESID.getAsSingleID () +
                          "' to the repository";
      LOGGER.error (sMsg);
      throw new IllegalStateException (sMsg);
    }
  }

  /**
   * Mark a VES as deprecated. Reads the existing status (or creates a new one), sets the deprecated
   * flag and optionally a deprecation reason and replacement VESID, then writes it back. The
   * modified status is checked like an uploaded status before it is written, so a replacement VESID
   * must exist in the repository. The VES itself must exist in the repository as well.
   *
   * @param aVESID
   *        The VESID to deprecate. Must be a static version. May not be <code>null</code>.
   * @param sDeprecationReason
   *        Optional deprecation reason. May be <code>null</code>.
   * @param aReplacementVESID
   *        Optional replacement VESID. May be <code>null</code>.
   * @return {@link EChange#CHANGED} if the deprecation was applied, {@link EChange#UNCHANGED} if
   *         already deprecated.
   * @throws IllegalStateException
   *         If the VES does not exist, the existing status cannot be read or the modified status
   *         cannot be checked or written.
   */
  @NonNull
  public EChange setVESDeprecated (@NonNull final DVRCoordinate aVESID,
                                   @Nullable final String sDeprecationReason,
                                   @Nullable final DVRCoordinate aReplacementVESID)
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    ValueEnforcer.isTrue (aVESID.getVersionObj ().isStaticVersion (), "Cannot set a pseudo version as deprecated");
    _checkVESExists (aVESID);

    final RepoStorageKey aStatusKey = RepoStorageKeyOfArtefact.of (aVESID, VESLoader.FILE_EXT_STATUS);
    VesStatusType aStatus = _readExistingStatus (aStatusKey);
    if (aStatus != null)
    {
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
    aHistoryItem.setAuthor (m_sAuthor);
    aHistoryItem.setValue ("Marked as deprecated");
    aStatus.getHistory ().addHistoryItem (aHistoryItem);

    _writeStatus (aVESID, aStatusKey, aStatus);

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
   *         already deprecated.
   * @throws IllegalStateException
   *         If the VES does not exist, the existing status cannot be read or the modified status
   *         cannot be checked or written.
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
   * validFrom/validTo dates, then writes it back. The modified status is checked like an uploaded
   * status before it is written. The VES must exist in the repository. If only one date is
   * provided, the other one is taken from the existing status and the resulting validFrom must not
   * be after the resulting validTo.
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
   *         unchanged.
   * @throws IllegalArgumentException
   *         If the resulting validFrom is after the resulting validTo.
   * @throws IllegalStateException
   *         If the VES does not exist, the existing status cannot be read or the modified status
   *         cannot be checked or written.
   */
  @NonNull
  public EChange setVESValidityDate (@NonNull final DVRCoordinate aVESID,
                                     @Nullable final OffsetDateTime aValidFrom,
                                     @Nullable final OffsetDateTime aValidTo)
  {
    ValueEnforcer.notNull (aVESID, "VESID");
    ValueEnforcer.isTrue (aVESID.getVersionObj ().isStaticVersion (),
                          "Cannot set the validity dates of a pseudo version");
    ValueEnforcer.isTrue (aValidFrom != null || aValidTo != null, "ValidFrom or ValidTo must be set");
    if (aValidFrom != null && aValidTo != null)
      ValueEnforcer.isTrue (aValidFrom.compareTo (aValidTo) <= 0, "ValidFrom must not be after ValidTo");
    _checkVESExists (aVESID);

    final RepoStorageKey aStatusKey = RepoStorageKeyOfArtefact.of (aVESID, VESLoader.FILE_EXT_STATUS);
    VesStatusType aStatus = _readExistingStatus (aStatusKey);
    if (aStatus != null)
    {
      final boolean bSameValidFrom = EqualsHelper.equals (aValidFrom, _toODT (aStatus.getValidFrom ()));
      final boolean bSameValidTo = EqualsHelper.equals (aValidTo, _toODT (aStatus.getValidTo ()));
      if (bSameValidFrom && bSameValidTo)
      {
        LOGGER.warn ("VESID '" + aVESID.getAsSingleID () + "' already has the provided validity dates");
        return EChange.UNCHANGED;
      }

      // Check the combination with the existing date
      final OffsetDateTime aRealValidFrom = aValidFrom != null ? aValidFrom : _toODT (aStatus.getValidFrom ());
      final OffsetDateTime aRealValidTo = aValidTo != null ? aValidTo : _toODT (aStatus.getValidTo ());
      if (aRealValidFrom != null && aRealValidTo != null)
        ValueEnforcer.isTrue (aRealValidFrom.compareTo (aRealValidTo) <= 0,
                              () -> "The resulting ValidFrom " +
                                    aRealValidFrom +
                                    " must not be after the resulting ValidTo " +
                                    aRealValidTo);
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
    aHistoryItem.setAuthor (m_sAuthor);
    aHistoryItem.setValue ("Updated validity dates");
    aStatus.getHistory ().addHistoryItem (aHistoryItem);

    _writeStatus (aVESID, aStatusKey, aStatus);

    return EChange.CHANGED;
  }
}
