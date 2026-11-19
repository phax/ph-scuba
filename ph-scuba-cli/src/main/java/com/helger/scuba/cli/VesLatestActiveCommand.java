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

import java.time.OffsetDateTime;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.phive.api.config.PhivePseudoVersionRegistrarSPIImpl;
import com.helger.phive.ves.engine.load.VESLoaderPseudoVersionResolver;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The command <code>ves latest-active</code>: determine the latest version of a VES that is active
 * according to its status, including snapshots.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "latest-active",
          mixinStandardHelpOptions = true,
          description = { "Print the VESID of the latest version of a VES that is active according to its status, including snapshot versions.",
                          "A version without status is considered active." })
public final class VesLatestActiveCommand extends AbstractVesResolveCommand
{
  /** The point in time for which the status is checked. */
  @Option (names = "--at",
           paramLabel = "DATETIME",
           description = "The point in time for which the status is checked, e.g. 2026-06-01T00:00:00Z. Default: now.")
  OffsetDateTime m_aCheckDateTime;

  /** Constructor. Called by picocli. */
  public VesLatestActiveCommand ()
  {}

  @Override
  @NonNull
  protected String getPseudoVersionName ()
  {
    return PhivePseudoVersionRegistrarSPIImpl.LATEST_ACTIVE.getID ();
  }

  @Override
  @Nullable
  protected DVRCoordinate resolve (@NonNull final VESLoaderPseudoVersionResolver aResolver,
                                   @NonNull final String sGroupID,
                                   @NonNull final String sArtifactID)
  {
    return aResolver.getLatestActiveVersion (sGroupID, sArtifactID, null, m_aCheckDateTime);
  }
}
