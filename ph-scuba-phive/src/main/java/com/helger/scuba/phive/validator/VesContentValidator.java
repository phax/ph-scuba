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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsSet;
import com.helger.diagnostics.error.list.ErrorList;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.RepoStorageKeyOfArtefact;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.phive.api.executorset.status.ValidationExecutorSetStatus;
import com.helger.phive.ves.engine.load.DefaultVESLoaderXSD;
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
 * Content validator for VES definition files (.ves). Validates JAXB
 * unmarshalling, SPDX licenses, requirement resolution, and XSD catalog
 * entries.
 *
 * @author Philip Helger
 */
public final class VesContentValidator implements IUploadContentValidatorSPI
{
  private static final Logger LOGGER = LoggerFactory.getLogger (VesContentValidator.class);

  private IRepoStorageWithToc m_aRepo;

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

  public boolean isValidContent (@NonNull final String sFileExt, @NonNull final InputStream aIS)
  {
    final VesType aVes = new VES1Marshaller ().read (aIS);
    if (aVes == null)
    {
      LOGGER.error ("Failed to read payload as VES - XSD error");
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
            LOGGER.error ("The license SPDX Id '" +
                          sID +
                          "' is not contained in SPDX list version " +
                          SPDXHelper.getListVersion ());
            return false;
          }

          if (!aUniqueIDs.add (sID))
          {
            LOGGER.error ("The license SPDX ID '" + sID + "' is contained more then once");
            return false;
          }
        }

        final String sName = StringHelper.trim (aLicence.getValue ());
        if (StringHelper.isEmpty (sName))
        {
          LOGGER.error ("The license name is missing");
          return false;
        }

        final ICommonsSet <String> aAllowedIDs = SPDXHelper.getAllSpdxIDsOfName (sName);
        if (aAllowedIDs != null)
        {
          if (StringHelper.isNotEmpty (sID) && !aAllowedIDs.contains (sID))
          {
            LOGGER.error ("The license SPDX ID '" +
                          sID +
                          "' does not match to the name '" +
                          sName +
                          "'. Matching IDs to that name are: " +
                          aAllowedIDs);
            return false;
          }
        }
        else
        {
          // Warning only
          LOGGER.warn ("The contained license name '" + sName + "' is not contained in the SPDX license list");
        }
      }
    }

    // VES loader checks, that any required artefact is present as well
    final ErrorList aErrorList = new ErrorList ();
    final LoadedVES aLoadedVES = new VESLoader (m_aRepo).setUseEagerRequirementLoading (true)
                                                        .convertToLoadedVES (ValidationExecutorSetStatus.createValidNow (),
                                                                             aVes,
                                                                             new VESLoaderStatus (),
                                                                             aErrorList);
    if (aLoadedVES == null)
    {
      aErrorList.getAllErrors ()
                .forEach (x -> LOGGER.error ("VES validation error: " + x.getAsStringLocaleIndepdent ()));
      return false;
    }

    // Check if all XSD catalog entries can be resolved
    if (aVes.getXsd () != null)
      if (aVes.getXsd ().getCatalog () != null)
        for (final Object aEntry : aVes.getXsd ().getCatalog ().getPublicOrSystem ())
        {
          final VesResourceType aRes;
          if (aEntry instanceof VesXsdCatalogItemPublicType)
            aRes = ((VesXsdCatalogItemPublicType) aEntry).getResource ();
          else
            aRes = ((VesXsdCatalogItemSystemType) aEntry).getResource ();

          final DVRCoordinate aVESID = new DVRCoordinate (aRes.getGroupId (),
                                                          aRes.getArtifactId (),
                                                          DVRVersion.parseOrNull (aRes.getVersion ()));

          // XSD Catalogue can only reference XSD stuff
          if (!m_aRepo.exists (RepoStorageKeyOfArtefact.of (aVESID, DefaultVESLoaderXSD.FILE_EXT_XSD)))
          {
            LOGGER.error ("Catalog entry '" +
                          aVESID.getAsSingleID () +
                          "' with file ext '" +
                          DefaultVESLoaderXSD.FILE_EXT_XSD +
                          "' does not exist");
            return false;
          }
        }

    return true;
  }
}
