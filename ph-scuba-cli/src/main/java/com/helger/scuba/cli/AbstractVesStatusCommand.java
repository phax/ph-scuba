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

import java.util.concurrent.Callable;

import org.jspecify.annotations.NonNull;

import com.helger.base.log.ILogHandler;
import com.helger.base.state.EChange;
import com.helger.base.string.StringHelper;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.config.ConfiguredRepoStorage;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.scuba.phive.upload.PhiveUploader;
import com.helger.scuba.upload.ScubaUploader;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Base class of the commands that change the status of an existing VES.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
abstract class AbstractVesStatusCommand implements Callable <Integer>
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

  /** The VESID whose status is changed. */
  @Parameters (index = "0",
               paramLabel = "VESID",
               description = "The VESID whose status is changed: groupID:artifactID:version.")
  String m_sVESID;

  /** The author of the change. */
  @Option (names = "--author",
           required = true,
           paramLabel = "NAME",
           description = "The author of the change, as recorded in the status history.")
  String m_sAuthor;

  /**
   * Check if a VES exists in the repository.
   *
   * @param aRepo
   *        The repository to use. May not be <code>null</code>.
   * @param aVESID
   *        The VESID to check. May not be <code>null</code>.
   * @return <code>true</code> if it exists.
   */
  protected static boolean existsVES (@NonNull final IRepoStorageWithToc aRepo, @NonNull final DVRCoordinate aVESID)
  {
    return aRepo.exists (RepoStorageKeyOfArtefact.of (aVESID, VESLoader.FILE_EXT_VES));
  }

  /**
   * Check the command specific options. Called before the repository is opened.
   *
   * @throws ParameterException
   *         If the options are not valid
   */
  protected abstract void checkOptions ();

  /**
   * Change the status.
   *
   * @param aLog
   *        The log handler to use. May not be <code>null</code>.
   * @param aRepo
   *        The repository to use. May not be <code>null</code>.
   * @param aUploader
   *        The uploader to use. May not be <code>null</code>.
   * @param aVESID
   *        The VESID whose status is changed. May not be <code>null</code>.
   * @return <code>null</code> if a precondition is not met (already logged), otherwise if the
   *         status was changed or not.
   */
  protected abstract EChange changeStatus (@NonNull ILogHandler aLog,
                                           @NonNull IRepoStorageWithToc aRepo,
                                           @NonNull PhiveUploader aUploader,
                                           @NonNull DVRCoordinate aVESID);

  /**
   * @return The text describing the change, e.g. "deprecated".
   */
  @NonNull
  protected abstract String getChangeDescription ();

  /**
   * Change the status of the VES.
   *
   * @return {@link ScubaCli#EXIT_OK} if the status was changed or already had the requested values
   *         and {@link ScubaCli#EXIT_FAILURE} if the VES or a referenced VES does not exist.
   */
  public final Integer call ()
  {
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);

    final DVRCoordinate aVESID = CliHelper.parseStaticCoordinate (m_aSpec, m_sVESID);
    if (StringHelper.isBlank (m_sAuthor))
      throw new ParameterException (m_aSpec.commandLine (), "The author must not be empty");

    checkOptions ();

    try (final ConfiguredRepoStorage aConfiguredRepo = m_aRepoOptions.createRepoStorage (m_aSpec, aLog, true))
    {
      final IRepoStorageWithToc aRepo = aConfiguredRepo.getRepoStorage ();
      if (!existsVES (aRepo, aVESID))
      {
        aLog.error ("The VES '" + aVESID.getAsSingleID () + "' does not exist in the repository");
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);
      }

      final PhiveUploader aUploader = new PhiveUploader (new ScubaUploader (aRepo)).setAuthor (m_sAuthor.trim ());
      final EChange eChange = changeStatus (aLog, aRepo, aUploader, aVESID);
      if (eChange == null)
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);

      if (eChange.isChanged ())
        aLog.info ("The VES '" + aVESID.getAsSingleID () + "' was " + getChangeDescription ());
      else
        aLog.info ("The VES '" +
                   aVESID.getAsSingleID () +
                   "' was already " +
                   getChangeDescription () +
                   " - nothing changed");
      return Integer.valueOf (ScubaCli.EXIT_OK);
    }
  }
}
