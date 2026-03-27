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
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.base.io.nonblocking.NonBlockingByteArrayOutputStream;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.impl.RepoStorageInMemory;

/**
 * Test class for {@link UploadContentValidator}.
 *
 * @author Philip Helger
 */
public final class UploadContentValidatorTest
{
  @NonNull
  private static UploadContentValidator _createValidator ()
  {
    return new UploadContentValidator (RepoStorageInMemory.createDefault ("test",
                                                                          ERepoWritable.WITHOUT_WRITE,
                                                                          ERepoDeletable.WITHOUT_DELETE));
  }

  @Test
  public void testHasValidatorForExtension ()
  {
    final UploadContentValidator aValidator = _createValidator ();
    assertTrue (aValidator.hasValidatorForExtension (".xsd"));
    assertTrue (aValidator.hasValidatorForExtension (".sch"));
    assertTrue (aValidator.hasValidatorForExtension (".xslt"));
    assertTrue (aValidator.hasValidatorForExtension (".zip"));
    assertFalse (aValidator.hasValidatorForExtension (".unknown"));
  }

  @Test
  public void testValidXsd () throws Exception
  {
    final UploadContentValidator aValidator = _createValidator ();
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
                        "  <xs:element name='test' type='xs:string'/>\n" +
                        "</xs:schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aValidator.validateContent ("",
                                            ".xsd",
                                            new NonBlockingByteArrayInputStream (sXsd.getBytes (StandardCharsets.UTF_8)),
                                            aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testInvalidXsdWithContext () throws Exception
  {
    final UploadContentValidator aValidator = _createValidator ();
    final ErrorList aErrors = new ErrorList ();
    assertFalse (aValidator.validateContent ("my-artifact",
                                             ".xsd",
                                             new NonBlockingByteArrayInputStream ("broken".getBytes (StandardCharsets.UTF_8)),
                                             aErrors));
    assertFalse (aErrors.isEmpty ());
    // Verify context path is in the error message
    assertTrue (aErrors.getFirstOrNull ().getErrorText (Locale.ROOT).startsWith ("my-artifact: "));
  }

  @Test
  public void testZipWithRecursiveXsdValidation () throws Exception
  {
    final UploadContentValidator aValidator = _createValidator ();

    // Create a ZIP containing a valid XSD
    final String sXsd = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<xs:schema xmlns:xs='http://www.w3.org/2001/XMLSchema'>\n" +
                        "  <xs:element name='test' type='xs:string'/>\n" +
                        "</xs:schema>";
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream zos = new ZipOutputStream (aBAOS))
    {
      zos.putNextEntry (new ZipEntry ("schemas/test.xsd"));
      zos.write (sXsd.getBytes (StandardCharsets.UTF_8));
      zos.closeEntry ();
    }

    final ErrorList aErrors = new ErrorList ();
    assertTrue (aValidator.validateContent ("", ".zip", aBAOS.getAsInputStream (), aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testZipWithInvalidXsdRecursive () throws Exception
  {
    final UploadContentValidator aValidator = _createValidator ();

    // Create a ZIP containing an invalid XSD
    final NonBlockingByteArrayOutputStream aBAOS = new NonBlockingByteArrayOutputStream ();
    try (final ZipOutputStream zos = new ZipOutputStream (aBAOS))
    {
      zos.putNextEntry (new ZipEntry ("bad.xsd"));
      zos.write ("not valid xml".getBytes (StandardCharsets.UTF_8));
      zos.closeEntry ();
    }

    final ErrorList aErrors = new ErrorList ();
    assertFalse (aValidator.validateContent ("", ".zip", aBAOS.getAsInputStream (), aErrors));
    assertFalse (aErrors.isEmpty ());
    // Error should contain the ZIP entry path context
    assertTrue (aErrors.getFirstOrNull ().getErrorText (Locale.ROOT).contains ("bad.xsd"));
  }
}
