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

import java.io.File;

import org.jspecify.annotations.NonNull;

import com.helger.base.log.ILogHandler;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.config.IConfig;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.repo.config.ConfiguredRepoStorage;
import com.helger.diver.repo.config.ERepoTopTocType;
import com.helger.diver.repo.config.RepoStorageConfig;
import com.helger.diver.repo.config.RepoStorageConfigReader;
import com.helger.diver.repo.config.RepoStorageDefinition;
import com.helger.diver.repo.config.RepoStorageFactoryLocalFileSystem;
import com.helger.diver.repo.config.RepoStorageFactoryRegistry;
import com.helger.diver.repo.http.RepoStorageFactoryHttp;
import com.helger.diver.repo.s3.RepoStorageFactoryS3;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

/**
 * The options to select the repository, shared by all commands. Either <code>--basedir</code> (a
 * local file system repository) or <code>--config</code> together with <code>--repo</code> (a
 * repository of a ph-diver repository configuration file of type <code>localfs</code>,
 * <code>http</code> or <code>aws-s3</code>) must be used.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
final class CliRepoOptions
{
  /** The repository ID used for the repository given by <code>--basedir</code>. */
  static final String BASEDIR_REPO_ID = "basedir";

  /** The base directory of a local file system repository. */
  @Option (names = "--basedir",
           paramLabel = "DIR",
           description = "The existing base directory of a local file system repository (type 'localfs'). Cannot be combined with --config.")
  File m_aBaseDir;

  /** The top-level ToC type of the repository given by <code>--basedir</code>. */
  @Option (names = "--toptoc",
           paramLabel = "TYPE",
           description = "The top-level ToC type of the repository given by --basedir: 'none' or 'diver-xml'. Default: 'none'.")
  String m_sTopTocType;

  /** The ph-diver repository configuration file. */
  @Option (names = "--config",
           paramLabel = "FILE",
           description = "A ph-diver repository configuration file. Requires --repo.")
  File m_aConfigFile;

  /** The ID of the repository in the configuration file. */
  @Option (names = "--repo", paramLabel = "ID", description = "The ID of the repository in the file given by --config.")
  String m_sRepoID;

  /** Constructor. Called by picocli. */
  CliRepoOptions ()
  {}

  /**
   * @return <code>true</code> if a repository was selected on the command line.
   */
  boolean isRepositorySelected ()
  {
    return m_aBaseDir != null || m_aConfigFile != null || m_sRepoID != null;
  }

  @NonNull
  private RepoStorageDefinition _getBaseDirDefinition (@NonNull final CommandLine aCL, final boolean bWritable)
  {
    if (m_aConfigFile != null || m_sRepoID != null)
      throw new ParameterException (aCL, "--basedir cannot be combined with --config or --repo");
    // Don't silently create a new repository on a typo
    if (!m_aBaseDir.isDirectory ())
      throw new ParameterException (aCL,
                                    "The repository base directory '" + m_aBaseDir.getPath () + "' does not exist");

    ERepoTopTocType eTopTocType = ERepoTopTocType.NONE;
    if (m_sTopTocType != null)
    {
      eTopTocType = ERepoTopTocType.getFromIDOrNull (m_sTopTocType);
      if (eTopTocType == null)
        throw new ParameterException (aCL,
                                      "Unsupported top-level ToC type '" +
                                           m_sTopTocType +
                                           "' - use 'none' or 'diver-xml'");
    }

    return RepoStorageDefinition.builder ()
                                .id (BASEDIR_REPO_ID)
                                .type (RepoStorageFactoryLocalFileSystem.TYPE)
                                .writable (bWritable)
                                .deletable (false)
                                .verifyHash (true)
                                .topTocType (eTopTocType)
                                .property (RepoStorageFactoryLocalFileSystem.PROPERTY_BASEDIR,
                                           m_aBaseDir.getAbsolutePath ())
                                .build ();
  }

  @NonNull
  private RepoStorageDefinition _getConfigDefinition (@NonNull final CommandLine aCL,
                                                      @NonNull final ILogHandler aLog,
                                                      final boolean bWritable)
  {
    if (m_sTopTocType != null)
      throw new ParameterException (aCL, "--toptoc can only be used with --basedir");
    if (m_sRepoID == null)
      throw new ParameterException (aCL, "--config requires --repo");
    if (!m_aConfigFile.isFile ())
      throw new ParameterException (aCL,
                                    "The repository configuration file '" + m_aConfigFile.getPath () + "' does not exist");

    final ErrorList aErrorList = new ErrorList ();
    final RepoStorageConfig aConfig = RepoStorageConfigReader.read (m_aConfigFile, aErrorList);
    CliHelper.logErrors (aLog, aErrorList);
    if (aConfig == null || aErrorList.containsAtLeastOneError ())
      throw new IllegalStateException ("Failed to read the repository configuration '" +
                                       m_aConfigFile.getPath () +
                                       "' - see the messages above");

    final RepoStorageDefinition aDefinition = aConfig.getDefinitionOfID (m_sRepoID);
    if (aDefinition == null)
      throw new ParameterException (aCL,
                                    "The repository configuration '" +
                                         m_aConfigFile.getPath () +
                                         "' contains no repository with ID '" +
                                         m_sRepoID +
                                         "'");
    if (bWritable && !aDefinition.getWritable ().isWriteEnabled ())
      throw new ParameterException (aCL,
                                    "The repository '" +
                                         m_sRepoID +
                                         "' is not writable - set writable=\"true\" in the repository configuration");
    return aDefinition;
  }

  /**
   * @param aConfig
   *        The configuration to resolve secrets from. May not be <code>null</code>.
   * @return A new factory registry with all repository types supported by the command line client.
   */
  @NonNull
  static RepoStorageFactoryRegistry createFactoryRegistry (@NonNull final IConfig aConfig)
  {
    final RepoStorageFactoryRegistry ret = RepoStorageFactoryRegistry.createDefault ();
    ret.registerFactory (new RepoStorageFactoryHttp (aConfig));
    ret.registerFactory (new RepoStorageFactoryS3 ());
    return ret;
  }

  /**
   * @param aSpec
   *        The command. May not be <code>null</code>.
   * @return The configuration of the {@link ScubaCli} root command. Never <code>null</code>.
   */
  @NonNull
  private static IConfig _getConfig (@NonNull final CommandSpec aSpec)
  {
    if (aSpec.root ().userObject () instanceof final ScubaCli aCli)
      return aCli.getConfig ();
    throw new IllegalStateException ("The root command is not a ScubaCli");
  }

  /**
   * Create the selected repository storage. The caller must close it.
   *
   * @param aSpec
   *        The command, for usage errors. May not be <code>null</code>.
   * @param aLog
   *        The log handler for the messages of reading the configuration. May not be
   *        <code>null</code>.
   * @param bWritable
   *        <code>true</code> if the command writes to the repository.
   * @return The configured repository storage. Never <code>null</code>.
   * @throws ParameterException
   *         If the repository options are not valid.
   * @throws IllegalStateException
   *         If the configuration cannot be read or the repository cannot be created.
   */
  @NonNull
  ConfiguredRepoStorage createRepoStorage (@NonNull final CommandSpec aSpec,
                                         @NonNull final ILogHandler aLog,
                                         final boolean bWritable)
  {
    final CommandLine aCL = aSpec.commandLine ();
    final RepoStorageDefinition aDefinition;
    if (m_aBaseDir != null)
      aDefinition = _getBaseDirDefinition (aCL, bWritable);
    else
      if (m_aConfigFile != null)
        aDefinition = _getConfigDefinition (aCL, aLog, bWritable);
      else
        throw new ParameterException (aCL, "Either --basedir or --config together with --repo must be provided");

    // Only create the selected repository, not all of the configuration
    final ErrorList aErrorList = new ErrorList ();
    final ICommonsList <ConfiguredRepoStorage> aRepos = new RepoStorageConfig (new CommonsArrayList <> (aDefinition)).createAllRepoStorages (createFactoryRegistry (_getConfig (aSpec)),
                                                                                                                                             aErrorList);
    CliHelper.logErrors (aLog, aErrorList);
    if (aRepos.isEmpty () || aErrorList.containsAtLeastOneError ())
    {
      aRepos.forEach (ConfiguredRepoStorage::close);
      throw new IllegalStateException ("Failed to create the repository '" +
                                       aDefinition.getID () +
                                       "' - see the messages above");
    }
    return aRepos.getFirstOrNull ();
  }
}
