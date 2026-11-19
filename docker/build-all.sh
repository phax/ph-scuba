#!/bin/bash
#
# Copyright (C) 2026 Philip Helger (www.helger.com)
# philip[at]helger[dot]com
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

# Builds and pushes the Docker image of the command line client for amd64 and arm64.
# Run it from this directory, after "mvn install" of the released version.

version=1.2.0

#echo Docker login
#echo "$DOCKER_TOKEN" | docker login -u "$DOCKER_USER" --password-stdin

set -e

# The Dockerfile copies the shaded JAR from target/ - it must be exactly the one of this version
jars=(../ph-scuba-cli/target/ph-scuba-cli-*-shaded.jar)
if [ ${#jars[@]} -ne 1 ] || [ "${jars[0]}" != "../ph-scuba-cli/target/ph-scuba-cli-$version-shaded.jar" ]; then
  echo "Expected exactly ph-scuba-cli/target/ph-scuba-cli-$version-shaded.jar, found: ${jars[*]}"
  exit 1
fi

echo Starting buildx
docker buildx rm ph-scuba || true
docker buildx create --name ph-scuba --driver docker-container

docker buildx build --builder ph-scuba --platform=linux/amd64,linux/arm64 --push --pull -t phelger/scuba:$version -t phelger/scuba:latest -f ../ph-scuba-cli/Dockerfile ../ph-scuba-cli
