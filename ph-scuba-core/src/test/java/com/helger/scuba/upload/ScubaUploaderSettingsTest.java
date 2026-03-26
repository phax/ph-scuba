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
package com.helger.scuba.upload;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Test class for {@link ScubaUploaderSettings}.
 *
 * @author Philip Helger
 */
public final class ScubaUploaderSettingsTest
{
  @Test
  public void testDefaults ()
  {
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ();
    assertFalse (aSettings.isAllowUploadWithUnknownExtension ());
    assertFalse (aSettings.isAllowOverwriteExisting ());
  }

  @Test
  public void testChaining ()
  {
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true)
                                                                        .setAllowOverwriteExisting (true);
    assertTrue (aSettings.isAllowUploadWithUnknownExtension ());
    assertTrue (aSettings.isAllowOverwriteExisting ());
  }

  @Test
  public void testAssignFrom ()
  {
    final ScubaUploaderSettings aSource = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true)
                                                                      .setAllowOverwriteExisting (true);
    final ScubaUploaderSettings aTarget = new ScubaUploaderSettings ();
    aTarget.assignFrom (aSource);
    assertEquals (aSource, aTarget);
  }

  @Test
  public void testEqualsAndHashCode ()
  {
    final ScubaUploaderSettings a = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true);
    final ScubaUploaderSettings b = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true);
    assertEquals (a, b);
    assertEquals (a.hashCode (), b.hashCode ());
  }
}
