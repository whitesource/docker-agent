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
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.agent.api.model.DependencyInfo;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;


/**
 * This class sends execute commands for getting a list of packages (Debian / RPM) to a docker container and parses the output.
 *
 * @author tom.shapira
 */
public class ContainerPackageExtractor {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(DockerAgent.class);

    private static final String[] DEBIAN_PACKAGES_LIST_COMMAND = new String[] { "dpkg", "-l" };
    private static final String[] RPM_PACKAGES_LIST_COMMAND = new String[] { "rpm", "-qa" };
    private static final String[] ALPINE_PACKAGES_LIST_COMMAND = new String[] { "apk", "-vv", "info" };

    // reference: http://askubuntu.com/questions/18804/what-do-the-various-dpkg-flags-like-ii-rc-mean/18807#18807
    private static final String DEBIAN_INSTALLED_PACKAGE_PREFIX = "ii";

    private static final int DEBIAN_PACKAGE_NAME_INDEX = 0;
    private static final int DEBIAN_PACKAGE_VERSION_INDEX = 1;
    private static final int DEBIAN_PACKAGE_ARCH_INDEX = 2;
    private static final String DEBIAN_PACKAGE_PATTERN = "{0}_{1}_{2}.deb";
    private static final String RPM_PACKAGE_PATTERN = "{0}.rpm";
    private static final String ALPINE_PACKAGE_PATTERN = "{0}.apk";
    private static final String ALPINE_PACKAGE_SPLIT_PATTERN = " - ";
    private static final String WHITE_SPACE = " ";

    private static final String COLON = ":";
    private static final String NON_ASCII_CHARS = "[^\\x20-\\x7e]";
    private static final String EMPTY_STRING = "";

    /* --- Public methods --- */

    /**
     * Get all Debian packages by executing "dpkg -l" in a container and parsing the output.
     */
    public static Collection<DependencyInfo> extractDebianPackages(DockerClient dockerClient, String containerId) {
        Collection<DependencyInfo> debianPackages = new ArrayList<>();

        // create execute command
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withCmd(DEBIAN_PACKAGES_LIST_COMMAND).exec();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(execResponse.getId())
                    .withDetach(false).withTty(false)
                    .withExecId(execResponse.getId())
                    .exec(new ExecStartResultCallback(outputStream, System.err)).awaitCompletion();
        } catch (InterruptedException e) {
            logger.warn("Error writing output: {}", e.getMessage());
        }

        // parse debian packages
        String linesStr = new String(outputStream.toByteArray());
        String[] lines = linesStr.split("\\r?\\n");
        for (String line : lines) {
            line = line.replaceAll(NON_ASCII_CHARS, EMPTY_STRING);
            if (line.startsWith(DEBIAN_INSTALLED_PACKAGE_PREFIX)) {
                List<String> args = new ArrayList<>();
                for (String s : line.split(WHITE_SPACE)) {
                    if (StringUtils.isNotBlank(s) && !s.equals(DEBIAN_INSTALLED_PACKAGE_PREFIX)) {
                        args.add(s);
                    }
                }

                if (args.size() >= 3) {
                    // names may contain the arch (i.e. package_name:amd64) - remove it
                    String name = args.get(DEBIAN_PACKAGE_NAME_INDEX);
                    if (name.contains(COLON)) {
                        name = name.substring(0, name.indexOf(COLON));
                    }

                    // versions may contain a
                    String version = args.get(DEBIAN_PACKAGE_VERSION_INDEX);
                    if (version.contains(COLON)) {
                        version = version.substring(version.indexOf(COLON) + 1);
                    }

                    String arch = args.get(DEBIAN_PACKAGE_ARCH_INDEX);
                    debianPackages.add(new DependencyInfo(
                            null, MessageFormat.format(DEBIAN_PACKAGE_PATTERN, name, version, arch), version));
                }
            }
        }

        try {
            outputStream.close();
        } catch (IOException e) {
            logger.warn("Error reading output: {}", e.getMessage());
        }
        return debianPackages;
    }

    /**
     * Get all RPM packages by executing "rpm -qa" in a container and parsing the output.
     */
    public static Collection<DependencyInfo> extractRpmPackages(DockerClient dockerClient, String containerId) {
        Collection<DependencyInfo> rpmPackages = new ArrayList<>();

        // create execute command
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(containerId)
                .withAttachStdout(true)
                .withCmd(RPM_PACKAGES_LIST_COMMAND).exec();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(execResponse.getId())
                    .withDetach(false).withTty(false)
                    .withExecId(execResponse.getId())
                    .exec(new ExecStartResultCallback(outputStream, System.err)).awaitCompletion();
        } catch (InterruptedException e) {
            logger.warn("Error writing output: {}", e.getMessage());
        }

        // parse rpm packages
        String linesStr = new String(outputStream.toByteArray());
        String[] lines = linesStr.split("\\r?\\n");
        for (String line : lines) {
            if (StringUtils.isNotBlank(line)) {
                rpmPackages.add(new DependencyInfo(null, MessageFormat.format(RPM_PACKAGE_PATTERN, line), null));
            }
        }

        try{
            outputStream.close();
        } catch (IOException e) {
            logger.warn("Error reading output: {}", e.getMessage());
        }
        return rpmPackages;
    }

    /**
     * Get all Alpine packages by executing "apk info -vv" in a container and parsing the output.
     */
    public static Collection<DependencyInfo> extractAlpinePackages(DockerClient dockerClient, String containerId) {
        Collection<DependencyInfo> alpinePackages = new ArrayList<>();

        // create execute command
        ExecCreateCmdResponse execResponse = dockerClient.execCreateCmd(String.valueOf(containerId))
                .withAttachStdout(true)
                .withCmd(ALPINE_PACKAGES_LIST_COMMAND).exec();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            dockerClient.execStartCmd(execResponse.getId())
                    .withDetach(false).withTty(false)
                    .withExecId(execResponse.getId())
                    .exec(new ExecStartResultCallback(outputStream, System.err)).awaitCompletion();
        } catch (InterruptedException e) {
            logger.warn("Error writing output: {}", e.getMessage());
        }

        // parse rpm packages
        String linesStr = new String(outputStream.toByteArray());
        String[] lines = linesStr.split("\\r?\\n");
        for (String line : lines) {
            line = line.replaceAll(NON_ASCII_CHARS, EMPTY_STRING);
            if (line.contains(ALPINE_PACKAGE_SPLIT_PATTERN)) {
                String[] split = line.split(ALPINE_PACKAGE_SPLIT_PATTERN);
                if (split.length > 0) {
                    alpinePackages.add(new DependencyInfo(null, MessageFormat.format(ALPINE_PACKAGE_PATTERN, split[0]), null));
                }
            }
        }

        try{
            outputStream.close();
        } catch (IOException e) {
            logger.warn("Error reading output: {}", e.getMessage());
        }
        return alpinePackages;
    }
}