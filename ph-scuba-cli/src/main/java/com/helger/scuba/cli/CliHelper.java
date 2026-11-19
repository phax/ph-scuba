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
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.concurrent.Immutable;
import com.helger.base.log.ILogHandler;
import com.helger.base.string.StringHelper;
import com.helger.base.string.StringHex;
import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.level.EErrorLevel;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.security.messagedigest.EMessageDigestAlgorithm;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.ParameterException;

/**
 * Helper methods shared by the commands.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Immutable
final class CliHelper
{
  private CliHelper ()
  {}

  /**
   * Log all provided errors and warnings with their level.
   *
   * @param aLog
   *        The log handler to use. May not be <code>null</code>.
   * @param aErrors
   *        The errors to log. May not be <code>null</code>.
   */
  static void logErrors (@NonNull final ILogHandler aLog, @NonNull final Iterable <? extends IError> aErrors)
  {
    for (final IError aError : aErrors)
      if (aError.getErrorLevel ().isGE (EErrorLevel.ERROR))
        aLog.error (aError.getAsStringLocaleIndepdent ());
      else
        aLog.warn (aError.getAsStringLocaleIndepdent ());
  }

  /**
   * Make sure the provided input file exists.
   *
   * @param aSpec
   *        The command, for usage errors. May not be <code>null</code>.
   * @param aFile
   *        The file to check. May not be <code>null</code>.
   * @throws ParameterException
   *         If the file does not exist.
   */
  static void checkInputFile (@NonNull final CommandSpec aSpec, @NonNull final File aFile)
  {
    if (!aFile.isFile ())
      throw new ParameterException (aSpec.commandLine (), "The file '" + aFile.getPath () + "' does not exist");
  }

  /**
   * Parse a DVR coordinate with a static version, as needed to write to the repository.
   *
   * @param aSpec
   *        The command, for usage errors. May not be <code>null</code>.
   * @param sCoordinate
   *        The coordinate in the form <code>groupID:artifactID:version[:classifier]</code>. May
   *        not be <code>null</code>.
   * @return The parsed coordinate. Never <code>null</code>.
   * @throws ParameterException
   *         If the coordinate cannot be parsed or uses a pseudo version.
   */
  @NonNull
  static DVRCoordinate parseStaticCoordinate (@NonNull final CommandSpec aSpec, @NonNull final String sCoordinate)
  {
    final DVRCoordinate ret = DVRCoordinate.parseOrNull (sCoordinate);
    if (ret == null)
      throw new ParameterException (aSpec.commandLine (),
                                    "'" +
                                                          sCoordinate +
                                                          "' is not a valid coordinate - expected 'groupID:artifactID:version[:classifier]'");
    if (!ret.getVersionObj ().isStaticVersion ())
      throw new ParameterException (aSpec.commandLine (),
                                    "The coordinate '" + sCoordinate + "' must use a static version, not a pseudo version");
    return ret;
  }

  /**
   * Parse a group ID and artifact ID.
   *
   * @param aSpec
   *        The command, for usage errors. May not be <code>null</code>.
   * @param sGroupArtifact
   *        The value in the form <code>groupID:artifactID</code>. May not be <code>null</code>.
   * @return An array with the group ID and the artifact ID. Never <code>null</code>.
   * @throws ParameterException
   *         If the value is not in the expected form.
   */
  @NonNull
  static String [] parseGroupArtifact (@NonNull final CommandSpec aSpec, @NonNull final String sGroupArtifact)
  {
    final String [] ret = StringHelper.getExplodedArray (':', sGroupArtifact, 3);
    if (ret.length != 2 || StringHelper.isEmpty (ret[0]) || StringHelper.isEmpty (ret[1]))
      throw new ParameterException (aSpec.commandLine (),
                                    "'" + sGroupArtifact + "' is not valid - expected 'groupID:artifactID'");
    return ret;
  }

  /**
   * Calculate the SHA-256 hash value of a file.
   *
   * @param aFile
   *        The file to read. May not be <code>null</code>.
   * @return The lower case hex encoded hash value. Never <code>null</code>.
   * @throws IOException
   *         If reading the file fails
   */
  @NonNull
  static String getSha256Hex (@NonNull final File aFile) throws IOException
  {
    final MessageDigest aMD = EMessageDigestAlgorithm.SHA_256.createMessageDigest ();
    try (final InputStream aIS = Files.newInputStream (aFile.toPath ()))
    {
      final byte [] aBuffer = new byte [16 * 1024];
      int nRead;
      while ((nRead = aIS.read (aBuffer)) > 0)
        aMD.update (aBuffer, 0, nRead);
    }
    return StringHex.getHexEncoded (aMD.digest ());
  }

  /**
   * Calculate the SHA-256 hash value of the input file, log it and compare it with the expected
   * value. This makes sure that the uploaded file is the one that was reviewed.
   *
   * @param aLog
   *        The log handler to use. May not be <code>null</code>.
   * @param aFile
   *        The input file. May not be <code>null</code>.
   * @param sExpectedSha256
   *        The expected hex encoded SHA-256 hash value. May be <code>null</code> to only log the
   *        hash value.
   * @return <code>true</code> if no hash value was expected or if it matches.
   * @throws IOException
   *         If reading the file fails
   */
  static boolean checkSha256 (@NonNull final ILogHandler aLog,
                              @NonNull final File aFile,
                              @Nullable final String sExpectedSha256) throws IOException
  {
    final String sSha256 = getSha256Hex (aFile);
    if (sExpectedSha256 == null)
    {
      aLog.info ("The SHA-256 hash value of '" + aFile.getPath () + "' is " + sSha256);
      return true;
    }

    if (!sSha256.equals (sExpectedSha256.trim ().toLowerCase (Locale.ROOT)))
    {
      aLog.error ("The SHA-256 hash value of '" +
                  aFile.getPath () +
                  "' is " +
                  sSha256 +
                  " but " +
                  sExpectedSha256 +
                  " was expected");
      return false;
    }
    aLog.info ("The SHA-256 hash value of '" + aFile.getPath () + "' matches the expected value " + sSha256);
    return true;
  }
}
