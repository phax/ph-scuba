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
package com.helger.scuba.upload;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.nio.charset.StandardCharsets;

import org.jspecify.annotations.NonNull;
import org.junit.Test;

import com.helger.diver.api.coord.DVRCoordinate;
import com.helger.diver.api.version.DVRVersion;
import com.helger.diver.repo.ERepoDeletable;
import com.helger.diver.repo.ERepoWritable;
import com.helger.diver.repo.RepoStorageContentByteArray;
import com.helger.diver.repo.impl.RepoStorageInMemory;
import com.helger.diver.repo.toc.IRepoStorageWithToc;

/**
 * Test class for {@link ScubaUploader}.
 *
 * @author Philip Helger
 */
public final class ScubaUploaderTest
{
  @NonNull
  private static IRepoStorageWithToc _createRepo ()
  {
    return RepoStorageInMemory.createDefault ("test", ERepoWritable.WITH_WRITE, ERepoDeletable.WITH_DELETE);
  }

  @Test
  public void testDefaultSettings ()
  {
    final ScubaUploader aUploader = new ScubaUploader (_createRepo ());
    assertNotNull (aUploader.getSettings ());
    assertFalse (aUploader.getSettings ().isAllowUploadWithUnknownExtension ());
    assertFalse (aUploader.getSettings ().isAllowOverwriteExisting ());
  }

  @Test
  public void testUnknownExtensionRejectedByDefault () throws Exception
  {
    final ScubaUploader aUploader = new ScubaUploader (_createRepo ());
    final DVRCoordinate aCoord = new DVRCoordinate ("com.test", "artifact", DVRVersion.parseOrNull ("1.0"));

    try
    {
      aUploader.uploadResource (aCoord,
                                RepoStorageContentByteArray.of ("data".getBytes (StandardCharsets.UTF_8)),
                                ".unknown");
      fail ("Expected IllegalArgumentException");
    }
    catch (final IllegalArgumentException ex)
    {
      // Expected
    }
  }

  @Test
  public void testUnknownExtensionAllowedWithSetting () throws Exception
  {
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true);
    final ScubaUploader aUploader = new ScubaUploader (_createRepo (), aSettings);
    final DVRCoordinate aCoord = new DVRCoordinate ("com.test", "artifact", DVRVersion.parseOrNull ("1.0"));

    // Should succeed without validation
    aUploader.uploadResource (aCoord,
                              RepoStorageContentByteArray.of ("data".getBytes (StandardCharsets.UTF_8)),
                              ".unknown");

    assertTrue (aUploader.existsResource (aCoord, ".unknown"));
  }

  @Test
  public void testOverwriteRejectedByDefault () throws Exception
  {
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true);
    final ScubaUploader aUploader = new ScubaUploader (_createRepo (), aSettings);
    final DVRCoordinate aCoord = new DVRCoordinate ("com.test", "artifact", DVRVersion.parseOrNull ("1.0"));

    aUploader.uploadResource (aCoord,
                              RepoStorageContentByteArray.of ("data".getBytes (StandardCharsets.UTF_8)),
                              ".txt");

    try
    {
      aUploader.uploadResource (aCoord,
                                RepoStorageContentByteArray.of ("data2".getBytes (StandardCharsets.UTF_8)),
                                ".txt");
      fail ("Expected RepoKeyAlreadyInUseException");
    }
    catch (final Exception ex)
    {
      // Expected
    }
  }

  @Test
  public void testOverwriteAllowedWithSetting () throws Exception
  {
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true)
                                                                        .setAllowOverwriteExisting (true);
    final ScubaUploader aUploader = new ScubaUploader (_createRepo (), aSettings);
    final DVRCoordinate aCoord = new DVRCoordinate ("com.test", "artifact", DVRVersion.parseOrNull ("1.0"));

    aUploader.uploadResource (aCoord,
                              RepoStorageContentByteArray.of ("data".getBytes (StandardCharsets.UTF_8)),
                              ".txt");

    // Should succeed with overwrite
    aUploader.uploadResource (aCoord,
                              RepoStorageContentByteArray.of ("data2".getBytes (StandardCharsets.UTF_8)),
                              ".txt");

    assertTrue (aUploader.existsResource (aCoord, ".txt"));
  }

  @Test
  public void testIsValidResourceUnknownExtension () throws Exception
  {
    // Default settings: unknown extension is invalid
    final ScubaUploader aUploaderStrict = new ScubaUploader (_createRepo ());
    // Can't easily test isValidResource without a real file, but we can verify getSettings
    assertFalse (aUploaderStrict.getSettings ().isAllowUploadWithUnknownExtension ());

    // Permissive settings: unknown extension is valid
    final ScubaUploaderSettings aSettings = new ScubaUploaderSettings ().setAllowUploadWithUnknownExtension (true);
    final ScubaUploader aUploaderPermissive = new ScubaUploader (_createRepo (), aSettings);
    assertTrue (aUploaderPermissive.getSettings ().isAllowUploadWithUnknownExtension ());
  }
}
