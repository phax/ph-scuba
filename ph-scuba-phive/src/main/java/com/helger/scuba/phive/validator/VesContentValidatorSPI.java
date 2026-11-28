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
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.phive.api.executorset.status.ValidationExecutorSetStatus;
import com.helger.phive.ves.engine.load.DefaultVESLoaderXSD;
import com.helger.phive.ves.engine.load.IVESRequirementResolver;
import com.helger.phive.ves.engine.load.LoadedVES;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.ves.engine.load.VESLoader.VESLoaderStatus;
import com.helger.phive.ves.model.v1.VES1Marshaller;
import com.helger.phive.ves.v10.VesLicenseType;
import com.helger.phive.ves.v10.VesResourceType;
import com.helger.phive.ves.v10.VesType;
import com.helger.phive.ves.v10.VesXsdCatalogItemPublicType;
import com.helger.phive.ves.v10.VesXsdCatalogItemSystemType;
import com.helger.scuba.api.spi.IUploadContentValidatorSPI;
import com.helger.scuba.phive.codelists.SPDXHelper;

/**
 * Content validator for VES definition files (.ves). Validates JAXB unmarshalling, SPDX licenses,
 * requirement resolution, and XSD catalog entries.
 *
 * @author Philip Helger
 */
@IsSPIImplementation
public final class VesContentValidatorSPI implements IUploadContentValidatorSPI
{
  /** Default constructor. */
  public VesContentValidatorSPI ()
  {}

  private IRepoStorageWithToc m_aRepo;
  private volatile IVESRequirementResolver m_aRequirementResolver;

  @NonNull
  public ICommonsSet <String> getSupportedFileExtensions ()
  {
    return new CommonsHashSet <> (VESLoader.FILE_EXT_VES);
  }

  @Override
  public void initRepoStorage (@NonNull final IRepoStorageWithToc aRepo)
  {
    m_aRepo = aRepo;
  }

  /**
   * @return The resolver for requirements that are not contained in the repository. May be
   *         <code>null</code>, which is the default.
   * @since 1.2.0
   */
  @Nullable
  public IVESRequirementResolver getRequirementResolver ()
  {
    return m_aRequirementResolver;
  }

  /**
   * Set the resolver for <code>requires</code> and <code>partialRequires</code> of an uploaded VES
   * that are not contained in the target repository, e.g. rule sets built into the application that
   * executes the VES. It is passed to {@link VESLoader#setRequirementResolver(IVESRequirementResolver)}
   * and only called if the repository contains no VES for the required VESID. Without a resolver
   * (the default), such a requirement is refused.
   *
   * @param aRequirementResolver
   *        The resolver to use. May be <code>null</code> to only resolve requirements from the
   *        repository.
   * @return this for chaining
   * @since 1.2.0
   */
  @NonNull
  public VesContentValidatorSPI setRequirementResolver (@Nullable final IVESRequirementResolver aRequirementResolver)
  {
    m_aRequirementResolver = aRequirementResolver;
    return this;
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
    final VES1Marshaller aMarshaller = new VES1Marshaller ();
    // Avoid logging - the errors are collected in the error list instead
    aMarshaller.readExceptionCallbacks ().removeAll ();
    final VesType aVes = aMarshaller.setCollectErrors (aErrorList).read (aIS);
    if (aVes == null)
    {
      // The marshaller already added the XSD errors
      if (false)
        aErrorList.add (SingleError.builderError ().errorText ("Failed to read payload as VES - XSD error").build ());
      return false;
    }

    // Check licenses
    {
      final ICommonsSet <String> aUniqueIDs = new CommonsHashSet <> ();
      for (final VesLicenseType aLicence : aVes.getLicense ())
      {
        final String sID = StringHelper.trim (aLicence.getSpdxid ());
        if (StringHelper.isNotEmpty (sID))
        {
          if (!SPDXHelper.isSpdxIDValid (sID))
          {
            aErrorList.add (SingleError.builderError ()
                                       .errorText ("The license SPDX Id '" +
                                                   sID +
                                                   "' is not contained in SPDX list version " +
                                                   SPDXHelper.getListVersion ())
                                       .build ());
            return false;
          }

          if (!aUniqueIDs.add (sID))
          {
            aErrorList.add (SingleError.builderError ()
                                       .errorText ("The license SPDX ID '" + sID + "' is contained more then once")
                                       .build ());
            return false;
          }
        }

        final String sName = StringHelper.trim (aLicence.getValue ());
        if (StringHelper.isEmpty (sName))
        {
          aErrorList.add (SingleError.builderError ().errorText ("The license name is missing").build ());
          return false;
        }

        // A license name may map to more than one SPDX ID
        final ICommonsSet <String> aAllowedIDs = SPDXHelper.getAllSpdxIDsOfName (sName);
        if (aAllowedIDs != null)
        {
          // SPDX IDs are compared case-insensitively
          if (StringHelper.isNotEmpty (sID) && !aAllowedIDs.containsAny (x -> x.equalsIgnoreCase (sID)))
          {
            aErrorList.add (SingleError.builderError ()
                                       .errorText ("The license SPDX ID '" +
                                                   sID +
                                                   "' does not match to the name '" +
                                                   sName +
                                                   "'. Matching IDs to that name are: " +
                                                   aAllowedIDs)
                                       .build ());
            return false;
          }
        }
        else
        {
          // Warning only
          aErrorList.add (SingleError.builderWarn ()
                                     .errorText ("The contained license name '" +
                                                 sName +
                                                 "' is not contained in the SPDX license list")
                                     .build ());
        }
      }
    }

    // VES loader checks, that any required artefact is present as well. Eager loading resolves the
    // requirements now, so that a missing one is detected at upload time. A requirement that is not
    // in the repository may be supplied by the optional requirement resolver.
    final ErrorList aVESErrorList = new ErrorList ();
    final LoadedVES aLoadedVES = new VESLoader (m_aRepo).setUseEagerRequirementLoading (true)
                                                        .setRequirementResolver (m_aRequirementResolver)
                                                        .convertToLoadedVES (ValidationExecutorSetStatus.createValidNow (),
                                                                             aVes,
                                                                             new VESLoaderStatus (),
                                                                             aVESErrorList);
    if (aLoadedVES == null)
    {
      // Transfer VES loader errors to our error list
      aVESErrorList.getAllErrors ()
                   .forEach (x -> aErrorList.add (SingleError.builderError ()
                                                             .errorText ("VES validation error: " +
                                                                         x.getAsStringLocaleIndepdent ())
                                                             .build ()));
      return false;
    }

    // Check if all XSD catalog entries can be resolved
    if (aVes.getXsd () != null)
      if (aVes.getXsd ().getCatalog () != null)
        for (final Object aEntry : aVes.getXsd ().getCatalog ().getPublicOrSystem ())
        {
          final VesResourceType aRes;
          if (aEntry instanceof final VesXsdCatalogItemPublicType aPublic)
            aRes = aPublic.getResource ();
          else
            aRes = ((VesXsdCatalogItemSystemType) aEntry).getResource ();

          final DVRCoordinate aVESID = _createStaticCoordinateOrNull (aRes.getGroupId (),
                                                                      aRes.getArtifactId (),
                                                                      aRes.getVersion ());
          if (aVESID == null)
          {
            aErrorList.add (SingleError.builderError ()
                                       .errorText ("Catalog entry '" +
                                                   aRes.getGroupId () +
                                                   ":" +
                                                   aRes.getArtifactId () +
                                                   ":" +
                                                   aRes.getVersion () +
                                                   "' is not a valid coordinate with a static version")
                                       .build ());
            return false;
          }

          // XSD Catalogue can only reference XSD stuff
          if (!m_aRepo.exists (RepoStorageKeyOfArtefact.of (aVESID, DefaultVESLoaderXSD.FILE_EXT_XSD)))
          {
            aErrorList.add (SingleError.builderError ()
                                       .errorText ("Catalog entry '" +
                                                   aVESID.getAsSingleID () +
                                                   "' with file ext '" +
                                                   DefaultVESLoaderXSD.FILE_EXT_XSD +
                                                   "' does not exist")
                                       .build ());
            return false;
          }
        }

    return true;
  }
}
