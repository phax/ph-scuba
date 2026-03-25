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
package com.helger.scuba.phive.validator;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.Test;

import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.impl.RepoStorageInMemory;
import com.helger.phive.ves.engine.load.VESLoader;

public final class VesContentValidatorTest
{
  private static VesContentValidator _createValidator ()
  {
    final VesContentValidator ret = new VesContentValidator ();
    ret.initRepoStorage (RepoStorageInMemory.createDefault ("test",
                                                             ERepoWritable.WITH_WRITE,
                                                             ERepoDeletable.WITH_DELETE));
    return ret;
  }

  @Test
  public void testSupportedExtensions ()
  {
    final VesContentValidator aValidator = _createValidator ();
    assertNotNull (aValidator.getSupportedFileExtensions ());
    assertTrue (aValidator.getSupportedFileExtensions ().contains (VESLoader.FILE_EXT_VES));
  }

  @Test
  public void testInvalidContent ()
  {
    final VesContentValidator aValidator = _createValidator ();
    final ErrorList aErrors = new ErrorList ();
    assertFalse (aValidator.isValidContent (VESLoader.FILE_EXT_VES,
                                            new ByteArrayInputStream ("not valid xml".getBytes (StandardCharsets.UTF_8)),
                                            aErrors));
    assertFalse (aErrors.isEmpty ());
  }
}
