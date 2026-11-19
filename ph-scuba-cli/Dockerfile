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

# Dockerfile for the ph-scuba command line client
# Build the module first ("mvn clean install"), so that target/ contains exactly one shaded JAR.

FROM eclipse-temurin:25-jre

LABEL maintainer="Philip Helger <philip@helger.com>"
LABEL org.opencontainers.image.title="ph-scuba"
LABEL org.opencontainers.image.description="Command line client of ph-scuba to check and upload artefacts to ph-diver repositories"
LABEL org.opencontainers.image.url="https://github.com/phax/ph-scuba"
LABEL org.opencontainers.image.licenses="Apache-2.0"

COPY target/ph-scuba-cli-*-shaded.jar /opt/ph-scuba/ph-scuba-cli.jar

# Mount the files and the repository to work on here, e.g. "-v $PWD:/data"
WORKDIR /data

ENTRYPOINT ["java", "-jar", "/opt/ph-scuba/ph-scuba-cli.jar"]
