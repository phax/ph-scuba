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

public final class SchContentValidatorTest
{
  private static final SchContentValidator VALIDATOR = new SchContentValidator ();

  @Test
  public void testValidSchematron ()
  {
    final String sSch = "<?xml version='1.0' encoding='UTF-8'?>\n" +
                         "<schema xmlns='http://purl.oclc.org/dml/schematron'>\n" +
                         "  <pattern name='test'/>\n" +
                         "</schema>";
    final ErrorList aErrors = new ErrorList ();
    assertTrue (VALIDATOR.isValidContent (".sch",
                                          new ByteArrayInputStream (sSch.getBytes (StandardCharsets.UTF_8)),
                                          aErrors));
  }

  @Test
  public void testInvalidXml ()
  {
    final ErrorList aErrors = new ErrorList ();
    assertFalse (VALIDATOR.isValidContent (".sch",
                                           new ByteArrayInputStream ("not xml at all".getBytes (StandardCharsets.UTF_8)),
                                           aErrors));
    assertFalse (aErrors.isEmpty ());
  }
}
