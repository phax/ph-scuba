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

import org.jspecify.annotations.NonNull;

import com.helger.annotation.concurrent.NotThreadSafe;
import com.helger.annotation.style.ReturnsMutableCopy;
import com.helger.base.enforce.ValueEnforcer;
import com.helger.base.hashcode.HashCodeGenerator;
import com.helger.base.tostring.ToStringGenerator;

/**
 * Settings for the {@link ScubaUploader}. This class acts as both the settings container and the
 * builder (mutable, chainable setters).
 *
 * @author Philip Helger
 */
@NotThreadSafe
public class ScubaUploaderSettings
{
  /** Default: do not allow upload of artifacts with unknown file extensions */
  public static final boolean DEFAULT_ALLOW_UPLOAD_WITH_UNKNOWN_EXTENSION = false;
  /** Default: do not allow overwriting existing artifacts */
  public static final boolean DEFAULT_ALLOW_OVERWRITE_EXISTING = false;

  private boolean m_bAllowUploadWithUnknownExtension = DEFAULT_ALLOW_UPLOAD_WITH_UNKNOWN_EXTENSION;
  private boolean m_bAllowOverwriteExisting = DEFAULT_ALLOW_OVERWRITE_EXISTING;

  public ScubaUploaderSettings ()
  {}

  /**
   * @return {@code true} if artifacts with unknown file extensions (no SPI validator registered)
   *         are allowed to be uploaded without content validation, {@code false} if they should be
   *         rejected. Default is {@value #DEFAULT_ALLOW_UPLOAD_WITH_UNKNOWN_EXTENSION}.
   */
  public final boolean isAllowUploadWithUnknownExtension ()
  {
    return m_bAllowUploadWithUnknownExtension;
  }

  /**
   * Set whether artifacts with unknown file extensions should be allowed to be uploaded without
   * content validation.
   *
   * @param bAllow
   *        {@code true} to allow, {@code false} to reject.
   * @return this for chaining
   */
  @NonNull
  public final ScubaUploaderSettings setAllowUploadWithUnknownExtension (final boolean bAllow)
  {
    m_bAllowUploadWithUnknownExtension = bAllow;
    return this;
  }

  /**
   * @return {@code true} if existing artifacts may be overwritten, {@code false} if uploading to an
   *         existing key should be rejected. Default is {@value #DEFAULT_ALLOW_OVERWRITE_EXISTING}.
   */
  public final boolean isAllowOverwriteExisting ()
  {
    return m_bAllowOverwriteExisting;
  }

  /**
   * Set whether existing artifacts may be overwritten.
   *
   * @param bAllow
   *        {@code true} to allow, {@code false} to reject.
   * @return this for chaining
   */
  @NonNull
  public final ScubaUploaderSettings setAllowOverwriteExisting (final boolean bAllow)
  {
    m_bAllowOverwriteExisting = bAllow;
    return this;
  }

  /**
   * Copy all settings from the provided source.
   *
   * @param aOther
   *        The source settings. May not be <code>null</code>.
   */
  public final void assignFrom (@NonNull final ScubaUploaderSettings aOther)
  {
    ValueEnforcer.notNull (aOther, "Other");
    setAllowUploadWithUnknownExtension (aOther.isAllowUploadWithUnknownExtension ());
    setAllowOverwriteExisting (aOther.isAllowOverwriteExisting ());
  }

  @NonNull
  @ReturnsMutableCopy
  public ScubaUploaderSettings getClone ()
  {
    final ScubaUploaderSettings ret = new ScubaUploaderSettings ();
    ret.assignFrom (this);
    return ret;
  }

  @Override
  public boolean equals (final Object o)
  {
    if (o == this)
      return true;
    if (o == null || !getClass ().equals (o.getClass ()))
      return false;
    final ScubaUploaderSettings rhs = (ScubaUploaderSettings) o;
    return m_bAllowUploadWithUnknownExtension == rhs.m_bAllowUploadWithUnknownExtension &&
           m_bAllowOverwriteExisting == rhs.m_bAllowOverwriteExisting;
  }

  @Override
  public int hashCode ()
  {
    return new HashCodeGenerator (this).append (m_bAllowUploadWithUnknownExtension)
                                       .append (m_bAllowOverwriteExisting)
                                       .getHashCode ();
  }

  @Override
  public String toString ()
  {
    return new ToStringGenerator (this).append ("AllowUploadWithUnknownExtension", m_bAllowUploadWithUnknownExtension)
                                       .append ("AllowOverwriteExisting", m_bAllowOverwriteExisting)
                                       .getToString ();
  }
}
