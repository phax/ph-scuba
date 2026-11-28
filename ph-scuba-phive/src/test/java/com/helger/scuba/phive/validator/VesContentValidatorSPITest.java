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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.base.io.stream.StreamHelper;
import com.helger.base.io.stream.StringInputStream;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.RepoStorageContentByteArray;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.impl.RepoStorageInMemory;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.io.resource.ClassPathResource;
import com.helger.phive.api.executorset.IValidationExecutorSet;
import com.helger.phive.api.executorset.ValidationExecutorSet;
import com.helger.phive.api.executorset.status.ValidationExecutorSetStatus;
import com.helger.phive.ves.engine.load.IVESRequirementResolver;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.xml.schematron.ValidationExecutorSchematronBuilder;
import com.helger.phive.xml.source.IValidationSourceXML;

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

  @Test
  public void testRequirementResolver ()
  {
    final DVRCoordinate aBuiltinVESID = DVRCoordinate.parseOrNull ("com.helger.phive.builtin:sch:1.0");
    final IValidationExecutorSet <IValidationSourceXML> aBuiltinVES = ValidationExecutorSet.create (aBuiltinVESID,
                                                                                                    "Built-in Schematron",
                                                                                                    ValidationExecutorSetStatus.createValidNow (),
                                                                                                    ValidationExecutorSchematronBuilder.schIso (new ClassPathResource ("ves/requirement-resolver/builtin.sch"))
                                                                                                                                       .build ());

    final IRepoStorageWithToc aRepo = RepoStorageInMemory.createDefault ("test",
                                                                         ERepoWritable.WITH_WRITE,
                                                                         ERepoDeletable.WITH_DELETE);
    // Only the own Schematron of the VES is in the repository, the required VES is not
    final byte [] aSch = StreamHelper.getAllBytes (new ClassPathResource ("ves/requirement-resolver/own.sch"));
    assertNotNull (aSch);
    assertTrue (aRepo.write (RepoStorageKeyOfArtefact.of (DVRCoordinate.parseOrNull ("com.helger.scuba.test:own-sch:1.0"),
                                                          ".sch"),
                             RepoStorageContentByteArray.of (aSch)).isSuccess ());

    final VesContentValidatorSPI aValidator = new VesContentValidatorSPI ();
    aValidator.initRepoStorage (aRepo);
    assertNull (aValidator.getRequirementResolver ());

    // Default: the requirement is refused
    ErrorList aErrors = new ErrorList ();
    assertFalse (aValidator.isValidContent (VESLoader.FILE_EXT_VES,
                                            new ClassPathResource ("ves/requirement-resolver/requires-builtin.ves").getInputStream (),
                                            aErrors));
    assertFalse (aErrors.isEmpty ());

    // A resolver that does not know the requirement
    aValidator.setRequirementResolver ( (aRequiredVES, aErrorList) -> null);
    aErrors = new ErrorList ();
    assertFalse (aValidator.isValidContent (VESLoader.FILE_EXT_VES,
                                            new ClassPathResource ("ves/requirement-resolver/requires-builtin.ves").getInputStream (),
                                            aErrors));

    // A resolver that knows the requirement
    final IVESRequirementResolver aResolver = (aRequiredVES,
                                               aErrorList) -> aBuiltinVESID.equals (aRequiredVES.getRequiredVESID ()) ? aBuiltinVES
                                                                                                                       : null;
    assertSame (aValidator, aValidator.setRequirementResolver (aResolver));
    assertSame (aResolver, aValidator.getRequirementResolver ());
    aErrors = new ErrorList ();
    assertTrue (aErrors.toString (),
                aValidator.isValidContent (VESLoader.FILE_EXT_VES,
                                           new ClassPathResource ("ves/requirement-resolver/requires-builtin.ves").getInputStream (),
                                           aErrors));
  }
}
