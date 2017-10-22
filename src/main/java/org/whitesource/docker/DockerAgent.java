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
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.SaveImageCmd;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.jaxrs.JerseyDockerCmdExecFactory;
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
import java.text.MessageFormat;
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

    public static final String TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "WhiteSource-Docker";
    public static final String ARCHIVE_EXTRACTOR_TEMP_FOLDER = System.getProperty("java.io.tmpdir") + File.separator + "WhiteSource-ArchiveExtractor";
    public static final String TAR_SUFFIX = ".tar";
    public static final int SHORT_CONTAINER_ID_LENGTH = 12;
    public static final String UNIX_FILE_SEPARATOR = "/";
    public static final String DOCKER_NAME_FORMAT_STRING = "{0} {1} ({2})";
    public static final MessageFormat DOCKER_NAME_FORMAT = new MessageFormat(DOCKER_NAME_FORMAT_STRING);

    // docker client configuration
    public static final int TIMEOUT = 300000;
    public static final int MAX_TOTAL_CONNECTIONS = 100;
    public static final int MAX_PER_ROUTE_CONNECTIONS = 10;

    // property keys for the configuration file
    public static final String DOCKER_API_VERSION = "docker.apiVersion";
    public static final String DOCKER_URL = "docker.url";
    public static final String DOCKER_CERT_PATH = "docker.certPath";
    public static final String DOCKER_WITH_TLS_VERIFY = "docker.withDockerTlsVerify";
    public static final String DOCKER_USERNAME = "docker.username";
    public static final String DOCKER_PASSWORD = "docker.password";
    public static final String DOCKER_READ_TIMEOUT = "docker.readTimeOut";
    public static final String DOCKER_CONNECTION_TIMEOUT = "docker.connectionTimeOut";

    // directory scanner defaults
    public static final int ARCHIVE_EXTRACTION_DEPTH = 2;
    public static final boolean CASE_SENSITIVE_GLOB = false;
    public static final boolean FOLLOW_SYMLINKS = false;
    public static final boolean PARTIAL_SHA1_MATCH = false;

    public static final String AGENT_TYPE = "docker-agent";
    public static final String AGENT_VERSION = "2.3.9";
    private static final String PLUGIN_VERSION = "1.0.7";

    public static final String WINDOWS_PATH_SEPARATOR = "\\";
    public static final String UNIX_PATH_SEPARATOR = "/";

    /* --- Members --- */

    private final CommandLineArgs commandLineArgs;

    /* --- Constructors --- */

    public DockerAgent(Properties config) {
        this(config, new CommandLineArgs());
    }

    public DockerAgent(Properties config, CommandLineArgs commandLineArgs) {
        super(config);
        this.commandLineArgs = commandLineArgs;
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

    @Override
    protected String getPluginVersion() {
        return PLUGIN_VERSION;
    }

    /* --- Private methods --- */

    /**
     * Build the docker client with all the provided properties.
     */
    private DockerClient buildDockerClient() {
        DefaultDockerClientConfig.Builder configBuilder = DefaultDockerClientConfig.createDefaultConfigBuilder();

        final String dockerApiVersion = config.getProperty(DOCKER_API_VERSION);
        if (StringUtils.isNotBlank(dockerApiVersion)) {
            logger.info("api version: {}", dockerApiVersion);
            configBuilder.withApiVersion(dockerApiVersion);

        }

        String dockerUrl = config.getProperty(DOCKER_URL);
        if (StringUtils.isBlank(dockerUrl)) {
            logger.error("Missing Docker URL");
            return null;
        } else {
            logger.info("Docker URL: {}", dockerUrl);
            configBuilder.withDockerHost(dockerUrl);
        }

        String dockerTlsVerify = config.getProperty(DOCKER_WITH_TLS_VERIFY);
        if (StringUtils.isBlank(dockerTlsVerify)) {
            logger.error("Missing Docker TlsVerify");
        } else {
            logger.info("Docker TlsVerify: {}", dockerTlsVerify);
            configBuilder.withDockerTlsVerify(dockerTlsVerify);
        }
        String dockerCertPath = config.getProperty(DOCKER_CERT_PATH);
        if (StringUtils.isNotBlank(dockerCertPath)) {
            logger.info("Docker certificate path: {}", dockerCertPath);
            configBuilder.withDockerCertPath(dockerCertPath);
        }
        String dockerUsername = config.getProperty(DOCKER_USERNAME);
        if (StringUtils.isNotBlank(dockerUsername)) {
            logger.info("Docker username: {}", dockerUrl);
            configBuilder.withRegistryUsername(dockerUsername);
        }
        String dockerPassword = config.getProperty(DOCKER_PASSWORD);
        if (StringUtils.isNotBlank(dockerPassword)) {
            logger.info("Docker password: {}", dockerPassword);
            configBuilder.withRegistryPassword(dockerPassword);
        }
        Integer readTimeOut = Integer.parseInt(config.getProperty(DOCKER_READ_TIMEOUT, String.valueOf(TIMEOUT)));
        Integer connectionTimeOut = Integer.parseInt(config.getProperty(DOCKER_CONNECTION_TIMEOUT, String.valueOf(TIMEOUT)));
        logger.info("Read timeout is set to {}", readTimeOut);
        logger.info("Connection timeout is set to {}", connectionTimeOut);
        JerseyDockerCmdExecFactory dockerCmdExecFactory = new JerseyDockerCmdExecFactory()
                .withReadTimeout(readTimeOut)
                .withConnectTimeout(connectionTimeOut)
                .withMaxTotalConnections(MAX_TOTAL_CONNECTIONS)
                .withMaxPerRouteConnections(MAX_PER_ROUTE_CONNECTIONS);

        return DockerClientBuilder.getInstance(configBuilder.build())
                .withDockerCmdExecFactory(dockerCmdExecFactory)
                .build();
    }

    /**
     * Create a {@link AgentProjectInfo} for each container:
     *   1. Run "dpkg -l" and "rpm -qa" to extract the Debian and RPM package names.
     *   2. Extract the tar archive and scan with the File System Agent.
     */
    private Collection<AgentProjectInfo> createProjects(DockerClient dockerClient) {
        Collection<AgentProjectInfo> projects = new ArrayList<>();

        CreateContainerResponse forcedContainer = null;
        if (StringUtils.isNotBlank(commandLineArgs.dockerImage)) {
            logger.info("Check if image exists '{}'", commandLineArgs.dockerImage);

            if (!imageExists(dockerClient, commandLineArgs.dockerImage)) {
                logger.info("Pulling image '{}'", commandLineArgs.dockerImage);
                dockerClient.pullImageCmd(commandLineArgs.dockerImage).exec(new PullImageResultCallback()).awaitSuccess();
            } else {
                logger.info("Image found '{}',skip pulling", commandLineArgs.dockerImage);
            }

            logger.info("Creating container");
            final CreateContainerCmd createdContainerCmd = dockerClient.createContainerCmd(commandLineArgs.dockerImage);
            if (commandLineArgs.withCmd.size() != 0) {
                logger.info("Container will be started with '{}' command", commandLineArgs.withCmd);
                createdContainerCmd.withCmd(commandLineArgs.withCmd);
            }

            // enable attach stdin and tty so container won't stop after execution
            createdContainerCmd.withAttachStdin(true);
            createdContainerCmd.withTty(true);

            forcedContainer = createdContainerCmd.exec();
            logger.info("Container '{}' created and starting", forcedContainer.getId());
            dockerClient.startContainerCmd(forcedContainer.getId()).exec();

            // must execute at least one command (touch is the most minimalistic)
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(forcedContainer.getId())
                    .withCmd("touch", "/execStartText.log")
                    .exec();

            try {
                dockerClient.execStartCmd(execCreateCmdResponse.getId())
                        // docker -d parameter
                        .withDetach(true)
                        .exec(new ExecStartResultCallback(System.out, System.err))
                        .awaitCompletion();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        // list containers
        List<Container> containers = dockerClient.listContainersCmd().withShowSize(true).exec();
        if (containers.isEmpty()) {
            logger.info("No active containers");
            return projects;
        }

        boolean containerFoundAfterStart = false;
        for (Container container : containers) {
            String containerId = container.getId().substring(0, SHORT_CONTAINER_ID_LENGTH);
            String containerName = getContainerName(container);
            String image = container.getImage();

            if (forcedContainer != null && !forcedContainer.getId().equalsIgnoreCase(container.getId())) {
                continue;
            }
            containerFoundAfterStart = true;
            logger.info("Processing Container {} {} ({})", image, containerId, containerName);

            // create agent project info
            AgentProjectInfo projectInfo = new AgentProjectInfo();
            projectInfo.setCoordinates(new Coordinates(null, DOCKER_NAME_FORMAT.format(DOCKER_NAME_FORMAT_STRING, image, containerId, containerName), null));
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

            // get Alpine packages
            Collection<DependencyInfo> alpinePackages = ContainerPackageExtractor.extractAlpinePackages(dockerClient, containerId);
            if (!alpinePackages.isEmpty()) {
                projectInfo.getDependencies().addAll(alpinePackages);
                logger.info("Found {} Alpine Packages", alpinePackages.size());
            }

            // get Arch Linux packages
            Collection<DependencyInfo> archLinuxPackages = ContainerPackageExtractor.extractArchLinuxPackages(dockerClient, containerId);
            if (!archLinuxPackages.isEmpty()) {
                projectInfo.getDependencies().addAll(archLinuxPackages);
                logger.info("Found {} Arch Linux Packages", archLinuxPackages.size());
            }

            // export container tar file
            File containerTarFile = new File(TEMP_FOLDER, containerName + TAR_SUFFIX);
            File containerTarExtractDir = new File(TEMP_FOLDER, containerName);
            containerTarExtractDir.mkdir();
            File containerTarArchiveExtractDir = new File(ARCHIVE_EXTRACTOR_TEMP_FOLDER, containerName);
            containerTarArchiveExtractDir.mkdir();

            logger.info("Exporting Container to {} (may take a few minutes)", containerTarFile.getPath());
            SaveImageCmd exportContainerCmd = dockerClient.saveImageCmd(container.getImageId());
            InputStream is = exportContainerCmd.exec();
            try {
                // copy input stream to tar archive
                ExtractProgressIndicator progressIndicator = new ExtractProgressIndicator(containerTarFile, container.getSizeRootFs());
                new Thread(progressIndicator).start();
                FileUtils.copyInputStreamToFile(is, containerTarFile);
                progressIndicator.finished();
                logger.info("Successfully Exported Container to {}", containerTarFile.getPath());

                // extract tar archive
                extractTarArchive(containerTarFile, containerTarExtractDir);

                // scan files
                String extractPath = containerTarExtractDir.getPath();
                List<DependencyInfo> dependencyInfos = new FileSystemScanner(false, null).createDependencies(
                        Arrays.asList(extractPath), null, INCLUDES, EXCLUDES, CASE_SENSITIVE_GLOB,
                        ARCHIVE_EXTRACTION_DEPTH, ARCHIVE_INCLUDES, ARCHIVE_EXCLUDES, false, FOLLOW_SYMLINKS, new ArrayList<String>(), PARTIAL_SHA1_MATCH);

                // modify file paths relative to the container
                for (DependencyInfo dependencyInfo : dependencyInfos) {
                    String systemPath = dependencyInfo.getSystemPath();
                    if (StringUtils.isNotBlank(systemPath)) {
                        String containerRelativePath = systemPath.substring(extractPath.length())
                                .replace(WINDOWS_PATH_SEPARATOR, UNIX_PATH_SEPARATOR);
                        dependencyInfo.setSystemPath(containerRelativePath);
                    }
                }
                projectInfo.getDependencies().addAll(dependencyInfos);
            } catch (IOException e) {
                logger.error("Error exporting container {}: {}", containerId, e.getMessage());
                logger.debug("Error exporting container {}", containerId, e);
            } catch (ArchiverException e) {
                logger.error("Error extracting {}: {}", containerTarFile, e.getMessage());
                logger.debug("Error extracting tar archive", e);
            } finally {
                IOUtils.closeQuietly(is);
                FileUtils.deleteQuietly(containerTarFile);
                FileUtils.deleteQuietly(containerTarExtractDir);
                FileUtils.deleteQuietly(containerTarArchiveExtractDir);
            }
        }

        if (forcedContainer != null && containerFoundAfterStart) {
            logger.info("Cleaning created container");
            dockerClient.stopContainerCmd(forcedContainer.getId()).exec();
            dockerClient.removeContainerCmd(forcedContainer.getId()).exec();
        }
        return projects;
    }

    private boolean imageExists(DockerClient dockerClient, String dockerImage) {
        List<Image> images = dockerClient.listImagesCmd().exec();
        for (Image image : images) {
            if (image.getRepoTags().length > 0 && image.getRepoTags()[0].startsWith(dockerImage))
                return true;
        }
        return false;
    }

    /**
     * Extract matching files from the tar archive.
     */
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

    /**
     * Get the container's name.
     */
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