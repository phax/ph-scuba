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
package com.helger.scuba.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.jspecify.annotations.NonNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.helger.config.Config;
import com.helger.config.IConfig;
import com.helger.config.source.MultiConfigurationValueProvider;
import com.helger.config.source.appl.ConfigurationSourceFunction;
import com.helger.http.CHttpHeader;
import com.helger.http.basicauth.BasicAuthClientCredentials;
import com.helger.scuba.cli.CliRunner.CliResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Test the commands with repositories of type <code>http</code> and <code>aws-s3</code> from a
 * repository configuration.
 *
 * @author Philip Helger
 */
public final class RemoteRepositoryTest
{
  private static final String VALID_XSD = "src/test/resources/external/valid.xsd";

  @Rule
  public final TemporaryFolder m_aTempFolder = new TemporaryFolder ();

  private final Map <String, byte []> m_aStore = new ConcurrentHashMap <> ();
  private HttpServer m_aServer;
  private String m_sRequiredAuthorization;

  private void _handle (@NonNull final HttpExchange aExchange) throws Exception
  {
    final String sPath = aExchange.getRequestURI ().getPath ();
    if (m_sRequiredAuthorization != null &&
        !m_sRequiredAuthorization.equals (aExchange.getRequestHeaders ().getFirst (CHttpHeader.AUTHORIZATION)))
    {
      aExchange.sendResponseHeaders (401, -1);
      aExchange.close ();
      return;
    }

    switch (aExchange.getRequestMethod ())
    {
      case "PUT":
      {
        try (final InputStream aIS = aExchange.getRequestBody ())
        {
          m_aStore.put (sPath, aIS.readAllBytes ());
        }
        aExchange.sendResponseHeaders (201, -1);
        break;
      }
      case "HEAD":
        aExchange.sendResponseHeaders (m_aStore.containsKey (sPath) ? 200 : 404, -1);
        break;
      case "GET":
      {
        final byte [] aData = m_aStore.get (sPath);
        if (aData == null)
          aExchange.sendResponseHeaders (404, -1);
        else
        {
          aExchange.sendResponseHeaders (200, aData.length);
          try (final OutputStream aOS = aExchange.getResponseBody ())
          {
            aOS.write (aData);
          }
        }
        break;
      }
      default:
        aExchange.sendResponseHeaders (405, -1);
    }
    aExchange.close ();
  }

  @Before
  public void before () throws Exception
  {
    m_aServer = HttpServer.create (new InetSocketAddress ("localhost", 0), 0);
    m_aServer.createContext ("/", x -> {
      try
      {
        _handle (x);
      }
      catch (final Exception ex)
      {
        x.sendResponseHeaders (500, -1);
        x.close ();
      }
    });
    m_aServer.start ();
  }

  @After
  public void after ()
  {
    m_aServer.stop (0);
  }

  @NonNull
  private String _writeConfig (@NonNull final String sRepositories) throws Exception
  {
    final File aConfig = m_aTempFolder.newFile ();
    Files.writeString (aConfig.toPath (),
                       "<repositories xmlns=\"urn:fdc:com:helger:diver:repoconfig:v1.0\">\n" +
                                          sRepositories +
                                          "</repositories>\n",
                       StandardCharsets.UTF_8);
    return aConfig.getAbsolutePath ();
  }

  @Test
  public void testHttpUpload () throws Exception
  {
    final String sURL = "http://localhost:" + m_aServer.getAddress ().getPort () + "/repo/";
    final String sConfig = _writeConfig ("  <repository id=\"remote\" type=\"http\" writable=\"true\">\n" +
                                         "    <property name=\"url\">" +
                                         sURL +
                                         "</property>\n" +
                                         "    <property name=\"allow-insecure\">true</property>\n" +
                                         "  </repository>\n");

    CliResult aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "remote", "com.test:mini:1.0", VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (m_aStore.keySet ().toString (), m_aStore.containsKey ("/repo/com/test/mini/1/mini-1.xsd"));
    assertTrue (m_aStore.keySet ().toString (), m_aStore.containsKey ("/repo/com/test/mini/1/mini-1.xsd.sha256"));

    // The existence check works remotely
    aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "remote", "com.test:mini:1.0", VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_CONFLICT, aResult.exitCode ());

    assertEquals (ScubaCli.EXIT_OK,
                  CliRunner.run ("exists", "--config", sConfig, "--repo", "remote", "com.test:mini:1.0", "xsd")
                           .exitCode ());
  }

  @Test
  public void testHttpUploadBasicAuth () throws Exception
  {
    m_sRequiredAuthorization = new BasicAuthClientCredentials ("uploader", "secret").getRequestValue ();
    final String sURL = "http://localhost:" + m_aServer.getAddress ().getPort () + "/repo/";
    final String sConfig = _writeConfig ("  <repository id=\"remote\" type=\"http\" writable=\"true\">\n" +
                                         "    <property name=\"url\">" +
                                         sURL +
                                         "</property>\n" +
                                         "    <property name=\"allow-insecure\">true</property>\n" +
                                         "    <property name=\"username\">uploader</property>\n" +
                                         "    <property name=\"password-key\">scuba.test.password</property>\n" +
                                         "  </repository>\n");
    final Map <String, String> aSecrets = Map.of ("scuba.test.password", "secret");
    final IConfig aConfig = Config.create (new MultiConfigurationValueProvider (new ConfigurationSourceFunction (aSecrets::get)));

    // The secret is resolved from the configuration of the client
    CliResult aResult = CliRunner.runWithConfig (aConfig,
                                                 "upload",
                                                 "--config",
                                                 sConfig,
                                                 "--repo",
                                                 "remote",
                                                 "com.test:mini:1.0",
                                                 VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_OK, aResult.exitCode ());
    assertTrue (m_aStore.keySet ().toString (), m_aStore.containsKey ("/repo/com/test/mini/1/mini-1.xsd"));

    // The authenticated existence check finds it
    aResult = CliRunner.runWithConfig (aConfig,
                                       "upload",
                                       "--config",
                                       sConfig,
                                       "--repo",
                                       "remote",
                                       "com.test:mini:1.0",
                                       VALID_XSD);
    assertEquals (aResult.err (), ScubaCli.EXIT_CONFLICT, aResult.exitCode ());

    // Without the secret the repository cannot be created
    aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "remote", "com.test:mini:1.0", VALID_XSD);
    assertEquals (ScubaCli.EXIT_ERROR, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("scuba.test.password"));
    assertFalse (aResult.err (), aResult.err ().contains ("secret'"));
  }

  @Test
  public void testInvalidRemoteConfigurations () throws Exception
  {
    // http:// without allow-insecure
    final String sURL = "http://localhost:" + m_aServer.getAddress ().getPort () + "/repo/";
    String sConfig = _writeConfig ("  <repository id=\"remote\" type=\"http\" writable=\"true\">\n" +
                                   "    <property name=\"url\">" +
                                   sURL +
                                   "</property>\n" +
                                   "  </repository>\n");
    CliResult aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "remote", "com.test:mini:1.0", VALID_XSD);
    assertEquals (ScubaCli.EXIT_ERROR, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("allow-insecure"));
    assertTrue (m_aStore.isEmpty ());

    // S3 without bucket
    sConfig = _writeConfig ("  <repository id=\"s3\" type=\"aws-s3\" writable=\"true\">\n" +
                            "    <property name=\"region\">eu-west-1</property>\n" +
                            "  </repository>\n");
    aResult = CliRunner.run ("upload", "--config", sConfig, "--repo", "s3", "com.test:mini:1.0", VALID_XSD);
    assertEquals (ScubaCli.EXIT_ERROR, aResult.exitCode ());
    assertTrue (aResult.err (), aResult.err ().contains ("bucket"));
  }
}
