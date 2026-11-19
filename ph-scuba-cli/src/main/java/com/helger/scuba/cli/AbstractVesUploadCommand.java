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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.log.ILogHandler;
import com.helger.base.state.EChange;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.config.ConfiguredRepoStorage;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.scuba.api.repo.ScubaException;
import com.helger.scuba.phive.upload.PhiveUploader;
import com.helger.scuba.upload.ScubaUploader;
import com.helger.scuba.upload.ScubaUploaderSettings;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Base class of the commands that upload a VES related file. The coordinate is taken from the file
 * content.
 *
 * @author Philip Helger
 * @param <JAXBTYPE>
 *        The JAXB type of the file content
 * @since 1.2.0
 */
abstract class AbstractVesUploadCommand <JAXBTYPE> implements Callable <Integer>
{
  /** The model of this command, for the output streams and usage errors. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The logging options <code>-v</code> and <code>-q</code>. */
  @Mixin
  CliLogOptions m_aLogOptions;

  /** The repository selection. */
  @Mixin
  CliRepoOptions m_aRepoOptions;

  /** The file to upload. */
  @Parameters (index = "0", paramLabel = "FILE", description = "The file to upload.")
  File m_aFile;

  /** The expected SHA-256 hash value of the file. */
  @Option (names = "--expected-sha256",
           paramLabel = "HEX",
           description = "The expected SHA-256 hash value of the file. The upload is refused if it does not match.")
  String m_sExpectedSha256;

  /** <code>true</code> to allow overwriting an existing artefact. */
  @Option (names = "--overwrite", description = "Allow overwriting an existing artefact.")
  boolean m_bOverwrite;

  /** <code>true</code> to only check without uploading. */
  @Option (names = "--dry-run",
           description = "Perform all checks, including the hash value and the conflict check, but do not upload.")
  boolean m_bDryRun;

  /**
   * @return The repository file extension of the uploaded artefact, including the leading dot.
   */
  @NonNull
  protected abstract String getFileExt ();

  /**
   * @return The name of the artefact type for messages.
   */
  @NonNull
  protected abstract String getArtefactName ();

  /**
   * Read the file.
   *
   * @param aFile
   *        The file to read. May not be <code>null</code>.
   * @return <code>null</code> if the file cannot be read.
   */
  @Nullable
  protected abstract JAXBTYPE readFile (@NonNull File aFile);

  /**
   * @param aObj
   *        The read file content. May not be <code>null</code>.
   * @return The group ID, artifact ID and version of the file content, in that order. Never
   *         <code>null</code>.
   */
  @NonNull
  protected abstract String [] getCoordinateParts (@NonNull JAXBTYPE aObj);

  /**
   * Upload the file content.
   *
   * @param aUploader
   *        The uploader to use. May not be <code>null</code>.
   * @param aObj
   *        The read file content. May not be <code>null</code>.
   * @return {@link EChange#CHANGED} if it was uploaded.
   * @throws IOException
   *         On IO error
   * @throws ScubaException
   *         If some scuba constraints don't match
   */
  @NonNull
  protected abstract EChange upload (@NonNull PhiveUploader aUploader,
                                     @NonNull JAXBTYPE aObj) throws IOException, ScubaException;

  /**
   * Check the file and upload it.
   *
   * @return {@link ScubaCli#EXIT_OK} if the file was uploaded (or would be uploaded in dry run
   *         mode), {@link ScubaCli#EXIT_FAILURE} if its hash value does not match or its content is
   *         not valid, and
   *         {@link ScubaCli#EXIT_CONFLICT} if the artefact already exists.
   * @throws Exception
   *         On technical errors
   */
  public final Integer call () throws Exception
  {
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    CliHelper.checkInputFile (m_aSpec, m_aFile);

    if (!CliHelper.checkSha256 (aLog, m_aFile, m_sExpectedSha256))
      return Integer.valueOf (ScubaCli.EXIT_FAILURE);

    final JAXBTYPE aObj = readFile (m_aFile);
    if (aObj == null)
    {
      aLog.error ("The file '" + m_aFile.getPath () + "' cannot be read as " + getArtefactName ());
      return Integer.valueOf (ScubaCli.EXIT_FAILURE);
    }

    final String [] aParts = getCoordinateParts (aObj);
    final DVRVersion aVersion = DVRVersion.parseOrNull (aParts[2]);
    if (aVersion == null || !aVersion.isStaticVersion ())
    {
      aLog.error ("The " + getArtefactName () + " in '" + m_aFile.getPath () + "' has no valid static version");
      return Integer.valueOf (ScubaCli.EXIT_FAILURE);
    }

    final DVRCoordinate aCoordinate = new DVRCoordinate (aParts[0], aParts[1], aVersion);
    final String sFileExt = getFileExt ();

    try (final ConfiguredRepoStorage aConfiguredRepo = m_aRepoOptions.createRepoStorage (m_aSpec, aLog, true))
    {
      final IRepoStorageWithToc aRepo = aConfiguredRepo.getRepoStorage ();
      final ScubaUploader aUploader = new ScubaUploader (aRepo,
                                                         new ScubaUploaderSettings ().setAllowOverwriteExisting (m_bOverwrite));

      if (aUploader.existsResource (aCoordinate, sFileExt) && !m_bOverwrite)
      {
        aLog.error ("The " +
                    getArtefactName () +
                    " '" +
                    aCoordinate.getAsSingleID () +
                    "' already exists in the repository - use --overwrite to replace it");
        return Integer.valueOf (ScubaCli.EXIT_CONFLICT);
      }

      final ErrorList aErrorList = new ErrorList ();
      final boolean bValid;
      try (final InputStream aIS = Files.newInputStream (m_aFile.toPath ()))
      {
        bValid = aUploader.getContentValidator ()
                          .validateContent (aCoordinate.getAsSingleID (), sFileExt, aIS, aErrorList);
      }

      CliHelper.logErrors (aLog, aErrorList);
      if (!bValid)
      {
        aLog.error ("The file '" + m_aFile.getPath () + "' is not valid - nothing was uploaded");
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);
      }

      if (m_bDryRun)
      {
        aLog.info ("Dry run: '" +
                   m_aFile.getPath () +
                   "' would be uploaded as " +
                   getArtefactName () +
                   " '" +
                   aCoordinate.getAsSingleID () +
                   "' to repository '" +
                   m_aRepoOptions.getRepoDisplayName (aRepo.getID ()) +
                   "'");
        return Integer.valueOf (ScubaCli.EXIT_OK);
      }

      if (upload (new PhiveUploader (aUploader), aObj).isUnchanged ())
      {
        aLog.error ("The " + getArtefactName () + " '" + aCoordinate.getAsSingleID () + "' could not be uploaded");
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);
      }
      aLog.info ("Uploaded '" +
                 m_aFile.getPath () +
                 "' as " +
                 getArtefactName () +
                 " '" +
                 aCoordinate.getAsSingleID () +
                 "' to repository '" +
                 m_aRepoOptions.getRepoDisplayName (aRepo.getID ()) +
                 "'");
      return Integer.valueOf (ScubaCli.EXIT_OK);
    }
  }
}
