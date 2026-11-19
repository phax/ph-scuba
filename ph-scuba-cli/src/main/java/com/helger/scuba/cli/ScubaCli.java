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

import com.helger.base.rt.NonBlockingProperties;
import com.helger.base.rt.PropertiesHelper;
import com.helger.io.resource.ClassPathResource;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command line client of ph-scuba. Each function is a sub command.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "ph-scuba",
          mixinStandardHelpOptions = true,
          versionProvider = ScubaCli.VersionProvider.class,
          description = "Check and upload artefacts to a ph-diver repository and manage the status of VES artefacts.",
          subcommands = { UploadCommand.class,
                          ValidateCommand.class,
                          ExistsCommand.class,
                          VesCommand.class,
                          HelpCommand.class })
public final class ScubaCli implements Callable <Integer>
{
  /** Exit code: success */
  public static final int EXIT_OK = 0;
  /**
   * Exit code: an expectation was not met (invalid content, hash value mismatch of the input, not
   * found)
   */
  public static final int EXIT_FAILURE = 1;
  /** Exit code: invalid command line */
  public static final int EXIT_USAGE = CommandLine.ExitCode.USAGE;
  /** Exit code: technical error (I/O, invalid repository configuration, failed write) */
  public static final int EXIT_ERROR = 3;
  /** Exit code: the artefact already exists in the repository and overwriting is not enabled */
  public static final int EXIT_CONFLICT = 4;

  /**
   * Provides the version for <code>--version</code>.
   */
  public static final class VersionProvider implements CommandLine.IVersionProvider
  {
    /** Create a new version provider. Called by picocli. */
    public VersionProvider ()
    {}

    @NonNull
    public String [] getVersion ()
    {
      String sVersion = null;
      final NonBlockingProperties p = PropertiesHelper.loadProperties (ClassPathResource.getInputStream ("ph-scuba-cli-version.properties",
                                                                                                         ScubaCli.class.getClassLoader ()));
      if (p != null)
        sVersion = p.get ("version");
      return new String [] { "ph-scuba " + (sVersion != null ? sVersion : "undefined") };
    }
  }

  /** The model of the top level command, for the usage message. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  /** Constructor. Called by {@link #createCommandLine()}. */
  public ScubaCli ()
  {}

  /**
   * Called when no command is given: print the usage.
   *
   * @return Always {@link #EXIT_USAGE}.
   */
  public Integer call ()
  {
    // No sub command given
    m_aSpec.commandLine ().usage (m_aSpec.commandLine ().getErr ());
    return Integer.valueOf (EXIT_USAGE);
  }

  /**
   * Create the picocli command line of this client. Used by {@link #main(String[])} and by tests
   * that run the client in-process.
   *
   * @return A new command line for this client, with technical errors mapped to
   *         {@link #EXIT_ERROR}. Never <code>null</code>.
   */
  @NonNull
  public static CommandLine createCommandLine ()
  {
    final CommandLine ret = new CommandLine (new ScubaCli ());
    ret.setExecutionExceptionHandler ( (ex, aCmd, aParseResult) -> {
      aCmd.getErr ().println ("[error] " + ex.getClass ().getSimpleName () + ": " + ex.getMessage ());
      aCmd.getErr ().flush ();
      return EXIT_ERROR;
    });
    return ret;
  }

  /**
   * Entry point of the executable JAR. Exits the JVM with the exit code of the command.
   *
   * @param aArgs
   *        The command line arguments. May not be <code>null</code>.
   */
  public static void main (final String [] aArgs)
  {
    System.exit (createCommandLine ().execute (aArgs));
  }
}
