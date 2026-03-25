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

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

import com.helger.base.io.nonblocking.NonBlockingByteArrayInputStream;
import com.helger.diagnostics.error.list.ErrorList;

public final class ZipContentValidatorTest
{
  private static final ZipContentValidator VALIDATOR = new ZipContentValidator ();

  @Test
  public void testValidZip () throws Exception
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream ();
    try (final ZipOutputStream zos = new ZipOutputStream (baos))
    {
      zos.putNextEntry (new ZipEntry ("test.txt"));
      zos.write ("hello".getBytes (StandardCharsets.UTF_8));
      zos.closeEntry ();
    }

    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".zip", new NonBlockingByteArrayInputStream (baos.toByteArray ()), aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testEmptyZip () throws Exception
  {
    final ByteArrayOutputStream baos = new ByteArrayOutputStream ();
    try (final ZipOutputStream zos = new ZipOutputStream (baos))
    {
      // empty
    }

    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".zip", new NonBlockingByteArrayInputStream (baos.toByteArray ()), aErrors));
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
}
