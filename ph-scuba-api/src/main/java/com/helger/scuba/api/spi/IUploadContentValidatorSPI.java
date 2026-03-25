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
package com.helger.scuba.api.spi;

import java.io.IOException;
import java.io.InputStream;

import org.jspecify.annotations.NonNull;

import com.helger.collection.commons.ICommonsSet;
import com.helger.diver.repo.toc.IRepoStorageWithToc;

/**
 * SPI interface for content validation based on file extension. Implementations
 * are discovered via {@link java.util.ServiceLoader} and registered in
 * {@code META-INF/services/com.helger.scuba.api.spi.IUploadContentValidatorSPI}.
 *
 * @author Philip Helger
 */
public interface IUploadContentValidatorSPI
{
  /**
   * @return The set of file extensions this validator can handle. Each extension
   *         must start with a dot (e.g., ".xsd", ".sch"). Never
   *         <code>null</code> and never empty.
   */
  @NonNull
  ICommonsSet <String> getSupportedFileExtensions ();

  /**
   * Initialize this validator with the repository storage. Called once after SPI
   * discovery.
   *
   * @param aRepo
   *        The repository storage to use for resolution checks. May not be
   *        <code>null</code>.
   */
  default void initRepoStorage (@NonNull final IRepoStorageWithToc aRepo)
  {
    // Default: do nothing. Override if validation needs repository access.
  }

  /**
   * Validate the content of a file with the given extension.
   *
   * @param sFileExt
   *        The file extension including the leading dot (e.g., ".xsd"). Never
   *        <code>null</code>.
   * @param aIS
   *        The input stream to read the content from. Will be closed by the
   *        caller. Never <code>null</code>.
   * @return {@code true} if the content is valid for this file type,
   *         {@code false} otherwise.
   * @throws IOException
   *         On IO error
   */
  boolean isValidContent (@NonNull String sFileExt, @NonNull InputStream aIS) throws IOException;
}
