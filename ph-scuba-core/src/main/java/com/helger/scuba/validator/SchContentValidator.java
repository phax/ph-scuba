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

import java.io.InputStream;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.annotation.style.IsSPIImplementation;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;
import com.helger.xml.sax.WrappedCollectingSAXErrorHandler;
import com.helger.xml.serialize.read.DOMReader;
import com.helger.xml.serialize.read.DOMReaderSettings;

/**
 * Content validator for Schematron files (.sch). Checks XML well-formedness.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public final class SchContentValidator implements IUploadContentValidatorSPI
{
  public static final String FILE_EXT_SCH = ".sch";

  @NonNull
  public ICommonsSet <String> getSupportedFileExtensions ()
  {
    return new CommonsHashSet <> (FILE_EXT_SCH);
  }

  public boolean isValidContent (@NonNull final String sFileExt,
                                 @NonNull final InputStream aIS,
                                 @NonNull final ErrorList aErrorList)
  {
    if (DOMReader.readXMLDOM (aIS,
                              new DOMReaderSettings ().setErrorHandler (new WrappedCollectingSAXErrorHandler (aErrorList))) ==
        null)
    {
      aErrorList.add (SingleError.builderError ().errorText ("Failed to parse Schematron as valid XML").build ());
      return false;
    }
    return true;
  }
}
