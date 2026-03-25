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
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.io.stream.NonClosingInputStream;
import com.helger.base.io.stream.NullOutputStream;
import com.helger.base.io.stream.StreamHelper;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.io.file.FilenameHelper;
import com.helger.scuba.api.spi.IUploadContentValidatorRegistry;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;

/**
 * Content validator for ZIP files (.zip). Verifies all ZIP entries are readable and recursively
 * validates entry content based on file extension using the content validator registry.
 *
 * @author Philip Helger
 */
public final class ZipContentValidator implements IUploadContentValidatorSPI
{
  public static final String FILE_EXT_ZIP = ".zip";

  private static final Logger LOGGER = LoggerFactory.getLogger (ZipContentValidator.class);

  private IUploadContentValidatorRegistry m_aRegistry;

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

  public boolean isValidContent (@NonNull final String sFileExt,
                                 @NonNull final InputStream aIS,
                                 @NonNull final ErrorList aErrorList) throws IOException
  {
    try (final ZipInputStream aZIPIS = new ZipInputStream (aIS))
    {
      ZipEntry aZipEntry;
      while ((aZipEntry = aZIPIS.getNextEntry ()) != null)
      {
        if (!aZipEntry.isDirectory ())
        {
          final String sEntryName = aZipEntry.getName ();
          final String sEntryExt = "." + FilenameHelper.getExtension (sEntryName);

          // Try recursive validation if a validator exists for this entry's extension
          if (m_aRegistry != null && m_aRegistry.hasValidatorForExtension (sEntryExt))
          {
            LOGGER.debug ("Recursively validating ZIP entry '" + sEntryName + "'");

            // Use NonClosingInputStream so the ZipInputStream is not closed by the validator
            if (!m_aRegistry.validateContent (".zip / " + sEntryName,
                                              sEntryExt,
                                              new NonClosingInputStream (aZIPIS),
                                              aErrorList))
              return false;
          }
          else
          {
            // No specific validator - just verify the entry is readable
            if (StreamHelper.copyByteStream ()
                            .from (aZIPIS)
                            .closeFrom (false)
                            .to (new NullOutputStream ())
                            .closeTo (false)
                            .build ()
                            .isFailure ())
            {
              aErrorList.add (SingleError.builderError ()
                                         .errorText ("Failed to read ZIP entry '" + sEntryName + "'")
                                         .build ());
              return false;
            }
          }
        }
      }
    }
    return true;
  }
}
