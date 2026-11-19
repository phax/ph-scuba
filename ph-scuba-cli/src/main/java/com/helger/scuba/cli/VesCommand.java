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

import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * The command group <code>ves</code>: upload VES artefacts, manage their status and determine their
 * latest versions.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "ves",
          mixinStandardHelpOptions = true,
          description = "Upload VES artefacts, manage their status and determine their latest versions.",
          subcommands = { VesAddCommand.class,
                          VesAddStatusCommand.class,
                          VesDeprecateCommand.class,
                          VesValidityCommand.class,
                          VesLatestCommand.class,
                          VesLatestReleaseCommand.class,
                          VesLatestActiveCommand.class })
public final class VesCommand implements Callable <Integer>
{
  /** The model of this command, for the usage message. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** Constructor. Called by picocli. */
  public VesCommand ()
  {}

  /**
   * Called when no sub command is given: print the usage.
   *
   * @return Always {@link ScubaCli#EXIT_USAGE}.
   */
  public Integer call ()
  {
    m_aSpec.commandLine ().usage (m_aSpec.commandLine ().getErr ());
    return Integer.valueOf (ScubaCli.EXIT_USAGE);
  }
}
