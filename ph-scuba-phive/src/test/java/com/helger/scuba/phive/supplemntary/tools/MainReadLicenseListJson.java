/*
 * Copyright (C) 2024-2026 Philip Helger (www.helger.com)
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
package com.helger.scuba.phive.supplemntary.tools;

import java.io.File;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.httpclient.HttpClientManager;
import com.helger.httpclient.response.ResponseHandlerJsonObject;
import com.helger.json.IJsonArray;
import com.helger.json.IJsonObject;
import com.helger.xml.microdom.IMicroDocument;
import com.helger.xml.microdom.IMicroElement;
import com.helger.xml.microdom.MicroDocument;
import com.helger.xml.microdom.serialize.MicroWriter;

public class MainReadLicenseListJson
{
  private static final Logger LOGGER = LoggerFactory.getLogger (MainReadLicenseListJson.class);

  public static void main (final String [] args) throws Exception
  {
    final String sUrl = "https://github.com/spdx/license-list-data/raw/main/json/licenses.json";
    try (HttpClientManager hcm = new HttpClientManager ())
    {
      final HttpGet get = new HttpGet (sUrl);
      final IJsonObject aJson = hcm.execute (get, new ResponseHandlerJsonObject ());
      final IJsonArray licenses = aJson.getAsArray ("licenses");

      final IMicroDocument aDoc = new MicroDocument ();
      aDoc.addComment (" This file was created with " + MainReadLicenseListJson.class.getName () + " - do not edit! ");
      final IMicroElement eRoot = aDoc.addElement ("root");
      eRoot.setAttribute ("licenseListVersion", aJson.getAsString ("licenseListVersion"));

      for (final IJsonObject lic : licenses.iteratorObjects ())
      {
        final String id = lic.getAsString ("licenseId");
        final String name = lic.getAsString ("name");

        eRoot.addElement ("license").setAttribute ("id", id).setAttribute ("name", name);
      }
      LOGGER.info ("Found " + eRoot.getChildCount () + " licenses");
      MicroWriter.writeToFile (aDoc, new File ("src/main/resources/external/codelists/spdx.xml"));
    }
  }
}
