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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.helger.diagnostics.error.list.ErrorList;

public final class XsltContentValidatorTest
{
  private static final XsltContentValidator VALIDATOR = new XsltContentValidator ();

  @Test
  public void testValidXslt ()
  {
    final String sXslt = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                         "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>\n" +
                         "  <xsl:template match='/'/>\n" +
                         "</xsl:stylesheet>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".xslt",
                                          new ByteArrayInputStream (sXslt.getBytes (StandardCharsets.UTF_8)),
                                          aErrors));
    assertTrue (aErrors.isEmpty ());
  }

  @Test
  public void testWrongRootElement ()
  {
    final String sXml = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                        "<transform xmlns='http://www.w3.org/1999/XSL/Transform'/>";
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".xslt",
                                           new ByteArrayInputStream (sXml.getBytes (StandardCharsets.UTF_8)),
                                           aErrors));
    assertFalse (aErrors.isEmpty ());
  }

  @Test
  public void testInvalidXml ()
  {
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".xslt",
                                           new ByteArrayInputStream ("not xml".getBytes (StandardCharsets.UTF_8)),
                                           aErrors));
    assertFalse (aErrors.isEmpty ());
  }
}
