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
package com.helger.scuba.upload;

import java.io.IOException;
import java.io.InputStream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.io.stream.StreamHelper;
import com.helger.base.state.EValidity;
import com.helger.base.string.StringHelper;
import com.helger.base.tostring.ToStringGenerator;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.IRepoStorageContent;
import com.helger.diver.repo.RepoStorageContentByteArray;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.io.file.FilenameHelper;
import com.helger.io.resource.IReadableResource;
import com.helger.scuba.api.IScubaUploader;
import com.helger.scuba.api.repo.RepoKeyAlreadyInUseException;
import com.helger.scuba.api.repo.ScubaException;
import com.helger.scuba.validator.UploadContentValidator;

/**
 * Default implementation of {@link IScubaUploader}. Delegates content validation to
 * {@link UploadContentValidator} which manages SPI loading and dispatch.
 *
 * @author Philip Helger
 */
public class ScubaUploader implements IScubaUploader
{
  private static final Logger LOGGER = LoggerFactory.getLogger (ScubaUploader.class);

  private final IRepoStorageWithToc m_aRepo;
  private final UploadContentValidator m_aContentValidator;
  private final ScubaUploaderSettings m_aSettings;

  /**
   * Create a new uploader with default settings.
   *
   * @param aRepo
   *        The repository storage to use. May not be <code>null</code>.
   */
  public ScubaUploader (@NonNull final IRepoStorageWithToc aRepo)
  {
    this (aRepo, new ScubaUploaderSettings ());
  }

  /**
   * Create a new uploader with custom settings.
   *
   * @param aRepo
   *        The repository storage to use. May not be <code>null</code>.
   * @param aSettings
   *        The settings to apply. May not be <code>null</code>. The settings are copied internally.
   */
  public ScubaUploader (@NonNull final IRepoStorageWithToc aRepo, @NonNull final ScubaUploaderSettings aSettings)
  {
    ValueEnforcer.notNull (aRepo, "Repo");
    ValueEnforcer.notNull (aSettings, "Settings");
    if (!aRepo.canWrite ())
      throw new IllegalArgumentException ("The provided repository is not in write-mode");

    m_aRepo = aRepo;
    m_aContentValidator = new UploadContentValidator (aRepo);
    // Defensive copy
    m_aSettings = aSettings.getClone ();
  }

  @NonNull
  public IRepoStorageWithToc getRepoStorage ()
  {
    return m_aRepo;
  }

  /**
   * Get the content validator used by this uploader.
   *
   * @return The content validator. Never <code>null</code>.
   */
  @NonNull
  public UploadContentValidator getContentValidator ()
  {
    return m_aContentValidator;
  }

  /**
   * Get a copy of the settings used by this uploader.
   *
   * @return A defensive copy of the settings. Never <code>null</code>.
   */
  @NonNull
  @ReturnsMutableCopy
  public ScubaUploaderSettings getSettings ()
  {
    return m_aSettings.getClone ();
  }

  /**
   * Get the file extension of the provided payload.
   *
   * @param aPayload
   *        The payload. May not be <code>null</code>.
   * @return The file extension including the leading dot or <code>null</code> if the path of the
   *         payload has no file extension.
   */
  @Nullable
  private static String _getFileExt (@NonNull final IReadableResource aPayload)
  {
    final String sExt = FilenameHelper.getExtension (aPayload.getPath ());
    return StringHelper.isEmpty (sExt) ? null : "." + sExt;
  }

  /**
   * Make sure the file extension starts with a dot.
   *
   * @param sFileExt
   *        The file extension with or without the leading dot. May not be <code>null</code>.
   * @return The file extension with the leading dot.
   */
  @NonNull
  private static String _getWithLeadingDot (@NonNull final String sFileExt)
  {
    return StringHelper.startsWith (sFileExt, '.') ? sFileExt : '.' + sFileExt;
  }

  /**
   * Read the complete payload, so that the checked content is exactly the uploaded content.
   *
   * @param aPayload
   *        The payload to read. May not be <code>null</code>.
   * @return The content. Never <code>null</code>.
   * @throws IOException
   *         If the payload cannot be read.
   */
  @NonNull
  private static IRepoStorageContent _readPayload (@NonNull final IReadableResource aPayload) throws IOException
  {
    final byte [] aBytes = StreamHelper.getAllBytes (aPayload);
    if (aBytes == null)
      throw new IOException ("Failed to read the resource '" + aPayload.getPath () + "'");
    return RepoStorageContentByteArray.of (aBytes);
  }

  /**
   * Check the content with the validator registered for the file extension.
   *
   * @param sContextPath
   *        The context path for error messages. May not be <code>null</code>.
   * @param sFileExt
   *        The file extension including the leading dot. May not be <code>null</code>.
   * @param aContent
   *        The content to check. May not be <code>null</code>.
   * @return <code>true</code> if the content is valid. All errors are logged.
   * @throws IOException
   *         On IO error
   */
  private boolean _isValidContent (@NonNull final String sContextPath,
                                   @NonNull final String sFileExt,
                                   @NonNull final IRepoStorageContent aContent) throws IOException
  {
    final ErrorList aErrorList = new ErrorList ();
    final boolean bValid;
    // Opens a new stream on the in-memory content, so that the same bytes can be written afterwards
    try (final InputStream aIS = aContent.getBufferedInputStream ())
    {
      bValid = m_aContentValidator.validateContent (sContextPath, sFileExt, aIS, aErrorList);
    }
    if (!bValid)
      for (final var aError : aErrorList.getAllErrors ())
        LOGGER.error (aError.getAsStringLocaleIndepdent ());
    return bValid;
  }

  @NonNull
  public EValidity isValidResource (@NonNull final IReadableResource aPayload) throws IOException
  {
    ValueEnforcer.notNull (aPayload, "Payload");

    final String sFileExt = _getFileExt (aPayload);
    if (sFileExt == null)
    {
      LOGGER.error ("The resource '" + aPayload.getPath () + "' has no file extension");
      return EValidity.INVALID;
    }

    if (!m_aContentValidator.hasValidatorForExtension (sFileExt))
    {
      // No validator available - validity depends on settings
      return EValidity.valueOf (m_aSettings.isAllowUploadWithUnknownExtension ());
    }

    // Empty context path - top level resource, so no prefix on the error messages
    return EValidity.valueOf (_isValidContent ("", sFileExt, _readPayload (aPayload)));
  }

  private void _uploadResource (@NonNull final DVRCoordinate aCoordinate,
                                @NonNull final IRepoStorageContent aContent,
                                @NonNull @Nonempty final String sFileExt) throws IOException, ScubaException
  {
    // Content validation
    if (m_aContentValidator.hasValidatorForExtension (sFileExt))
    {
      // Logs all collected errors
      if (!_isValidContent (aCoordinate.getAsSingleID (), sFileExt, aContent))
      {
        final String sMsg = "Data for coordinate '" +
                            aCoordinate.getAsSingleID () +
                            "' does not match expectations of the file extension ('" +
                            sFileExt +
                            "')";
        LOGGER.error (sMsg);
        throw new ScubaException (sMsg + " - see log for details");
      }
    }
    else
    {
      // No validator registered for this file extension
      if (!m_aSettings.isAllowUploadWithUnknownExtension ())
      {
        final String sMsg = "Unsupported file extension '" +
                            sFileExt +
                            "' - no content validator registered and upload of unknown extensions is disabled";
        LOGGER.error (sMsg);
        throw new ScubaException (sMsg);
      }

      LOGGER.warn ("No content validator for file extension '" + sFileExt + "' - uploading without validation");
    }

    // Create StorageKey
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, sFileExt);

    // Duplicate check
    if (m_aRepo.exists (aKey))
    {
      if (!m_aSettings.isAllowOverwriteExisting ())
      {
        final String sMsg = "A resource with key '" +
                            aKey.getPath () +
                            "' is already in Repo '" +
                            m_aRepo.getID () +
                            "' and overwrite is disabled.";
        LOGGER.error (sMsg);
        throw new RepoKeyAlreadyInUseException (sMsg);
      }

      LOGGER.warn ("Overwriting existing resource with key '" +
                   aKey.getPath () +
                   "' in Repo '" +
                   m_aRepo.getID () +
                   "'");
    }

    // Write data to Repo
    if (m_aRepo.write (aKey, aContent).isFailure ())
    {
      final String sMsg = "Failed to write content of key '" + aKey.getPath () + "' to Repo '" + m_aRepo.getID () + "'";
      LOGGER.error (sMsg);
      throw new IOException (sMsg);
    }
  }

  public void addResource (@NonNull final DVRCoordinate aCoordinate,
                           @NonNull final IReadableResource aPayload) throws IOException, ScubaException
  {
    ValueEnforcer.notNull (aCoordinate, "Coordinate");
    ValueEnforcer.notNull (aPayload, "Payload");

    if (!aPayload.exists ())
    {
      final String sMsg = "The provided resource '" + aPayload.getPath () + "' does not exist";
      LOGGER.error (sMsg);
      throw new IllegalArgumentException (sMsg);
    }

    final String sFileExt = _getFileExt (aPayload);
    if (sFileExt == null)
    {
      final String sMsg = "The provided resource '" + aPayload.getPath () + "' has no file extension";
      LOGGER.error (sMsg);
      throw new IllegalArgumentException (sMsg);
    }

    // Read once, so that the checked content is the uploaded content
    _uploadResource (aCoordinate, _readPayload (aPayload), sFileExt);
  }

  /**
   * Upload content directly with a specific file extension. Used by domain-specific uploaders
   * (e.g., ph-scuba-phive) that marshal objects to bytes before uploading.
   *
   * @param aCoordinate
   *        The DVR coordinate. May not be <code>null</code>.
   * @param aContent
   *        The content to upload. May not be <code>null</code>.
   * @param sFileExt
   *        The file extension including the leading dot. May not be <code>null</code>.
   * @throws IOException
   *         On IO error
   * @throws ScubaException
   *         If some scuba constraints don't match
   */
  public void uploadResource (@NonNull final DVRCoordinate aCoordinate,
                              @NonNull final IRepoStorageContent aContent,
                              @NonNull @Nonempty final String sFileExt) throws IOException, ScubaException
  {
    ValueEnforcer.notNull (aCoordinate, "Coordinate");
    ValueEnforcer.notNull (aContent, "Content");
    ValueEnforcer.notEmpty (sFileExt, "FileExt");
    _uploadResource (aCoordinate, aContent, sFileExt);
  }

  public boolean existsResource (@NonNull final DVRCoordinate aCoordinate, @NonNull final String sFileExt)
  {
    ValueEnforcer.notNull (aCoordinate, "Coordinate");
    ValueEnforcer.notEmpty (sFileExt, "FileExt");

    // Create StorageKey (must start with '.')
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, _getWithLeadingDot (sFileExt));
    return m_aRepo.exists (aKey);
  }

  public void deleteResource (@NonNull final DVRCoordinate aCoordinate,
                              @NonNull final String sFileExt) throws IOException
  {
    ValueEnforcer.notNull (aCoordinate, "Coordinate");
    ValueEnforcer.notEmpty (sFileExt, "FileExt");
    if (!m_aRepo.canDelete ())
      throw new IllegalStateException ("The underlying Repository object does not allow deletion");

    // Create StorageKey (must start with '.')
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, _getWithLeadingDot (sFileExt));

    // Delete from Repo
    if (m_aRepo.delete (aKey).isFailure ())
    {
      final String sMsg = "Failed to delete '" + aKey.getPath () + "' from Repo '" + m_aRepo.getID () + "'";
      LOGGER.error (sMsg);
      throw new IOException (sMsg);
    }
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("Repo", m_aRepo)
                                       .append ("ContentValidator", m_aContentValidator)
                                       .append ("Settings", m_aSettings)
                                       .getToString ();
  }
}
