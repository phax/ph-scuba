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

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRPseudoVersionRegistry;
import com.helger.phive.ves.engine.load.VESLoaderPseudoVersionResolver;

import picocli.CommandLine.Command;

/**
 * The command <code>ves latest-release</code>: determine the latest release version of a VES,
 * excluding snapshots.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "latest-release",
          mixinStandardHelpOptions = true,
          description = "Print the VESID of the latest release version of a VES, excluding snapshot versions.")
public final class VesLatestReleaseCommand extends AbstractVesResolveCommand
{
  /** Constructor. Called by picocli. */
  public VesLatestReleaseCommand ()
  {}

  @Override
  @NonNull
  protected String getPseudoVersionName ()
  {
    return DVRPseudoVersionRegistry.LATEST_RELEASE.getID ();
  }

  @Override
  @Nullable
  protected DVRCoordinate resolve (@NonNull final VESLoaderPseudoVersionResolver aResolver,
                                   @NonNull final String sGroupID,
                                   @NonNull final String sArtifactID)
  {
    return aResolver.getLatestReleaseVersion (sGroupID, sArtifactID, null);
  }
}
