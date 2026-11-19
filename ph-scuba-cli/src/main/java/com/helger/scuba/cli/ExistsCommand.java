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

import com.helger.base.log.ILogHandler;
import com.helger.base.string.StringHelper;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The command <code>exists</code>: check if an artefact exists in the repository.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "exists", mixinStandardHelpOptions = true, description = "Check if an artefact exists in the repository.")
public final class ExistsCommand implements Callable <Integer>
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

  /** The coordinate to check. */
  @Parameters (index = "0",
               paramLabel = "COORDINATE",
               description = "The coordinate to check: groupID:artifactID:version[:classifier].")
  String m_sCoordinate;

  /** The file extension to check. */
  @Parameters (index = "1", paramLabel = "EXTENSION", description = "The file extension, e.g. '.xsd' or 'xsd'.")
  String m_sFileExt;

  /** Constructor. Called by picocli. */
  public ExistsCommand ()
  {}

  /**
   * Check if the artefact exists.
   *
   * @return {@link ScubaCli#EXIT_OK} if it exists and {@link ScubaCli#EXIT_FAILURE} if not.
   */
  public Integer call ()
  {
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, false);
    final DVRCoordinate aCoordinate = CliHelper.parseStaticCoordinate (m_aSpec, m_sCoordinate);
    final String sFileExt = StringHelper.startsWith (m_sFileExt, '.') ? m_sFileExt : "." + m_sFileExt;

    final IRepoStorageWithToc aRepo = m_aRepoOptions.createRepoStorage (m_aSpec, aLog, false);
    if (aRepo.exists (RepoStorageKeyOfArtefact.of (aCoordinate, sFileExt)))
    {
      aLog.info ("The artefact '" +
                 aCoordinate.getAsSingleID () +
                 "' with file extension '" +
                 sFileExt +
                 "' exists");
      return Integer.valueOf (ScubaCli.EXIT_OK);
    }
    aLog.error ("The artefact '" +
                aCoordinate.getAsSingleID () +
                "' with file extension '" +
                sFileExt +
                "' does not exist");
    return Integer.valueOf (ScubaCli.EXIT_FAILURE);
  }
}
