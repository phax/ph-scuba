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

import java.io.File;
import java.io.IOException;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.helger.base.state.EChange;
import com.helger.phive.ves.engine.load.VESLoader;
import com.helger.phive.ves.model.v1.VES1Marshaller;
import com.helger.phive.ves.v10.VesType;
import com.helger.scuba.api.repo.ScubaException;
import com.helger.scuba.phive.upload.PhiveUploader;

import picocli.CommandLine.Command;

/**
 * The command <code>ves add</code>: check a VES file and upload it to the coordinate contained in
 * it.
 *
 * @author Philip Helger
 * @since 1.2.0
 */
@Command (name = "add",
          mixinStandardHelpOptions = true,
          description = "Check a VES file and upload it. The coordinate is taken from the file.")
public final class VesAddCommand extends AbstractVesUploadCommand <VesType>
{
  /** Constructor. Called by picocli. */
  public VesAddCommand ()
  {}

  @Override
  @NonNull
  protected String getFileExt ()
  {
    return VESLoader.FILE_EXT_VES;
  }

  @Override
  @NonNull
  protected String getArtefactName ()
  {
    return "VES";
  }

  @Override
  @Nullable
  protected VesType readFile (@NonNull final File aFile)
  {
    return new VES1Marshaller ().read (aFile);
  }

  @Override
  @NonNull
  protected String [] getCoordinateParts (@NonNull final VesType aObj)
  {
    return new String [] { aObj.getGroupId (), aObj.getArtifactId (), aObj.getVersion () };
  }

  @Override
  @NonNull
  protected EChange upload (@NonNull final PhiveUploader aUploader, @NonNull final VesType aObj) throws IOException,
                                                                                                    ScubaException
  {
    return aUploader.addVES (aObj);
  }
}
