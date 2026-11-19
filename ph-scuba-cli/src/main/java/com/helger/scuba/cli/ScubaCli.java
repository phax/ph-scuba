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

import com.helger.annotation.style.VisibleForTesting;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.config.ConfigFactory;
import com.helger.config.IConfig;
import com.helger.scuba.api.CScubaVersion;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

/**
 * Command line client of ph-scuba. Each function is a sub command. The client holds the ph-config
 * configuration ({@link IConfig}) used to resolve the secrets referenced by the repository
 * configuration, e.g. the <code>password-key</code> of an HTTP repository.
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
   * The product name and version, printed by <code>--version</code>, as header of the usage message
   * and at the start of each command
   */
  static final String HEADLINE = "ph-scuba " + CScubaVersion.BUILD_VERSION;

  /** The slf4j-simple system property with the default log level */
  private static final String SLF4J_SIMPLE_DEFAULT_LOG_LEVEL = "org.slf4j.simpleLogger.defaultLogLevel";

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
      return new String [] { HEADLINE };
    }
  }

  /** The model of the top level command, for the usage message. Injected by picocli. */
  @Spec
  CommandSpec m_aSpec;

  private final IConfig m_aConfig;

  /**
   * Constructor. Called by {@link #createCommandLine(IConfig)}.
   *
   * @param aConfig
   *        The configuration to resolve secrets from. May not be <code>null</code>.
   */
  public ScubaCli (@NonNull final IConfig aConfig)
  {
    ValueEnforcer.notNull (aConfig, "Config");
    m_aConfig = aConfig;
  }

  /**
   * @return The configuration to resolve secrets from. Never <code>null</code>.
   */
  @NonNull
  public IConfig getConfig ()
  {
    return m_aConfig;
  }

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
   * Set the headline as header of the usage message of the provided command and all its sub
   * commands. This cannot be done in the annotation, because the version is not a compile time
   * constant.
   *
   * @param aCmd
   *        The command line to modify. May not be <code>null</code>.
   */
  private static void _setHeaderRecursive (@NonNull final CommandLine aCmd)
  {
    aCmd.getCommandSpec ().usageMessage ().header (HEADLINE);
    for (final CommandLine aSubCmd : aCmd.getSubcommands ().values ())
      _setHeaderRecursive (aSubCmd);
  }

  /**
   * Create the picocli command line of this client. Used by {@link #main(String[])} and by tests
   * that run the client in-process.
   *
   * @param aConfig
   *        The configuration to resolve secrets from. May not be <code>null</code>.
   * @return A new command line for this client, with technical errors mapped to
   *         {@link #EXIT_ERROR}. Never <code>null</code>.
   */
  @NonNull
  public static CommandLine createCommandLine (@NonNull final IConfig aConfig)
  {
    final CommandLine ret = new CommandLine (new ScubaCli (aConfig));
    _setHeaderRecursive (ret);
    ret.setExecutionExceptionHandler ( (ex, aCmd, aParseResult) -> {
      aCmd.getErr ().println ("[error] " + ex.getClass ().getSimpleName () + ": " + ex.getMessage ());
      // The parse result of the (sub) command that failed contains its -v option
      if (aCmd.getParseResult ().hasMatchedOption ("--verbose"))
        ex.printStackTrace (aCmd.getErr ());
      aCmd.getErr ().flush ();
      return EXIT_ERROR;
    });
    return ret;
  }

  /**
   * Check if verbose output is requested, before the command line is parsed. This is needed,
   * because the log level of the libraries must be set before the first logger is created.
   *
   * @param aArgs
   *        The command line arguments. May not be <code>null</code>.
   * @return <code>true</code> if <code>-v</code> or <code>--verbose</code> is contained, also as
   *         part of combined short options like <code>-qv</code>.
   */
  @VisibleForTesting
  static boolean isVerboseRequested (@NonNull final String [] aArgs)
  {
    for (final String sArg : aArgs)
    {
      // End of options
      if ("--".equals (sArg))
        break;
      if ("--verbose".equals (sArg))
        return true;
      // The short options of all commands are -h, -q, -v and -V
      if (sArg.matches ("-[hqvV]+") && sArg.indexOf ('v') > 0)
        return true;
    }
    return false;
  }

  /**
   * Entry point of the executable JAR. Exits the JVM with the exit code of the command.
   *
   * @param aArgs
   *        The command line arguments. May not be <code>null</code>.
   */
  public static void main (final String [] aArgs)
  {
    // In verbose mode also show the info messages of the libraries (content checks, repository
    // access). Must happen before anything creates a logger; an explicit -D setting wins.
    if (isVerboseRequested (aArgs) && System.getProperty (SLF4J_SIMPLE_DEFAULT_LOG_LEVEL) == null)
      System.setProperty (SLF4J_SIMPLE_DEFAULT_LOG_LEVEL, "info");

    // System properties, environment variables and application properties files
    System.exit (createCommandLine (ConfigFactory.getDefaultConfig ()).execute (aArgs));
  }
}
