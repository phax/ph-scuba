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
package com.helger.scuba.api;

import java.io.IOException;

import org.jspecify.annotations.NonNull;

import com.helger.base.state.EValidity;
import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.repo.toc.IRepoStorageWithToc;
import com.helger.io.resource.IReadableResource;
import com.helger.scuba.api.repo.ScubaException;

/**
 * Generic interface for uploading artifacts to a diver-based repository.
 *
 * @author Philip Helger
 */
public interface IScubaUploader
{
  /**
   * @return The repository storage this uploader works on. Never <code>null</code>.
   */
  @NonNull
  IRepoStorageWithToc getRepoStorage ();

  /**
   * Check if the provided resource is valid for uploading. Performs the same content validation as
   * {@link #addResource(DVRCoordinate, IReadableResource)} but without actually uploading.
   *
   * @param aPayload
   *        The payload to check. May not be <code>null</code>.
   * @return {@link EValidity} and never <code>null</code>.
   * @throws IOException
   *         On IO error
   */
  @NonNull
  EValidity isValidResource (@NonNull IReadableResource aPayload) throws IOException;

  /**
   * Upload a single artifact to the repository with the provided coordinates. The content is
   * validated before upload based on the file extension. The file extension is extracted from the
   * resource path.
   *
   * @param aCoordinate
   *        The DVR coordinate to upload to. May not be <code>null</code>.
   * @param aPayload
   *        The payload to upload. May not be <code>null</code>.
   * @throws IOException
   *         On IO error
   * @throws ScubaException
   *         If some scuba constraints don't match
   */
  void addResource (@NonNull DVRCoordinate aCoordinate, @NonNull IReadableResource aPayload) throws IOException,
                                                                                             ScubaException;

  /**
   * Check if a resource with the given coordinate and file extension exists.
   *
   * @param aCoordinate
   *        The DVR coordinate. May not be <code>null</code>.
   * @param sFileExt
   *        The file extension including the leading dot. May not be <code>null</code>.
   * @return {@code true} if the resource exists, {@code false} otherwise.
   */
  boolean existsResource (@NonNull DVRCoordinate aCoordinate, @NonNull String sFileExt);

  /**
   * Delete a resource from the repository.
   *
   * @param aCoordinate
   *        The DVR coordinate. May not be <code>null</code>.
   * @param sFileExt
   *        The file extension including the leading dot. May not be <code>null</code>.
   * @throws IOException
   *         On IO error
   */
  void deleteResource (@NonNull DVRCoordinate aCoordinate, @NonNull String sFileExt) throws IOException;
}
