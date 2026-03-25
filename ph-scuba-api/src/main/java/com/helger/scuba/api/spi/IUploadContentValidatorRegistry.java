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

import com.helger.diagnostics.error.list.ErrorList;

/**
 * Registry for content validators. Provides a central dispatch point for
 * validating content by file extension, with support for nested context paths
 * (e.g., when validating files inside a ZIP archive).
 * <p>
 * The context path is prepended to error messages so that nested validation
 * errors carry the full path context. For example, validating a file inside a
 * ZIP produces errors like: {@code "archive.zip/inner-dir/schema.xsd: The root element ..."}
 *
 * @author Philip Helger
 */
public interface IUploadContentValidatorRegistry
{
  /**
   * Validate the content of a file with the given extension, using the provided
   * context path for error message prefixing.
   *
   * @param sContextPath
   *        The context path for error messages. May be empty for top-level
   *        validation. For nested validation (e.g., inside a ZIP), this contains
   *        the path to the container (e.g., "archive.zip/subdir/"). Never
   *        <code>null</code>.
   * @param sFileExt
   *        The file extension including the leading dot (e.g., ".xsd"). Never
   *        <code>null</code>.
   * @param aIS
   *        The input stream to read the content from. Never <code>null</code>.
   * @param aErrorList
   *        The error list to collect all validation errors into. Never
   *        <code>null</code>.
   * @return {@code true} if the content is valid, {@code false} otherwise.
   * @throws IOException
   *         On IO error
   */
  boolean validateContent (@NonNull String sContextPath,
                           @NonNull String sFileExt,
                           @NonNull InputStream aIS,
                           @NonNull ErrorList aErrorList) throws IOException;

  /**
   * Check if a validator is registered for the given file extension.
   *
   * @param sFileExt
   *        The file extension including the leading dot. Never
   *        <code>null</code>.
   * @return {@code true} if a validator exists for this extension.
   */
  boolean hasValidatorForExtension (@NonNull String sFileExt);
}
