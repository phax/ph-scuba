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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.diagnostics.error.list.ErrorList;

/**
 * Test class for {@link XsdContentValidator}.
 *
 * @author Philip Helger
 */
public final class XsdContentValidatorTest
{
  private static final XsdContentValidator VALIDATOR = new XsdContentValidator ();

  @Test
  public void testSupportedExtensions ()
  {
    assertNotNull (VALIDATOR.getSupportedFileExtensions ());
    assertTrue (VALIDATOR.getSupportedFileExtensions ().contains (".xsd"));
  }

  @Test
  public void testValidXsd ()
  {
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
                        "  <xs:element name='test' type='xs:string'/>\n" +
                        "</xs:schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".xsd",
                                          new NonBlockingByteArrayInputStream (sXsd.getBytes (StandardCharsets.UTF_8)),
                                          aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testInvalidXml ()
  {
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".xsd",
                                           new NonBlockingByteArrayInputStream ("not xml".getBytes (StandardCharsets.UTF_8)),
                                           aErrors));
    assertFalse (aErrors.isEmpty ());
  }

  @Test
  public void testWrongRootElement ()
  {
    final String sXml = "<?xml version='1.0' encoding='UTF-8'?>\n" + "<root xmlns='http://www.w3.org/2001/XMLSchema'/>";
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".xsd",
                                           new NonBlockingByteArrayInputStream (sXml.getBytes (StandardCharsets.UTF_8)),
                                           aErrors));
    assertFalse (aErrors.isEmpty ());
  }

  @Test
  public void testWrongNamespace ()
  {
    final String sXml = "<?xml version='1.0' encoding='UTF-8'?>\n" + "<schema xmlns='urn:wrong'/>";
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".xsd",
                                           new NonBlockingByteArrayInputStream (sXml.getBytes (StandardCharsets.UTF_8)),
                                           aErrors));
    assertFalse (aErrors.isEmpty ());
  }

  private static boolean _isValid (@NonNull final String sXsd, @NonNull final ErrorList aErrors)
  {
    return VALIDATOR.isValidContent (".xsd",
                                     new NonBlockingByteArrayInputStream (sXsd.getBytes (StandardCharsets.UTF_8)),
                                     aErrors);
  }

  @Test
  public void testInternalDoctype ()
  {
    // Like UBL-xmldsig-core-schema-2.1.xsd of UBL 2.1
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<!DOCTYPE schema\n" +
                        " [\n" +
                        "   <!ATTLIST schema xmlns:ds CDATA #FIXED 'http://www.w3.org/2000/09/xmldsig#'>\n" +
                        "   <!ENTITY dsig 'http://www.w3.org/2000/09/xmldsig#'>\n" +
                        "   <!ENTITY % p ''>\n" +
                        "   <!ENTITY % s ''>\n" +
                        "  ]>\n" +
                        "<schema xmlns='http://www.w3.org/2001/XMLSchema' targetNamespace='&dsig;'>\n" +
                        "  <element name='test' type='string'/>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aErrors.toString (), _isValid (sXsd, aErrors));
    assertTrue (aErrors.toString (), aErrors.isEmpty ());
  }

  @Test
  public void testExternalDtdNotLoaded ()
  {
    // The file does not exist - loading it would fail
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<!DOCTYPE schema SYSTEM 'file:///does/not/exist/XMLSchema.dtd'>\n" +
                        "<schema xmlns='http://www.w3.org/2001/XMLSchema'/>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aErrors.toString (), _isValid (sXsd, aErrors));
  }

  @Test
  public void testExternalParameterEntityNotLoaded ()
  {
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<!DOCTYPE schema [\n" +
                        "  <!ENTITY % ext SYSTEM 'file:///does/not/exist/ext.dtd'>\n" +
                        "  %ext;\n" +
                        "]>\n" +
                        "<schema xmlns='http://www.w3.org/2001/XMLSchema'/>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aErrors.toString (), _isValid (sXsd, aErrors));
  }

  @Test
  public void testExternalGeneralEntityNotLoaded ()
  {
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<!DOCTYPE schema [\n" +
                        "  <!ENTITY ext SYSTEM 'file:///does/not/exist/secret.txt'>\n" +
                        "]>\n" +
                        "<schema xmlns='http://www.w3.org/2001/XMLSchema'><annotation><documentation>&ext;</documentation></annotation></schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aErrors.toString (), _isValid (sXsd, aErrors));
  }

  @Test
  public void testEntityExpansionLimit ()
  {
    // "Billion laughs" - 10^10 expansions
    final StringBuilder aSB = new StringBuilder ("<?xml version='1.0' encoding='UTF-8'?>\n<!DOCTYPE schema [\n");
    aSB.append ("  <!ENTITY lol0 'lol'>\n");
    for (int i = 1; i <= 10; ++i)
    {
      aSB.append ("  <!ENTITY lol").append (i).append (" '");
      for (int j = 0; j < 10; ++j)
        aSB.append ("&lol").append (i - 1).append (';');
      aSB.append ("'>\n");
    }
    aSB.append ("]>\n<schema xmlns='http://www.w3.org/2001/XMLSchema'><annotation><documentation>&lol10;</documentation></annotation></schema>");
    final ErrorList aErrors = new ErrorList ();
    assertFalse (_isValid (aSB.toString (), aErrors));
    assertFalse (aErrors.isEmpty ());
  }
}
