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

# Builds the Docker image of the command line client. Run it from this directory.
#
# Usage:
#   ./build-all.sh             Release: after "mvn install" of the released version, build the image
#                              of $version for amd64 and arm64 and push it with the tags $version and latest
#   ./build-all.sh --snapshot  Test: after "mvn install" of a SNAPSHOT version, build the image for the
#                              local platform only and load it into the local Docker with the tag of the
#                              SNAPSHOT version (e.g. phelger/scuba:1.2.0-SNAPSHOT). Nothing is pushed.

version=1.2.0

#echo Docker login
#echo "$DOCKER_TOKEN" | docker login -u "$DOCKER_USER" --password-stdin

set -e

snapshot=false
if [ $# -eq 1 ] && [ "$1" = "--snapshot" ]; then
  snapshot=true
elif [ $# -ne 0 ]; then
  echo "Usage: $0 [--snapshot]"
  exit 1
fi

# The Dockerfile copies the shaded JAR from target/ - there must be exactly one
prefix=../ph-scuba-cli/target/ph-scuba-cli-
suffix=-shaded.jar
jars=(${prefix}*${suffix})
if [ ${#jars[@]} -ne 1 ] || [ ! -f "${jars[0]}" ]; then
  echo "Expected exactly one ph-scuba-cli/target/ph-scuba-cli-*${suffix}, found: ${jars[*]}"
  exit 1
fi

if [ "$snapshot" = true ]; then
  # Take the version from the JAR name
  jarversion=${jars[0]#${prefix}}
  jarversion=${jarversion%${suffix}}
  if [[ "$jarversion" != *-SNAPSHOT ]]; then
    echo "Expected a SNAPSHOT JAR for --snapshot, found: ${jars[0]}"
    exit 1
  fi

  echo "Building phelger/scuba:$jarversion for the local platform"
  docker build --pull -t phelger/scuba:$jarversion -t phelger/scuba:latest -f ../ph-scuba-cli/Dockerfile ../ph-scuba-cli
  echo "Built phelger/scuba:$jarversion - run it with: docker run --rm -v \"\$PWD:/data\" phelger/scuba:$jarversion --version"
  exit 0
fi

# Release: it must be exactly the JAR of this version
if [ "${jars[0]}" != "${prefix}${version}${suffix}" ]; then
  echo "Expected exactly ph-scuba-cli/target/ph-scuba-cli-${version}${suffix}, found: ${jars[*]}"
  exit 1
fi

echo Starting buildx
docker buildx rm ph-scuba || true
docker buildx create --name ph-scuba --driver docker-container

docker buildx build --builder ph-scuba --platform=linux/amd64,linux/arm64 --push --pull -t phelger/scuba:$version -t phelger/scuba:latest -f ../ph-scuba-cli/Dockerfile ../ph-scuba-cli
