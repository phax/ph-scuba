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

import java.io.InputStream;

import org.jspecify.annotations.NonNull;

import com.helger.annotation.style.IsSPIImplementation;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.diagnostics.error.SingleError;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.RepoStorageKey;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.ves.model.v1.VESStatus1Marshaller;
import com.helger.phive.ves.v10.VesStatusReplacementType;
import com.helger.phive.ves.v10.VesStatusType;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;

/**
 * Content validator for VES status files (.status). Validates JAXB unmarshalling, deprecation
 * consistency, and replacement VESID existence.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public final class VesStatusContentValidator implements IUploadContentValidatorSPI
{
  /** Default constructor. */
  public VesStatusContentValidator ()
  {}

  private IRepoStorageWithToc m_aRepo;

  @NonNull
  public ICommonsSet <String> getSupportedFileExtensions ()
  {
    return new CommonsHashSet <> (VESLoader.FILE_EXT_STATUS);
  }

  @Override
  public void initRepoStorage (@NonNull final IRepoStorageWithToc aRepo)
  {
    m_aRepo = aRepo;
  }

  public boolean isValidContent (@NonNull final String sFileExt,
                                 @NonNull final InputStream aIS,
                                 @NonNull final ErrorList aErrorList)
  {
    final VESStatus1Marshaller aMarshaller = new VESStatus1Marshaller ();
    aMarshaller.readExceptionCallbacks ().removeAll ();
    final VesStatusType aStatus = aMarshaller.setCollectErrors (aErrorList).read (aIS);
    if (aStatus == null)
    {
      if (false)
        aErrorList.add (SingleError.builderError ()
                                   .errorText ("Failed to read payload as VES Status - XSD error")
                                   .build ());
      return false;
    }

    final boolean bIsDeprecated = aStatus.isDeprecated () != null && aStatus.isDeprecated ().booleanValue ();
    if (StringHelper.isNotEmpty (aStatus.getDeprecationReason ()) && !bIsDeprecated)
    {
      aErrorList.add (SingleError.builderError ()
                                 .errorText ("A deprecation reason might only be provided if deprecation is enabled")
                                 .build ());
      return false;
    }

    final VesStatusReplacementType aReplacement = aStatus.getReplacement ();
    if (aReplacement != null)
    {
      // Check if the replacement is actually contained
      final DVRCoordinate aReplVESID = new DVRCoordinate (aReplacement.getGroupId (),
                                                          aReplacement.getArtifactId (),
                                                          DVRVersion.parseOrNull (aReplacement.getVersion ()));
      final RepoStorageKey aKey = RepoStorageKeyOfArtefact.of (aReplVESID, VESLoader.FILE_EXT_VES);
      if (!m_aRepo.exists (aKey))
      {
        aErrorList.add (SingleError.builderError ()
                                   .errorText ("Failed to resolve the replacement VESID '" +
                                               aReplVESID.getAsSingleID () +
                                               "' in the repository")
                                   .build ());
        return false;
      }
    }
    return true;
  }
}
