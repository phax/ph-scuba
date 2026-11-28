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

import com.helger.base.log.ILogHandler;
import com.helger.base.state.EChange;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.scuba.phive.upload.PhiveUploader;

import picocli.CommandLine.Command;

/**
 * The command <code>ves undeprecate</code>: revert the deprecation of an existing VES.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "undeprecate",
          mixinStandardHelpOptions = true,
          description = "Revert the deprecation of an existing VES. The deprecation reason and the replacement are removed.")
public final class VesUndeprecateCommand extends AbstractVesStatusCommand
{
  /** Constructor. Called by picocli. */
  public VesUndeprecateCommand ()
  {}

  @Override
  protected void checkOptions ()
  {
    // No command specific options
  }

  @Override
  protected EChange changeStatus (@NonNull final ILogHandler aLog,
                                  @NonNull final IRepoStorageWithToc aRepo,
                                  @NonNull final PhiveUploader aUploader,
                                  @NonNull final DVRCoordinate aVESID)
  {
    return aUploader.setVESNotDeprecated (aVESID);
  }

  @Override
  @NonNull
  protected String getChangeDescription ()
  {
    return "undeprecated";
  }
}
