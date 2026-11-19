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

import java.io.PrintWriter;
import java.util.concurrent.Callable;

import com.helger.base.log.ILogHandler;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.config.ConfiguredRepoStorage;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.diver.repo.toc.RepoToc;

import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * The command <code>versions</code>: print all versions of an artefact contained in the table of
 * contents of the repository. The coordinates are the only output on the standard output, one per
 * line in ascending version order, so that they can be used in scripts.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "versions",
          mixinStandardHelpOptions = true,
          description = "Print the coordinates of all versions of an artefact from the table of contents of the repository, in ascending order.")
public final class VersionsCommand implements Callable <Integer>
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

  /** The group ID and artifact ID of the artefact. */
  @Parameters (index = "0",
               paramLabel = "GROUP:ARTIFACT",
               description = "The group ID and artifact ID of the artefact.")
  String m_sGroupArtifact;

  /** Constructor. Called by picocli. */
  public VersionsCommand ()
  {}

  /**
   * Read the table of contents and print all versions.
   *
   * @return {@link ScubaCli#EXIT_OK} if at least one version was found and
   *         {@link ScubaCli#EXIT_FAILURE} if not.
   */
  public Integer call ()
  {
    // The standard output carries the result
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, true);
    final String [] aGA = CliHelper.parseGroupArtifact (m_aSpec, m_sGroupArtifact);

    try (final ConfiguredRepoStorage aConfiguredRepo = m_aRepoOptions.createRepoStorage (m_aSpec, aLog, false))
    {
      final IRepoStorageWithToc aRepo = aConfiguredRepo.getRepoStorage ();
      final RepoToc aToc = aRepo.readTocModel (aGA[0], aGA[1]);
      if (aToc == null || aToc.getVersionCount () == 0)
      {
        aLog.error ("The repository contains no version of '" + m_sGroupArtifact + "'");
        return Integer.valueOf (ScubaCli.EXIT_FAILURE);
      }

      final PrintWriter aOut = m_aSpec.commandLine ().getOut ();
      for (final DVRVersion aVersion : aToc.getAllVersionsOnly ())
        aOut.println (new DVRCoordinate (aGA[0], aGA[1], aVersion).getAsSingleID ());
      aOut.flush ();
      aLog.debug ("Found " + aToc.getVersionCount () + " version(s) of '" + m_sGroupArtifact + "'");
      return Integer.valueOf (ScubaCli.EXIT_OK);
    }
  }
}
