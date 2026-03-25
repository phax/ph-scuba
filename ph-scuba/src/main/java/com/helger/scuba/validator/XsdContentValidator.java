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

import javax.xml.XMLConstants;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;
import com.helger.xml.XMLHelper;
import com.helger.xml.serialize.read.DOMReader;

/**
 * Content validator for XML Schema files (.xsd). Checks XML well-formedness and
 * verifies root element is {@code schema} in the W3C XML Schema namespace.
 *
 * @author Philip Helger
 */
public final class XsdContentValidator implements IUploadContentValidatorSPI
{
  public static final String FILE_EXT_XSD = ".xsd";

  private static final Logger LOGGER = LoggerFactory.getLogger (XsdContentValidator.class);

  @NonNull
  public ICommonsSet <String> getSupportedFileExtensions ()
  {
    return new CommonsHashSet <> (FILE_EXT_XSD);
  }

  public boolean isValidContent (@NonNull final String sFileExt, @NonNull final InputStream aIS)
  {
    // Check well-formedness
    final Document aDoc = DOMReader.readXMLDOM (aIS);
    if (aDoc == null || aDoc.getDocumentElement () == null)
      return false;

    // Check root element
    final String sLocalName = XMLHelper.getLocalNameOrTagName (aDoc.getDocumentElement ());
    final String sExpectedLocaleName = "schema";
    if (!sExpectedLocaleName.equals (sLocalName))
    {
      LOGGER.error ("The root element name for XSD must be '" + sExpectedLocaleName + "'");
      return false;
    }

    final String sNamespaceURI = aDoc.getDocumentElement ().getNamespaceURI ();
    final String sExpectedNamespaceURI = XMLConstants.W3C_XML_SCHEMA_NS_URI;
    if (!sExpectedNamespaceURI.equals (sNamespaceURI))
    {
      LOGGER.error ("The root element namespace URI for XSD must be '" + sExpectedNamespaceURI + "'");
      return false;
    }

    return true;
  }
}
