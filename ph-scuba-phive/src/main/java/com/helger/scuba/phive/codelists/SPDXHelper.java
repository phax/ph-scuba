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
package com.helger.scuba.phive.codelists;

import java.util.Locale;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.annotation.Nonempty;
import com.helger.annotation.concurrent.Immutable;
import com.helger.base.exception.InitializationException;
import com.helger.base.string.StringHelper;
import com.helger.collection.commons.CommonsHashMap;
import com.helger.collection.commons.CommonsHashSet;
import com.helger.collection.commons.ICommonsMap;
import com.helger.collection.commons.ICommonsSet;
import com.helger.io.resource.ClassPathResource;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.serialize.MicroReader;

/**
 * Helper to access the SPDX list of open source licenses
 *
 * @author Philip Helger
 */
@Immutable
public final class SPDXHelper
{
  private static final ICommonsMap <String, String> ID_TO_NAME = new CommonsHashMap <> ();
  private static final ICommonsMap <String, ICommonsSet <String>> NAME_TO_IDS = new CommonsHashMap <> ();
  private static final String LIST_VERSION;

  @NonNull
  private static String _unify (@NonNull final String s)
  {
    return s.toLowerCase (Locale.ROOT);
  }

  static
  {
    final IMicroDocument aDoc = MicroReader.readMicroXML (new ClassPathResource ("codelists/spdx.xml",
                                                                                 SPDXHelper.class.getClassLoader ()));
    if (aDoc == null || aDoc.getDocumentElement () == null)
      throw new InitializationException ("Failed to read contained SPDX list");

    LIST_VERSION = aDoc.getDocumentElement ().getAttributeValue ("licenseListVersion");
    if (StringHelper.isEmpty (LIST_VERSION))
      throw new InitializationException ("The contained SPDX list has no list version attribute");

    for (final IMicroElement eItem : aDoc.getDocumentElement ().getAllChildElements ())
    {
      final String sID = eItem.getAttributeValue ("id");
      final String sName = eItem.getAttributeValue ("name");

      if (ID_TO_NAME.put (_unify (sID), sName) != null)
        throw new IllegalStateException ("ID '" + sID + "' is already contained");
      if (!NAME_TO_IDS.computeIfAbsent (_unify (sName), k -> new CommonsHashSet <> ()).add (sID))
        throw new IllegalStateException ("ID '" + sID + "' is already contained in name '" + sName + "'");
    }
  }

  private SPDXHelper ()
  {}

  @NonNull
  @Nonempty
  public static String getListVersion ()
  {
    return LIST_VERSION;
  }

  public static boolean isSpdxIDValid (@Nullable final String sID)
  {
    if (StringHelper.isEmpty (sID))
      return false;
    return ID_TO_NAME.containsKey (_unify (sID));
  }

  @Nullable
  public static String getSpdxNameOfID (@Nullable final String sID)
  {
    if (StringHelper.isEmpty (sID))
      return null;
    return ID_TO_NAME.get (_unify (sID));
  }

  public static boolean isSpdxNameValid (@Nullable final String sName)
  {
    if (StringHelper.isEmpty (sName))
      return false;
    return NAME_TO_IDS.containsKey (_unify (sName));
  }

  @Nullable
  public static ICommonsSet <String> getAllSpdxIDsOfName (@Nullable final String sName)
  {
    if (StringHelper.isEmpty (sName))
      return null;
    return NAME_TO_IDS.get (_unify (sName));
  }
}
