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

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.io.stream.StringInputStream;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.impl.RepoStorageInMemory;
import com.helger.phive.ves.engine.load.VESLoader;

/**
 * Test class for {@link VesContentValidatorSPI}.
 *
 * @author Philip Helger
 */
public final class VesContentValidatorSPITest
{
  @NonNull
  private static VesContentValidatorSPI _createValidator ()
  {
    final VesContentValidatorSPI ret = new VesContentValidatorSPI ();
    ret.initRepoStorage (RepoStorageInMemory.createDefault ("test",
                                                            ERepoWritable.WITH_WRITE,
                                                            ERepoDeletable.WITH_DELETE));
    return ret;
  }

  @Test
  public void testSupportedExtensions ()
  {
    final VesContentValidatorSPI aValidator = _createValidator ();
    assertNotNull (aValidator.getSupportedFileExtensions ());
    assertTrue (aValidator.getSupportedFileExtensions ().contains (VESLoader.FILE_EXT_VES));
  }

  @Test
  public void testInvalidContent ()
  {
    final VesContentValidatorSPI aValidator = _createValidator ();
    final ErrorList aErrors = new ErrorList ();
    assertFalse (aValidator.isValidContent (VESLoader.FILE_EXT_VES, StringInputStream.utf8 ("not valid xml"), aErrors));
    assertFalse (aErrors.isEmpty ());
  }
}
