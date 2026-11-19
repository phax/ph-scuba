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
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.Callable;

import com.helger.base.log.ILogHandler;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.config.ConfiguredRepoStorage;
import com.helger.diver.repo.impl.RepoStorageInMemory;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.scuba.validator.UploadContentValidator;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The command <code>validate</code>: check files like an upload does, without uploading them.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "validate",
          mixinStandardHelpOptions = true,
          description = { "Check files based on their file extension like an upload does, without uploading them.",
                          "A repository is only needed to resolve references (e.g. of VES and ZIP files); without one, an empty repository is used." })
public final class ValidateCommand implements Callable <Integer>
{
  /** The model of this command, for the output streams and usage errors. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** The logging options <code>-v</code> and <code>-q</code>. */
  @Mixin
  CliLogOptions m_aLogOptions;

  /** The optional repository selection. */
  @Mixin
  CliRepoOptions m_aRepoOptions;

  /** The files to check. */
  @Parameters (paramLabel = "FILE", arity = "1..*", description = "The files to check.")
  List <File> m_aFiles;

  /** Constructor. Called by picocli. */
  public ValidateCommand ()
  {}

  /**
   * Check all files and log the findings.
   *
   * @return {@link ScubaCli#EXIT_OK} if all files are valid and {@link ScubaCli#EXIT_FAILURE} if at
   *         least one is not or has an extension without a check.
   * @throws Exception
   *         On technical errors
   */
  public Integer call () throws Exception
  {
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    for (final File aFile : m_aFiles)
      CliHelper.checkInputFile (m_aSpec, aFile);

    try (final ConfiguredRepoStorage aConfiguredRepo = m_aRepoOptions.isRepositorySelected () ? m_aRepoOptions.createRepoStorage (m_aSpec,
                                                                                                                                  aLog,
                                                                                                                                  false)
                                                                                         : null)
    {
      final IRepoStorageWithToc aRepo = aConfiguredRepo != null ? aConfiguredRepo.getRepoStorage ()
                                                                : RepoStorageInMemory.createDefault ("empty",
                                                                                                     ERepoWritable.WITHOUT_WRITE,
                                                                                                     ERepoDeletable.WITHOUT_DELETE);
      final UploadContentValidator aValidator = new UploadContentValidator (aRepo);

      int nFailed = 0;
      for (final File aFile : m_aFiles)
      {
        final String sFileExt = CliHelper.getFileExt (aFile);
        if (sFileExt == null)
        {
          aLog.error ("The file '" + aFile.getPath () + "' has no file extension, so there is no check for it");
          nFailed++;
          continue;
        }
        if (!aValidator.hasValidatorForExtension (sFileExt))
        {
          aLog.error ("There is no check for the file extension '" + sFileExt + "' of '" + aFile.getPath () + "'");
          nFailed++;
          continue;
        }

        final ErrorList aErrorList = new ErrorList ();
        final boolean bValid;
        try (final InputStream aIS = Files.newInputStream (aFile.toPath ()))
        {
          bValid = aValidator.validateContent ("", sFileExt, aIS, aErrorList);
        }
        CliHelper.logErrors (aLog, aErrorList);
        if (bValid)
          aLog.info ("The file '" + aFile.getPath () + "' is valid");
        else
        {
          aLog.error ("The file '" + aFile.getPath () + "' is not valid");
          nFailed++;
        }
      }
      return Integer.valueOf (nFailed > 0 ? ScubaCli.EXIT_FAILURE : ScubaCli.EXIT_OK);
    }
  }
}
