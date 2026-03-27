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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.scuba.api.spi.IUploadContentValidatorRegistry;

/**
 * Test class for {@link ZipContentValidator}.
 *
 * @author Philip Helger
 */
public final class ZipContentValidatorTest
{
  private static final ZipContentValidator VALIDATOR = new ZipContentValidator ();

  @Test
  public void testValidZip () throws Exception
  {
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream zos = new ZipOutputStream (aBAOS))
    {
      zos.putNextEntry (new ZipEntry ("test.txt"));
      zos.write ("hello".getBytes (StandardCharsets.UTF_8));
      zos.closeEntry ();
    }

    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".zip", aBAOS.getAsInputStream (), aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testEmptyZip () throws Exception
  {
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream zos = new ZipOutputStream (aBAOS))
    {
      // empty
    }

    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".zip", aBAOS.getAsInputStream (), aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testInvalidZip () throws Exception
  {
    final ErrorList aErrors = new ErrorList ();
    // Not a valid ZIP - but ZipInputStream may not throw; it just returns null entries
    // This should still return true (no entries to validate)
    final boolean bResult = VALIDATOR.isValidContent (".zip",
                                                      new NonBlockingByteArrayInputStream ("not a zip".getBytes (StandardCharsets.UTF_8)),
                                                      aErrors);
    // ZipInputStream silently returns 0 entries for non-ZIP data
    assertTrue (bResult);
  }

  /**
   * Create a {@link ZipContentValidator} wired with a simple registry that supports .zip and .xsd
   * validation recursively.
   */
  @NonNull
  private static ZipContentValidator _createValidatorWithRegistry ()
  {
    final ZipContentValidator aZipValidator = new ZipContentValidator ();
    final XsdContentValidator aXsdValidator = new XsdContentValidator ();

    final IUploadContentValidatorRegistry aRegistry = new IUploadContentValidatorRegistry ()
    {
      public boolean hasValidatorForExtension (final String sFileExt)
      {
        return ".zip".equals (sFileExt) || ".xsd".equals (sFileExt);
      }

      public boolean validateContent (final String sContextPath,
                                      final String sFileExt,
                                      final InputStream aIS,
                                      final ErrorList aErrorList) throws IOException
      {
        if (".zip".equals (sFileExt))
          return aZipValidator.isValidContent (sFileExt, aIS, aErrorList);
        if (".xsd".equals (sFileExt))
          return aXsdValidator.isValidContent (sFileExt, aIS, aErrorList);
        return true;
      }
    };
    aZipValidator.initContentValidatorRegistry (aRegistry);
    return aZipValidator;
  }

  @NonNull
  private static byte [] _createInnerZip (@NonNull final String sEntryName, final byte @NonNull [] aContent)
                                                                                                             throws Exception
  {
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream aZOS = new ZipOutputStream (aBAOS))
    {
      aZOS.putNextEntry (new ZipEntry (sEntryName));
      aZOS.write (aContent);
      aZOS.closeEntry ();
    }
    return aBAOS.toByteArray ();
  }

  @Test
  public void testNestedZipWithInvalidXsd () throws Exception
  {
    // Inner zip contains an invalid .xsd (not valid XML)
    final byte [] aInnerZip = _createInnerZip ("bad.xsd", "not xml at all".getBytes (StandardCharsets.UTF_8));

    // Outer zip contains the inner zip
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream aZOS = new ZipOutputStream (aBAOS))
    {
      aZOS.putNextEntry (new ZipEntry ("inner.zip"));
      aZOS.write (aInnerZip);
      aZOS.closeEntry ();
    }

    final ErrorList aErrors = new ErrorList ();
    final ZipContentValidator aValidator = _createValidatorWithRegistry ();
    assertFalse (aValidator.isValidContent (".zip", aBAOS.getAsInputStream (), aErrors));
    assertFalse (aErrors.isEmpty ());
    assertEquals (3, aErrors.size ());
    assertTrue (aErrors.get (0).getAsStringLocaleIndepdent (),
                aErrors.get (0).getAsStringLocaleIndepdent ().contains ("org.xml.sax.SAXParseException"));
    assertTrue (aErrors.get (1).getAsStringLocaleIndepdent (),
                aErrors.get (1).getAsStringLocaleIndepdent ().contains ("org.xml.sax.SAXParseException"));
    assertTrue (aErrors.get (2).getAsStringLocaleIndepdent (),
                aErrors.get (2).getAsStringLocaleIndepdent ().contains ("XSD is not well-formed XML"));
  }

  @Test
  public void testNestedZipWithValidXsd () throws Exception
  {
    // Inner zip contains a valid dummy .xsd
    final String sXsd = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                        "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">\n" +
                        "  <xs:element name=\"dummy\" type=\"xs:string\" />\n" +
                        "</xs:schema>\n";
    final byte [] aInnerZip = _createInnerZip ("dummy.xsd", sXsd.getBytes (StandardCharsets.UTF_8));

    // Outer zip contains the inner zip
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream aZOS = new ZipOutputStream (aBAOS))
    {
      aZOS.putNextEntry (new ZipEntry ("inner.zip"));
      aZOS.write (aInnerZip);
      aZOS.closeEntry ();
    }

    final ErrorList aErrors = new ErrorList ();
    final ZipContentValidator aValidator = _createValidatorWithRegistry ();
    assertTrue (aValidator.isValidContent (".zip", aBAOS.getAsInputStream (), aErrors));
    assertTrue (aErrors.isEmpty ());
  }
}
