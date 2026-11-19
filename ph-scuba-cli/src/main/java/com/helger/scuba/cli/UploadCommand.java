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
import java.util.concurrent.Callable;

import com.helger.base.log.ILogHandler;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.config.ConfiguredRepoStorage;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.io.resource.FileSystemResource;
import com.helger.scuba.upload.ScubaUploader;
import com.helger.scuba.upload.ScubaUploaderSettings;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The command <code>upload</code>: check a file and upload it to the repository.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "upload",
          mixinStandardHelpOptions = true,
          description = "Check a file based on its file extension and upload it to the repository.")
public final class UploadCommand implements Callable <Integer>
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

  /** The coordinate to upload to. */
  @Parameters (index = "0",
               paramLabel = "COORDINATE",
               description = "The coordinate to upload to: groupID:artifactID:version[:classifier].")
  String m_sCoordinate;

  /** The file to upload. */
  @Parameters (index = "1", paramLabel = "FILE", description = "The file to upload. Its extension selects the check.")
  File m_aFile;

  /** The expected SHA-256 hash value of the file. */
  @Option (names = "--expected-sha256",
           paramLabel = "HEX",
           description = "The expected SHA-256 hash value of the file. The upload is refused if it does not match.")
  String m_sExpectedSha256;

  /** <code>true</code> to allow overwriting an existing artefact. */
  @Option (names = "--overwrite", description = "Allow overwriting an existing artefact.")
  boolean m_bOverwrite;

  /** <code>true</code> to allow files without a check for their extension. */
  @Option (names = "--allow-unknown-extension",
           description = "Allow uploading files with an extension for which no check exists, without checking them.")
  boolean m_bAllowUnknownExtension;

  /** <code>true</code> to only check without uploading. */
  @Option (names = "--dry-run",
           description = "Perform all checks, including the hash value and the conflict check, but do not upload.")
  boolean m_bDryRun;

  /** Constructor. Called by picocli. */
  public UploadCommand ()
  {}

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
  public Integer call () throws Exception
  {
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    final DVRCoordinate aCoordinate = CliHelper.parseStaticCoordinate (m_aSpec, m_sCoordinate);
    CliHelper.checkInputFile (m_aSpec, m_aFile);
    final String sFileExt = CliHelper.getFileExt (m_aFile);
    if (sFileExt == null)
      throw new ParameterException (m_aSpec.commandLine (),
                                    "The file '" +
                                                          m_aFile.getPath () +
                                                          "' has no file extension - it is needed to select the check and is part of the repository key");

    if (!CliHelper.checkSha256 (aLog, m_aFile, m_sExpectedSha256))
      return Integer.valueOf (ScubaCli.EXIT_FAILURE);

    try (final ConfiguredRepoStorage aConfiguredRepo = m_aRepoOptions.createRepoStorage (m_aSpec, aLog, true))
    {
      final IRepoStorageWithToc aRepo = aConfiguredRepo.getRepoStorage ();
      final ScubaUploader aUploader = new ScubaUploader (aRepo,
                                                         new ScubaUploaderSettings ().setAllowOverwriteExisting (m_bOverwrite)
                                                                                     .setAllowUploadWithUnknownExtension (m_bAllowUnknownExtension));

      if (!aUploader.getContentValidator ().hasValidatorForExtension (sFileExt) && !m_bAllowUnknownExtension)
      {
        aLog.error ("There is no check for the file extension '" +
                    sFileExt +
                    "' - use --allow-unknown-extension to upload it without checking");
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);
      }

      if (aUploader.existsResource (aCoordinate, sFileExt) && !m_bOverwrite)
      {
        aLog.error ("The artefact '" +
                    aCoordinate.getAsSingleID () +
                    "' with file extension '" +
                    sFileExt +
                    "' already exists in the repository - use --overwrite to replace it");
        return Integer.valueOf (ScubaCli.EXIT_CONFLICT);
      }

      final FileSystemResource aPayload = new FileSystemResource (m_aFile);
      if (aUploader.isValidResource (aPayload).isInvalid ())
      {
        aLog.error ("The file '" + m_aFile.getPath () + "' is not valid - nothing was uploaded");
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);
      }

      if (m_bDryRun)
      {
        aLog.info ("Dry run: '" +
                   m_aFile.getPath () +
                   "' would be uploaded as '" +
                   aCoordinate.getAsSingleID () +
                   "' with file extension '" +
                   sFileExt +
                   "' to repository '" +
                   aRepo.getID () +
                   "'");
        return Integer.valueOf (ScubaCli.EXIT_OK);
      }

      aUploader.addResource (aCoordinate, aPayload);
      aLog.info ("Uploaded '" +
                 m_aFile.getPath () +
                 "' as '" +
                 aCoordinate.getAsSingleID () +
                 "' with file extension '" +
                 sFileExt +
                 "' to repository '" +
                 aRepo.getID () +
                 "'");
      return Integer.valueOf (ScubaCli.EXIT_OK);
    }
  }
}
