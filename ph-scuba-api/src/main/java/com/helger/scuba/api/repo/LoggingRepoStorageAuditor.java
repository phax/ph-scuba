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
package com.helger.scuba.api.repo;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.helger.base.state.ESuccess;
import com.helger.diver.repo.IRepoStorage;
import com.helger.diver.repo.IRepoStorageAuditor;
import com.helger.diver.repo.IRepoStorageContent;
import com.helger.diver.repo.RepoStorageKey;

public class LoggingRepoStorageAuditor implements IRepoStorageAuditor
{
  private static final Logger LOGGER = LoggerFactory.getLogger (LoggingRepoStorageAuditor.class);

  public void onRead (@NonNull final IRepoStorage aRepo,
                      @NonNull final RepoStorageKey aKey,
                      @NonNull final ESuccess eSuccess)
  {
    LOGGER.info ("[REPO-READ] " + aRepo.getID () + " - " + aKey.getPath () + " - " + eSuccess);
  }

  public void onWrite (@NonNull final IRepoStorage aRepo,
                       @NonNull final RepoStorageKey aKey,
                       @NonNull final IRepoStorageContent aContent,
                       @NonNull final ESuccess eSuccess)
  {
    LOGGER.info ("[REPO-WRITE] " + aRepo.getID () + " - " + aKey.getPath () + " - " + eSuccess);
  }

  public void onDelete (@NonNull final IRepoStorage aRepo,
                        @NonNull final RepoStorageKey aKey,
                        @NonNull final ESuccess eSuccess)
  {
    LOGGER.info ("[REPO-DELETE] " + aRepo.getID () + " - " + aKey.getPath () + " - " + eSuccess);
  }
}
