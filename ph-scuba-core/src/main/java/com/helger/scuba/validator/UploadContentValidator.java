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
import java.util.ServiceLoader;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.enforce.ValueEnforcer;
import com.helger.collection.commons.CommonsArrayList;
import com.helger.collection.commons.ICommonsList;
import com.helger.diagnostics.error.IError;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.scuba.api.spi.IUploadContentValidatorRegistry;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;

/**
 * Central content validation dispatcher. Loads all {@link IUploadContentValidatorSPI}
 * implementations via {@link ServiceLoader} and dispatches validation by file extension.
 * <p>
 * Supports nested context paths for hierarchical validation (e.g., ZIP entries). Error messages are
 * prefixed with the context path so the user can identify where in a nested structure an error
 * occurred.
 *
 * @author Philip Helger
 */
public class UploadContentValidator implements IUploadContentValidatorRegistry
{
  private static final Logger LOGGER = LoggerFactory.getLogger (UploadContentValidator.class);
  private final ICommonsList <IUploadContentValidatorSPI> m_aValidators;

  public UploadContentValidator (@NonNull final IRepoStorageWithToc aRepo)
  {
    ValueEnforcer.notNull (aRepo, "Repo");

    // Load all SPI validators
    m_aValidators = new CommonsArrayList <> ();
    for (final IUploadContentValidatorSPI aValidator : ServiceLoader.load (IUploadContentValidatorSPI.class))
    {
      aValidator.initRepoStorage (aRepo);
      aValidator.initContentValidatorRegistry (this);
      m_aValidators.add (aValidator);
      LOGGER.info ("Loaded upload content validator '" +
                   aValidator.getClass ().getName () +
                   "' for extensions " +
                   aValidator.getSupportedFileExtensions ());
    }
  }

  @Nullable
  private IUploadContentValidatorSPI _findValidator (@NonNull final String sFileExt)
  {
    for (final IUploadContentValidatorSPI aValidator : m_aValidators)
      if (aValidator.getSupportedFileExtensions ().contains (sFileExt))
        return aValidator;
    return null;
  }

  public boolean hasValidatorForExtension (@NonNull final String sFileExt)
  {
    return _findValidator (sFileExt) != null;
  }

  public boolean validateContent (@NonNull final String sContextPath,
                                  @NonNull final String sFileExt,
                                  @NonNull final InputStream aIS,
                                  @NonNull final ErrorList aErrorList) throws IOException
  {
    ValueEnforcer.notNull (sContextPath, "ContextPath");
    ValueEnforcer.notNull (sFileExt, "FileExt");
    ValueEnforcer.notNull (aIS, "InputStream");
    ValueEnforcer.notNull (aErrorList, "ErrorList");

    LOGGER.info ("Checking content for" +
                 (sContextPath.isEmpty () ? "" : " '" + sContextPath + "'") +
                 " file extension '" +
                 sFileExt +
                 "'");
    try
    {
      final IUploadContentValidatorSPI aValidator = _findValidator (sFileExt);
      if (aValidator == null)
        throw new IllegalArgumentException ("Unsupported file extension '" +
                                            sFileExt +
                                            "' - no SPI validator registered");

      // Collect errors in a local list so we can prefix them with context path
      final ErrorList aLocalErrors = new ErrorList ();
      final boolean bValid = aValidator.isValidContent (sFileExt, aIS, aLocalErrors);

      // Transfer errors with context path prefix
      for (final IError aError : aLocalErrors)
      {
        if (sContextPath.isEmpty ())
        {
          // No prefix needed at top level
          aErrorList.add (aError);
        }
        else
        {
          // Prefix error text with context path
          aErrorList.add (SingleError.builder (aError)
                                     .errorText (sContextPath + ": " + aError.getErrorText (Locale.ROOT))
                                     .build ());
        }
      }

      return bValid;
    }
    finally
    {
      LOGGER.info ("Finished checking content for" +
                   (sContextPath.isEmpty () ? "" : " '" + sContextPath + "'") +
                   " file extension '" +
                   sFileExt +
                   "'");
    }
  }
}
