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
import com.helger.annotation.Nonnegative;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.EValidity;
import com.helger.base.string.StringHelper;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.IRepoStorageContent;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.io.file.FilenameHelper;
import com.helger.io.resource.IReadableResource;
import com.helger.scuba.api.IScubaUploader;
import com.helger.scuba.api.repo.RepoKeyAlreadyInUseException;
import com.helger.scuba.validator.UploadContentValidator;

/**
 * Default implementation of {@link IScubaUploader}. Delegates content validation to
 * {@link UploadContentValidator} which manages SPI loading and dispatch.
 *
 * @author Philip Helger
 */
public class ScubaUploader implements IScubaUploader
{
  private static final class RepoStorageContentFromReadableResource implements IRepoStorageContent
  {
    private final IReadableResource m_aPayload;

    private RepoStorageContentFromReadableResource (@NonNull final IReadableResource aPayload)
    {
      m_aPayload = aPayload;
    }

    public boolean isReadMultiple ()
    {
      return m_aPayload.isReadMultiple ();
    }

    @Nullable
    public InputStream getInputStream ()
    {
      return m_aPayload.getInputStream ();
    }

    @Nonnegative
    public long getLength ()
    {
      return m_aPayload.getAsFile ().length ();
    }
  }

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

  @NonNull
  private static String _getFileExt (@NonNull final IReadableResource aPayload)
  {
    return "." + FilenameHelper.getExtension (aPayload.getPath ());
  }

  @NonNull
  public EValidity isValidResource (@NonNull final IReadableResource aPayload) throws IOException
  {
    ValueEnforcer.notNull (aPayload, "Payload");

    final String sFileExt = _getFileExt (aPayload);

    if (!m_aContentValidator.hasValidatorForExtension (sFileExt))
    {
      // No validator available - validity depends on settings
      return EValidity.valueOf (m_aSettings.isAllowUploadWithUnknownExtension ());
    }

    final IRepoStorageContent aContent = new RepoStorageContentFromReadableResource (aPayload);
    final ErrorList aErrorList = new ErrorList ();
    final boolean bValid = m_aContentValidator.validateContent ("",
                                                                sFileExt,
                                                                aContent.getBufferedInputStream (),
                                                                aErrorList);
    if (!bValid)
      aErrorList.getAllErrors ().forEach (e -> LOGGER.error (e.getAsStringLocaleIndepdent ()));
    return EValidity.valueOf (bValid);
  }

  private void _uploadResource (@NonNull final DVRCoordinate aCoordinate,
                                @NonNull final IRepoStorageContent aContent,
                                @NonNull @Nonempty final String sFileExt) throws IOException
  {
    // Content validation
    if (m_aContentValidator.hasValidatorForExtension (sFileExt))
    {
      final ErrorList aErrorList = new ErrorList ();
      if (!m_aContentValidator.validateContent (aCoordinate.getAsSingleID (),
                                                sFileExt,
                                                aContent.getBufferedInputStream (),
                                                aErrorList))
      {
        // Log all collected errors
        aErrorList.getAllErrors ().forEach (e -> LOGGER.error (e.getAsStringLocaleIndepdent ()));
        throw new IllegalStateException ("Data for coordinate '" +
                                         aCoordinate.getAsSingleID () +
                                         "' does not match expectations of the file extension ('" +
                                         sFileExt +
                                         "') - see log for details");
      }
    }
    else
    {
      // No validator registered for this file extension
      if (!m_aSettings.isAllowUploadWithUnknownExtension ())
      {
        throw new IllegalArgumentException ("Unsupported file extension '" +
                                            sFileExt +
                                            "' - no content validator registered and upload of unknown extensions is disabled");
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
        throw new RepoKeyAlreadyInUseException ("A resource with key '" + aKey.getPath () + "' is already in the Repo");
      }

      LOGGER.warn ("Overwriting existing resource with key '" + aKey.getPath () + "'");
    }

    // Write data to Repo
    if (m_aRepo.write (aKey, aContent).isFailure ())
      throw new IllegalStateException ("Failed to write");
  }

  public void addResource (@NonNull final DVRCoordinate aCoordinate, @NonNull final IReadableResource aPayload)
                                                                                                                throws IOException
  {
    ValueEnforcer.notNull (aCoordinate, "Coordinate");
    ValueEnforcer.notNull (aPayload, "Payload");

    if (!aPayload.exists ())
      throw new IllegalArgumentException ("The provided resource '" + aPayload.getPath () + "' does not exist");

    final IRepoStorageContent aContent = new RepoStorageContentFromReadableResource (aPayload);
    _uploadResource (aCoordinate, aContent, _getFileExt (aPayload));
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
   */
  public void uploadResource (@NonNull final DVRCoordinate aCoordinate,
                              @NonNull final IRepoStorageContent aContent,
                              @NonNull @Nonempty final String sFileExt) throws IOException
  {
    ValueEnforcer.notNull (aCoordinate, "Coordinate");
    ValueEnforcer.notNull (aContent, "Content");
    ValueEnforcer.notEmpty (sFileExt, "FileExt");
    _uploadResource (aCoordinate, aContent, sFileExt);
  }

  public boolean existsResource (@NonNull final DVRCoordinate aCoordinate, @NonNull final String sFileExt)
  {
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, sFileExt);
    return m_aRepo.exists (aKey);
  }

  public void deleteResource (@NonNull final DVRCoordinate aCoordinate, @NonNull final String sFileExt)
  {
    if (!m_aRepo.canDelete ())
      throw new IllegalStateException ("The underlying Repository object does not allow deletion");

    // Create StorageKey (must start with '.')
    final String sRealFileExt = StringHelper.startsWith (sFileExt, '.') ? sFileExt : '.' + sFileExt;
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, sRealFileExt);

    // Delete from Repo
    if (m_aRepo.delete (aKey).isFailure ())
      LOGGER.warn ("Failed to delete " + aKey);
  }

  /**
   * Get the underlying repository storage with ToC support.
   *
   * @return The repository storage. Never <code>null</code>.
   */
  @NonNull
  public IRepoStorageWithToc getRepoStorageWithToc ()
  {
    return m_aRepo;
  }
}
