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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.log.ILogHandler;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.phive.ves.engine.load.VESLoaderPseudoVersionResolver;

import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

/**
 * Base class of the commands that determine a VES version based on a pseudo version. The resolved
 * VESID is the only output on the standard output, so that it can be used in scripts.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
abstract class AbstractVesResolveCommand implements Callable <Integer>
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

  /** The group ID and artifact ID of the VES. */
  @Parameters (index = "0", paramLabel = "GROUP:ARTIFACT", description = "The group ID and artifact ID of the VES.")
  String m_sGroupArtifact;

  /**
   * @return The name of the pseudo version, for messages.
   */
  @NonNull
  protected abstract String getPseudoVersionName ();

  /**
   * Resolve the version.
   *
   * @param aResolver
   *        The resolver to use. May not be <code>null</code>.
   * @param sGroupID
   *        The group ID. May not be <code>null</code>.
   * @param sArtifactID
   *        The artifact ID. May not be <code>null</code>.
   * @return <code>null</code> if no version matches.
   */
  @Nullable
  protected abstract DVRCoordinate resolve (@NonNull VESLoaderPseudoVersionResolver aResolver,
                                            @NonNull String sGroupID,
                                            @NonNull String sArtifactID);

  /**
   * Resolve the version and print the resulting VESID.
   *
   * @return {@link ScubaCli#EXIT_OK} if a version was found and {@link ScubaCli#EXIT_FAILURE} if
   *         not.
   */
  public final Integer call ()
  {
    // The standard output carries the result
    final ILogHandler aLog = m_aLogOptions.createLogHandler (m_aSpec, true);
    final String [] aGA = CliHelper.parseGroupArtifact (m_aSpec, m_sGroupArtifact);

    final IRepoStorageWithToc aRepo = m_aRepoOptions.createRepoStorage (m_aSpec, aLog, false);
    final DVRCoordinate aVESID = resolve (new VESLoaderPseudoVersionResolver (aRepo), aGA[0], aGA[1]);
    if (aVESID == null)
    {
      aLog.error ("No version of '" + m_sGroupArtifact + "' matches '" + getPseudoVersionName () + "'");
      return Integer.valueOf (ScubaCli.EXIT_FAILURE);
    }

    aLog.debug ("'" + m_sGroupArtifact + "' with '" + getPseudoVersionName () + "' resolves to '" + aVESID.getAsSingleID () + "'");
    final PrintWriter aOut = m_aSpec.commandLine ().getOut ();
    aOut.println (aVESID.getAsSingleID ());
    aOut.flush ();
    return Integer.valueOf (ScubaCli.EXIT_OK);
  }
}
