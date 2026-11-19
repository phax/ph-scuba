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
package com.helger.scuba.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.annotation.concurrent.GuardedBy;
import com.helger.annotation.style.IsSPIImplementation;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.concurrent.SimpleReadWriteLock;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.state.EContinue;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.io.file.FilenameHelper;
import com.helger.io.zip.IZipWalkerCallback;
import com.helger.io.zip.ZipWalkResult;
import com.helger.io.zip.ZipWalker;
import com.helger.io.zip.ZipWalkerSettings;
import com.helger.scuba.api.spi.IUploadContentValidatorRegistry;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;

/**
 * Content validator for ZIP files (.zip). Verifies that the content is a ZIP archive, that all ZIP
 * entries are readable and recursively validates entry content based on file extension using the
 * content validator registry. The archive is walked with the {@link ZipWalker}, which handles
 * nested ZIP archives itself, so that the limits of the {@link ZipWalkerSettings} (nesting depth,
 * number of entries, total uncompressed size) apply to the archive as a whole. The limits can be
 * changed globally with {@link #setSettings(ZipWalkerSettings)}.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public final class ZipContentValidator implements IUploadContentValidatorSPI
{
  /** The file extension handled by this validator. */
  public static final String FILE_EXT_ZIP = ".zip";

  private static final Logger LOGGER = LoggerFactory.getLogger (ZipContentValidator.class);
  private static final SimpleReadWriteLock RW_LOCK = new SimpleReadWriteLock ();
  @GuardedBy ("RW_LOCK")
  private static ZipWalkerSettings s_aSettings = new ZipWalkerSettings ();

  private IUploadContentValidatorRegistry m_aRegistry;

  /** Default constructor. */
  public ZipContentValidator ()
  {}

  /**
   * @return A copy of the limits applied when checking ZIP archives. Never <code>null</code>.
   *         Defaults to the defaults of {@link ZipWalkerSettings}.
   * @since 1.2.0
   */
  @NonNull
  @ReturnsMutableCopy
  public static ZipWalkerSettings getSettings ()
  {
    return RW_LOCK.readLockedGet (s_aSettings::getClone);
  }

  /**
   * Set the limits applied when checking ZIP archives. This applies to all instances.
   *
   * @param aSettings
   *        The settings to use. They are copied. May not be <code>null</code>.
   * @since 1.2.0
   */
  public static void setSettings (@NonNull final ZipWalkerSettings aSettings)
  {
    ValueEnforcer.notNull (aSettings, "Settings");
    final ZipWalkerSettings aCopy = aSettings.getClone ();
    RW_LOCK.writeLocked ( () -> s_aSettings = aCopy);
  }

  @NonNull
  public ICommonsSet <String> getSupportedFileExtensions ()
  {
    return new CommonsHashSet <> (FILE_EXT_ZIP);
  }

  @Override
  public void initContentValidatorRegistry (@NonNull final IUploadContentValidatorRegistry aRegistry)
  {
    m_aRegistry = aRegistry;
  }

  @NonNull
  private static String _getFileExt (@NonNull final String sPath)
  {
    return "." + FilenameHelper.getExtension (sPath).toLowerCase (Locale.ROOT);
  }

  @Nullable
  private static String _getErrorText (@NonNull final ZipWalkResult aResult,
                                       @NonNull final ZipWalkerSettings aSettings)
  {
    return switch (aResult.getResult ())
    {
      case NOT_A_ZIP -> "The content is not a ZIP archive";
      case BROKEN -> "Failed to read the ZIP archive";
      case MAX_ENTRY_COUNT_EXCEEDED -> "The ZIP archive contains more than " +
                                       aSettings.getMaxEntryCount () +
                                       " entries";
      case MAX_TOTAL_UNCOMPRESSED_BYTES_EXCEEDED -> "The total uncompressed size of the ZIP archive exceeds " +
                                                    aSettings.getMaxTotalUncompressedBytes () +
                                                    " bytes";
      case MAX_NESTING_DEPTH_EXCEEDED -> "The nested ZIP archive exceeds the maximum nesting depth of " +
                                         aSettings.getMaxNestingDepth ();
      // The errors were added by the content validator of the entry
      case SUCCESS, STOPPED_BY_CALLBACK -> null;
    };
  }

  public boolean isValidContent (@NonNull final String sFileExt,
                                 @NonNull final InputStream aIS,
                                 @NonNull final ErrorList aErrorList) throws IOException
  {
    // Snapshot of the global settings, so that the walk and the error texts use the same limits
    final ZipWalkerSettings aSettings = getSettings ();
    final IZipWalkerCallback aCallback = new IZipWalkerCallback ()
    {
      @Override
      public boolean isNestedArchive (@NonNull final String sPath, @NonNull final ZipEntry aEntry)
      {
        // The walker descends into nested ZIPs itself instead of dispatching them to this validator
        // again, so that its limits apply to the archive as a whole
        return FILE_EXT_ZIP.equals (_getFileExt (sPath));
      }

      @NonNull
      public EContinue onEntry (@NonNull final String sPath,
                                @NonNull final ZipEntry aEntry,
                                final int nDepth,
                                @NonNull final InputStream aEntryIS) throws IOException
      {
        final String sEntryExt = _getFileExt (sPath);
        if (m_aRegistry != null && m_aRegistry.hasValidatorForExtension (sEntryExt))
        {
          // Validate the entry recursively with the validator for its extension. An invalid entry
          // stops the walk.
          LOGGER.debug ("Recursively validating ZIP entry '" + sPath + "'");
          return EContinue.valueOf (m_aRegistry.validateContent ("zip:" + sPath, sEntryExt, aEntryIS, aErrorList));
        }

        // No specific validator - the walker reads the entry, so that a broken entry is detected
        return EContinue.CONTINUE;
      }
    };

    final ZipWalkResult aResult = ZipWalker.walk (aIS, aSettings, aCallback);
    final String sErrorText = _getErrorText (aResult, aSettings);
    if (sErrorText != null)
      // Prefix with the path of the failing entry, if the walker knows it
      aErrorList.add (SingleError.builderError ()
                                 .errorText (aResult.getEntryPath () == null ? sErrorText
                                                                             : "zip:" +
                                                                               aResult.getEntryPath () +
                                                                               ": " +
                                                                               sErrorText)
                                 .linkedException (aResult.getException ())
                                 .build ());
    return aResult.isSuccess ();
  }
}
