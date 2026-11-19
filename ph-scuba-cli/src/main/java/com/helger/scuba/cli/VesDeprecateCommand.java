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
import picocli.CommandLine.Option;

/**
 * The command <code>ves deprecate</code>: mark an existing VES as deprecated.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "deprecate",
          mixinStandardHelpOptions = true,
          description = "Mark an existing VES as deprecated, optionally with a reason and a replacement.")
public final class VesDeprecateCommand extends AbstractVesStatusCommand
{
  /** The optional deprecation reason. */
  @Option (names = "--reason", paramLabel = "TEXT", description = "The reason for the deprecation.")
  String m_sReason;

  /** The optional replacement VESID. */
  @Option (names = "--replacement",
           paramLabel = "VESID",
           description = "The VESID that replaces the deprecated one. It must exist in the repository.")
  String m_sReplacement;

  private DVRCoordinate m_aReplacement;

  /** Constructor. Called by picocli. */
  public VesDeprecateCommand ()
  {}

  @Override
  protected void checkOptions ()
  {
    if (m_sReplacement != null)
      m_aReplacement = CliHelper.parseStaticCoordinate (m_aSpec, m_sReplacement);
  }

  @Override
  protected EChange changeStatus (@NonNull final ILogHandler aLog,
                                  @NonNull final IRepoStorageWithToc aRepo,
                                  @NonNull final PhiveUploader aUploader,
                                  @NonNull final DVRCoordinate aVESID)
  {
    if (m_aReplacement != null && !existsVES (aRepo, m_aReplacement))
    {
      aLog.error ("The replacement VES '" + m_aReplacement.getAsSingleID () + "' does not exist in the repository");
      return null;
    }
    return aUploader.setVESDeprecated (aVESID, m_sReason, m_aReplacement);
  }

  @Override
  @NonNull
  protected String getChangeDescription ()
  {
    return "marked as deprecated";
  }
}
