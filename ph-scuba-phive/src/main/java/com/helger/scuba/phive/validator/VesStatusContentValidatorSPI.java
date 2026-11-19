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
import org.jspecify.annotations.Nullable;

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
public final class VesStatusContentValidatorSPI implements IUploadContentValidatorSPI
{
  /** Default constructor. */
  public VesStatusContentValidatorSPI ()
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

  /**
   * Create a coordinate with a static version, as needed to look it up in the repository.
   *
   * @param sGroupID
   *        Group ID. May be <code>null</code>.
   * @param sArtifactID
   *        Artifact ID. May be <code>null</code>.
   * @param sVersion
   *        Version. May be <code>null</code>.
   * @return <code>null</code> if the values do not form a valid coordinate or if the version is a
   *         pseudo version.
   */
  @Nullable
  private static DVRCoordinate _createStaticCoordinateOrNull (@Nullable final String sGroupID,
                                                             @Nullable final String sArtifactID,
                                                             @Nullable final String sVersion)
  {
    final DVRVersion aVersion = DVRVersion.parseOrNull (sVersion);
    if (aVersion == null || !aVersion.isStaticVersion ())
      return null;
    try
    {
      return new DVRCoordinate (sGroupID, sArtifactID, aVersion);
    }
    catch (final IllegalArgumentException ex)
    {
      return null;
    }
  }

  public boolean isValidContent (@NonNull final String sFileExt,
                                 @NonNull final InputStream aIS,
                                 @NonNull final ErrorList aErrorList)
  {
    final VESStatus1Marshaller aMarshaller = new VESStatus1Marshaller ();
    // Avoid logging - the errors are collected in the error list instead
    aMarshaller.readExceptionCallbacks ().removeAll ();
    final VesStatusType aStatus = aMarshaller.setCollectErrors (aErrorList).read (aIS);
    if (aStatus == null)
    {
      // The marshaller already added the XSD errors
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
      final DVRCoordinate aReplVESID = _createStaticCoordinateOrNull (aReplacement.getGroupId (),
                                                                      aReplacement.getArtifactId (),
                                                                      aReplacement.getVersion ());
      if (aReplVESID == null)
      {
        aErrorList.add (SingleError.builderError ()
                                   .errorText ("The replacement VESID '" +
                                               aReplacement.getGroupId () +
                                               ":" +
                                               aReplacement.getArtifactId () +
                                               ":" +
                                               aReplacement.getVersion () +
                                               "' is not a valid VESID with a static version")
                                   .build ());
        return false;
      }
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
