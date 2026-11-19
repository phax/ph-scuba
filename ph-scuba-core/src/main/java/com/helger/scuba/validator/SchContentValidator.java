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

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.io.resource.inmemory.ReadableResourceString;
import com.helger.schematron.errorhandler.WrappedCollectingPSErrorHandler;
import com.helger.schematron.exchange.PSReader;
import com.helger.schematron.model.IPSElement;
import com.helger.schematron.model.PSSchema;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.serialize.MicroReader;
import com.helger.xml.sax.WrappedCollectingSAXErrorHandler;
import com.helger.xml.serialize.read.SAXReaderSettings;

/**
 * Content validator for Schematron files (.sch). Checks XML well-formedness, reads the Schematron
 * and checks that it is a valid Schematron schema. Included files (<code>sch:include</code>) are
 * not resolved, because only the uploaded file is available: their content is not checked, and a
 * Schematron with includes does not need to contain a pattern itself.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public final class SchContentValidator implements IUploadContentValidatorSPI
{
  /** The file extension handled by this validator. */
  public static final String FILE_EXT_SCH = ".sch";

  /** Default constructor. */
  public SchContentValidator ()
  {}

  @NonNull
  public ICommonsSet <String> getSupportedFileExtensions ()
  {
    return new CommonsHashSet <> (FILE_EXT_SCH);
  }

  public boolean isValidContent (@NonNull final String sFileExt,
                                 @NonNull final InputStream aIS,
                                 @NonNull final ErrorList aErrorList)
  {
    // Check well-formedness. Unlike for XSDs, the parser defaults stay in place, so no DOCTYPE is
    // allowed.
    final SAXReaderSettings aDRS = new SAXReaderSettings ().setErrorHandler (new WrappedCollectingSAXErrorHandler (aErrorList));
    aDRS.exceptionCallbacks ()
        .set (ex -> aErrorList.add (SingleError.builderError ()
                                               .errorText ("Error parsing XML")
                                               .linkedException (ex)
                                               .build ()));
    final IMicroDocument aDoc = MicroReader.readMicroXML (aIS, aDRS);
    if (aDoc == null)
    {
      aErrorList.add (SingleError.builderError ().errorText ("Schematron is not well-formed XML").build ());
      return false;
    }
    if (aDoc.getDocumentElement () == null)
    {
      aErrorList.add (SingleError.builderError ().errorText ("Schematron is missing XML root element").build ());
      return false;
    }

    // Read specific Schematron
    final PSReader aReader = new PSReader (ReadableResourceString.utf8 ("Provided via InputStream"),
                                           new WrappedCollectingPSErrorHandler (aErrorList),
                                           null);
    try
    {
      // Read from previously parsed DOM node (throws in case of a wrong root element)
      final PSSchema aSchema = aReader.readSchemaFromXML (aDoc.getDocumentElement ());
      if (!aSchema.hasAnyInclude ())
      {
        // Check the structure (e.g. at least one pattern, test attributes of asserts)
        return aSchema.isValid (new WrappedCollectingPSErrorHandler (aErrorList));
      }

      // The included files cannot be resolved, because only this file is available
      aErrorList.add (SingleError.builderWarn ()
                                 .errorText ("The Schematron includes " +
                                             aSchema.getAllIncludes ().size () +
                                             " file(s) that are not resolved, so their content is not checked")
                                 .build ());

      // The patterns may all come from the included files, so the only error of the schema itself
      // ("no patterns or groups") is ignored. All other elements are checked.
      final ErrorList aStructureErrors = new ErrorList ();
      aSchema.validateCompletely (new WrappedCollectingPSErrorHandler (aStructureErrors)
      {
        @Override
        public void error (@NonNull final IPSElement aSourceElement, @NonNull final String sMessage)
        {
          if (aSourceElement != aSchema)
            super.error (aSourceElement, sMessage);
        }
      });
      aErrorList.addAll (aStructureErrors);
      return aStructureErrors.containsNoError ();
    }
    catch (final Exception ex)
    {
      aErrorList.add (SingleError.builderError ()
                                 .errorText ("Exception parsing Schematron: " + ex.getMessage ())
                                 .linkedException (ex)
                                 .build ());
      return false;
    }
  }
}
