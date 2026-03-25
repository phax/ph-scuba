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
import java.util.ServiceLoader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.Nonempty;
import com.helger.annotation.Nonnegative;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.EValidity;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.IRepoStorage;
import com.helger.diver.repo.IRepoStorageContent;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.io.file.FilenameHelper;
import com.helger.io.resource.IReadableResource;
import com.helger.scuba.api.IUploader;
import com.helger.scuba.api.repo.RepoKeyAlreadyInUseException;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;

/**
 * Default implementation of {@link IUploader}. Loads all {@link IUploadContentValidatorSPI}
 * implementations via {@link ServiceLoader} and dispatches content validation by file extension
 * before upload.
 *
 * @author Philip Helger
 */
public class ScubaUploader implements IUploader
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
  private final ICommonsList <IUploadContentValidatorSPI> m_aValidators;

  public ScubaUploader (@NonNull final IRepoStorageWithToc aRepo)
  {
    ValueEnforcer.notNull (aRepo, "Repo");
    m_aRepo = aRepo;

    // Load all SPI validators
    m_aValidators = new CommonsArrayList <> ();
    for (final IUploadContentValidatorSPI aValidator : ServiceLoader.load (IUploadContentValidatorSPI.class))
    {
      aValidator.initRepoStorage (aRepo);
      m_aValidators.add (aValidator);
      LOGGER.info ("Loaded upload content validator: " +
                   aValidator.getClass ().getName () +
                   " for extensions " +
                   aValidator.getSupportedFileExtensions ());
    }
  }

  @NonNull
  public IRepoStorage getRepoStorage ()
  {
    return m_aRepo;
  }

  @Nullable
  private IUploadContentValidatorSPI _findValidator (@NonNull final String sFileExt)
  {
    for (final IUploadContentValidatorSPI aValidator : m_aValidators)
      if (aValidator.getSupportedFileExtensions ().contains (sFileExt))
        return aValidator;
    return null;
  }

  private boolean _isContentValid (@Nullable final String sContext,
                                   @NonNull final String sFileExt,
                                   @NonNull final IRepoStorageContent aContent,
                                   @NonNull final ErrorList aErrorList) throws IOException
  {
    LOGGER.info ("Checking content for" +
                 (sContext == null ? "" : " '" + sContext + "' and") +
                 " file extension '" +
                 sFileExt +
                 "'");
    try
    {
      final IUploadContentValidatorSPI aValidator = _findValidator (sFileExt);
      if (aValidator == null)
        throw new IllegalArgumentException ("Unsupported file extension '" +
                                            sFileExt +
                                            "' - no SPI validator registered");

      return aValidator.isValidContent (sFileExt, aContent.getBufferedInputStream (), aErrorList);
    }
    finally
    {
      LOGGER.info ("Finished checking content for" +
                   (sContext == null ? "" : " '" + sContext + "' and") +
                   " file extension '" +
                   sFileExt +
                   "'");
    }
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

    final IRepoStorageContent aContent = new RepoStorageContentFromReadableResource (aPayload);
    final ErrorList aErrorList = new ErrorList ();
    final boolean bValid = _isContentValid (null, _getFileExt (aPayload), aContent, aErrorList);
    if (!bValid)
      aErrorList.getAllErrors ().forEach (e -> LOGGER.error (e.getAsStringLocaleIndepdent ()));
    return EValidity.valueOf (bValid);
  }

  private void _uploadResource (@NonNull final DVRCoordinate aCoordinate,
                                @NonNull final IRepoStorageContent aContent,
                                @NonNull @Nonempty final String sFileExt) throws IOException
  {
    final ErrorList aErrorList = new ErrorList ();
    if (!_isContentValid (aCoordinate.getAsSingleID (), sFileExt, aContent, aErrorList))
    {
      // Log all collected errors
      aErrorList.getAllErrors ().forEach (e -> LOGGER.error (e.getAsStringLocaleIndepdent ()));
      throw new IllegalStateException ("Data for coordinate '" +
                                       aCoordinate.getAsSingleID () +
                                       "' does not match expectations of the file extension ('" +
                                       sFileExt +
                                       "') - see log for details");
    }

    // Create StorageKey
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, sFileExt);

    // This check is essential
    if (m_aRepo.exists (aKey))
      throw new RepoKeyAlreadyInUseException ("A resource with key '" + aKey.getPath () + "' is already in the Repo");

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
    // Create StorageKey (must start with '.')
    final String sRealFileExt = StringHelper.startsWith (sFileExt, '.') ? sFileExt : '.' + sFileExt;
    final RepoStorageKeyOfArtefact aKey = RepoStorageKeyOfArtefact.of (aCoordinate, sRealFileExt);

    // Delete from Repo
    if (m_aRepo.delete (aKey).isFailure ())
      LOGGER.warn ("Failed to delete " + aKey);
  }

  /**
   * @return The underlying repository storage with ToC support. Never <code>null</code>.
   */
  @NonNull
  public IRepoStorageWithToc getRepoStorageWithToc ()
  {
    return m_aRepo;
  }
}
