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

import com.helger.base.log.ILogHandler;
import com.helger.base.state.EChange;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.scuba.phive.upload.PhiveUploader;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * The command <code>ves validity</code>: set the validity dates of an existing VES.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "validity",
          mixinStandardHelpOptions = true,
          description = "Set the validity dates of an existing VES. At least one of --from and --to is required.")
public final class VesValidityCommand extends AbstractVesStatusCommand
{
  /** The optional start of the validity. */
  @Option (names = "--from",
           paramLabel = "DATETIME",
           description = "The start of the validity, e.g. 2026-01-01T00:00:00Z.")
  OffsetDateTime m_aValidFrom;

  /** The optional end of the validity. */
  @Option (names = "--to", paramLabel = "DATETIME", description = "The end of the validity, e.g. 2026-12-31T23:59:59Z.")
  OffsetDateTime m_aValidTo;

  /** Constructor. Called by picocli. */
  public VesValidityCommand ()
  {}

  @Override
  protected void checkOptions ()
  {
    if (m_aValidFrom == null && m_aValidTo == null)
      throw new ParameterException (m_aSpec.commandLine (), "At least one of --from and --to is required");
    if (m_aValidFrom != null && m_aValidTo != null && m_aValidFrom.isAfter (m_aValidTo))
      throw new ParameterException (m_aSpec.commandLine (), "--from must not be after --to");
  }

  @Override
  protected EChange changeStatus (@NonNull final ILogHandler aLog,
                                  @NonNull final IRepoStorageWithToc aRepo,
                                  @NonNull final PhiveUploader aUploader,
                                  @NonNull final DVRCoordinate aVESID)
  {
    return aUploader.setVESValidityDate (aVESID, m_aValidFrom, m_aValidTo);
  }

  @Override
  @NonNull
  protected String getChangeDescription ()
  {
    return "updated with the provided validity dates";
  }
}
