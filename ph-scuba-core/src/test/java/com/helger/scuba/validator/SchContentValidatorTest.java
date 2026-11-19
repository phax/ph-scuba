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

import org.junit.Test;

import com.helger.base.io.stream.StringInputStream;
import com.helger.diagnostics.error.list.ErrorList;

/**
 * Test class for {@link SchContentValidator}.
 *
 * @author Philip Helger
 */
public final class SchContentValidatorTest
{
  private static final SchContentValidator VALIDATOR = new SchContentValidator ();

  @Test
  public void testValidSchematron ()
  {
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<schema xmlns='http://purl.oclc.org/dsdl/schematron'>\n" +
                        "  <pattern name='test'/>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    final boolean bIsValid = VALIDATOR.isValidContent (".sch", StringInputStream.utf8 (sSch), aErrors);
    assertTrue (aErrors.toString (), bIsValid);
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testInvalidSchematron ()
  {
    // Well-formed, but an assert without a test attribute
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<schema xmlns='http://purl.oclc.org/dsdl/schematron'>\n" +
                        "  <pattern name='test'>\n" +
                        "    <rule context='/'>\n" +
                        "      <assert>Missing test</assert>\n" +
                        "    </rule>\n" +
                        "  </pattern>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".sch", StringInputStream.utf8 (sSch), aErrors));
    assertFalse (aErrors.isEmpty ());
  }

  @Test
  public void testIncludeOnly ()
  {
    // All patterns come from the included file, which is not resolved
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<schema xmlns='http://purl.oclc.org/dsdl/schematron'>\n" +
                        "  <include href='patterns.sch'/>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aErrors.toString (), VALIDATOR.isValidContent (".sch", StringInputStream.utf8 (sSch), aErrors));
    assertEquals (1, aErrors.size ());
    assertTrue (aErrors.containsNoError ());
    assertTrue (aErrors.get (0).getAsStringLocaleIndepdent ().contains ("not resolved"));
  }

  @Test
  public void testIncludeWithInvalidPattern ()
  {
    // The local content is still checked
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<schema xmlns='http://purl.oclc.org/dsdl/schematron'>\n" +
                        "  <include href='patterns.sch'/>\n" +
                        "  <pattern>\n" +
                        "    <rule context='/'>\n" +
                        "      <assert>Missing test</assert>\n" +
                        "    </rule>\n" +
                        "  </pattern>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".sch", StringInputStream.utf8 (sSch), aErrors));
    assertTrue (aErrors.containsAtLeastOneError ());
  }

  @Test
  public void testIncludeWithoutHref ()
  {
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<schema xmlns='http://purl.oclc.org/dsdl/schematron'>\n" +
                        "  <include/>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".sch", StringInputStream.utf8 (sSch), aErrors));
    assertTrue (aErrors.toString (), aErrors.toString ().contains ("has no 'href'"));
  }

  @Test
  public void testXslIncludeAndImport ()
  {
    // Foreign XSLT elements are kept as they are
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<schema xmlns='http://purl.oclc.org/dsdl/schematron' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n" +
                        "  <xsl:include href='functions.xsl'/>\n" +
                        "  <xsl:import href='more.xsl'/>\n" +
                        "  <pattern>\n" +
                        "    <rule context='/'>\n" +
                        "      <assert test='true()'>Always</assert>\n" +
                        "    </rule>\n" +
                        "  </pattern>\n" +
                        "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (aErrors.toString (), VALIDATOR.isValidContent (".sch", StringInputStream.utf8 (sSch), aErrors));
  }

  @Test
  public void testInvalidXml ()
  {
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".sch", StringInputStream.utf8 ("not xml at all"), aErrors));
    assertFalse (aErrors.isEmpty ());
  }
}
