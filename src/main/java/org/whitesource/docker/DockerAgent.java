/**
 * Copyright (C) 2016 WhiteSource Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.whitesource.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.ExportContainerCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.jaxrs.DockerCmdExecFactoryImpl;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.codehaus.plexus.archiver.ArchiverException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.CommandLineAgent;
import org.whitesource.agent.FileSystemScanner;
import org.whitesource.agent.api.model.AgentProjectInfo;
import org.whitesource.agent.api.model.Coordinates;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.util.*;

import static org.whitesource.docker.ExtensionUtils.*;

/**
 * Scans Docker containers and sends requests to the WhiteSource service.
 *
 * @author tom.shapira
 */
public class DockerAgent extends CommandLineAgent {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(DockerAgent.class);

    private static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + "WhiteSource_Docker";
    private static final String TAR_SUFFIX = ".tar";
    private static final int SHORT_CONTAINER_ID_LENGTH = 12;
    private static final String UNIX_FILE_SEPARATOR = "/";

    // docker client configuration
    private static final int TIMEOUT = 1000;
    private static final int MAX_TOTAL_CONNECTIONS = 100;
    private static final int MAX_PER_ROUTE_CONNECTIONS = 10;

    // property keys for the configuration file
    public static final String DOCKER_URL = "docker.url";
    public static final String DOCKER_CERT_PATH = "docker.certPath";
    public static final String DOCKER_USERNAME = "docker.username";
    public static final String DOCKER_PASSWORD = "docker.password";

    // directory scanner defaults
    private static final int ARCHIVE_EXTRACTION_DEPTH = 2;
    private static final boolean CASE_SENSITIVE_GLOB = false;
    private static final boolean FOLLOW_SYMLINKS = false;
    private static final boolean PARTIAL_SHA1_MATCH = false;

    public static final String AGENT_TYPE = "docker-agent";
    public static final String AGENT_VERSION = "2.2.6";

    /* --- Constructors --- */

    public DockerAgent(Properties config) {
        super(config);
    }

    /* --- Overridden methods --- */

    @Override
    protected Collection<AgentProjectInfo> createProjects() {
        DockerClient dockerClient = buildDockerClient();
        if (dockerClient == null) {
            logger.error("Error creating docker client, exiting");
            return Collections.emptyList();
        } else {
            return createProjects(dockerClient);
        }
    }

    @Override
    protected String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    protected String getAgentVersion() {
        return AGENT_VERSION;
    }

    /* --- Private methods --- */

    private DockerClient buildDockerClient() {
        DockerClientConfig.DockerClientConfigBuilder configBuilder = DockerClientConfig.createDefaultConfigBuilder();
        String dockerUrl = config.getProperty(DOCKER_URL);
        if (StringUtils.isBlank(dockerUrl)) {
            logger.error("Missing Docker URL");
            return null;
        } else {
            logger.info("Docker URL is {}", dockerUrl);
            configBuilder.withUri(dockerUrl);
        }

        String dockerCertPath = config.getProperty(DOCKER_CERT_PATH);
        if (StringUtils.isNotBlank(dockerCertPath)) {
            logger.info("Docker cert path is {}", dockerCertPath);
            configBuilder.withDockerCertPath(dockerCertPath);
        }
        String dockerUsername = config.getProperty(DOCKER_USERNAME);
        if (StringUtils.isNotBlank(dockerUsername)) {
            logger.info("Docker username is {}", dockerUrl);
            configBuilder.withUsername(dockerUsername);
        }
        String dockerPassword = config.getProperty(DOCKER_PASSWORD);
        if (StringUtils.isNotBlank(dockerPassword)) {
            logger.info("Docker password is {}", dockerPassword);
            configBuilder.withPassword(dockerPassword);
        }

        DockerCmdExecFactoryImpl dockerCmdExecFactory = new DockerCmdExecFactoryImpl()
                .withReadTimeout(TIMEOUT)
                .withConnectTimeout(TIMEOUT)
                .withMaxTotalConnections(MAX_TOTAL_CONNECTIONS)
                .withMaxPerRouteConnections(MAX_PER_ROUTE_CONNECTIONS);

        return DockerClientBuilder.getInstance(configBuilder.build())
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();
    }

    private Collection<AgentProjectInfo> createProjects(DockerClient dockerClient) {
        Collection<AgentProjectInfo> projects = new ArrayList<>();

        // list containers
        List<Container> containers = dockerClient.listContainersCmd().withShowSize(true).exec();
        if (containers.isEmpty()) {
            logger.info("No active containers");
            return projects;
        }

        containers.stream().forEach(container -> {
            String containerId = container.getId().substring(0, SHORT_CONTAINER_ID_LENGTH);
            String containerName = getContainerName(container);
            logger.info("Processing Container {} ({})", containerId, containerName);

            // create agent project info
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            projectInfo.setCoordinates(new Coordinates(null,  containerName, containerId));
            projects.add(projectInfo);

            // get debian packages
            Collection<DependencyInfo> debianPackages = ContainerPackageExtractor.extractDebianPackages(dockerClient, containerId);
            if (!debianPackages.isEmpty()) {
                projectInfo.getDependencies().addAll(debianPackages);
                logger.info("Found {} Debian Packages", debianPackages.size());
            }

            // get RPM packages (just in case)
            Collection<DependencyInfo> rpmPackages = ContainerPackageExtractor.extractRpmPackages(dockerClient, containerId);
            if (!rpmPackages.isEmpty()) {
                projectInfo.getDependencies().addAll(rpmPackages);
                logger.info("Found {} RPM Packages", rpmPackages.size());
            }

            // export container tar file
            File containerTarFile = new File(TEMP_FOLDER, containerName + TAR_SUFFIX);
            File containerTarExtractDir = new File(TEMP_FOLDER, containerName);
            logger.info("Exporting Container to {} (may take a few minutes)", containerTarFile.getPath());
            ExportContainerCmd exportContainerCmd = dockerClient.exportContainerCmd(containerId);
            InputStream is = exportContainerCmd.exec();
            try {
                // copy input stream to tar archive
                ExtractProgressIndicator progressIndicator = new ExtractProgressIndicator(containerTarFile, container.getSize());
                new Thread(progressIndicator).start();
                FileUtils.copyInputStreamToFile(is, containerTarFile);
                progressIndicator.finished();

                // extract tar archive
                extractTarArchive(containerTarFile, containerTarExtractDir);

                // scan files
                List<DependencyInfo> dependencyInfos = new FileSystemScanner().createDependencyInfos(
                        Arrays.asList(containerTarExtractDir.getPath()), null, INCLUDES, EXCLUDES, CASE_SENSITIVE_GLOB,
                        ARCHIVE_EXTRACTION_DEPTH, ARCHIVE_INCLUDES, ARCHIVE_EXCLUDES, FOLLOW_SYMLINKS, Collections.emptyList(), PARTIAL_SHA1_MATCH);
                projectInfo.getDependencies().addAll(dependencyInfos);
            } catch (IOException e) {
                logger.error("Error exporting container {}", containerId);
            } catch (ArchiverException e) {
                logger.error("Error extracting {}", containerTarFile);
            } finally {
                IOUtils.closeQuietly(is);
                FileUtils.deleteQuietly(containerTarFile);
                FileUtils.deleteQuietly(containerTarExtractDir);
            }
        });
        return projects;
    }

    public void extractTarArchive(File containerTarFile, File containerTarExtractDir) {
        TarArchiveInputStream tais = null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(containerTarFile);
            tais = new TarArchiveInputStream(fis);
            ArchiveEntry entry = tais.getNextEntry();
            while (entry != null) {
                if (!entry.isDirectory()) {
                    String entryName = entry.getName();
                    String lowerCaseName = entryName.toLowerCase();
                    if (lowerCaseName.matches(SOURCE_FILE_PATTERN) || lowerCaseName.matches(BINARY_FILE_PATTERN) ||
                            lowerCaseName.matches(ARCHIVE_FILE_PATTERN)) {
                        File file = new File(containerTarExtractDir, entryName);
                        File parent = file.getParentFile();
                        if (!parent.exists()) {
                            parent.mkdirs();
                        }
                        OutputStream out = new FileOutputStream(file);
                        IOUtils.copy(tais, out);
                        out.close();
                    }
                }
                entry = tais.getNextTarEntry();
            }
        } catch (FileNotFoundException e) {
            logger.warn("Error extracting files from {}: {}", containerTarFile.getPath(), e.getMessage());
        } catch (IOException e) {
            logger.warn("Error extracting files from {}: {}", containerTarFile.getPath(), e.getMessage());
        } finally {
            IOUtils.closeQuietly(tais);
            IOUtils.closeQuietly(fis);
        }
    }

    private String getContainerName(Container container) {
        StringBuilder sb = new StringBuilder();
        for (String name : container.getNames()) {
            sb.append(name);
        }
        String containerName = sb.toString();

        // remove first "/" from name
        if (containerName.startsWith(UNIX_FILE_SEPARATOR)) {
            containerName = containerName.substring(1);
        }
        return containerName;
    }
}