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

import com.beust.jcommander.Parameter;

/**
 * Command line arguments.
 *
 * @author tom.shapira
 */
public class CommandLineArgs {

    /* --- Static members --- */

    private static final String CONFIG_FILE_NAME = "whitesource-docker-agent.config";

    /* --- Parameters --- */

    @Parameter(names = "-c", description = "Config file path")
    String configFilePath = CONFIG_FILE_NAME;

    @Parameter(names = {"-i", "--image"}, description = "Docker image (-i <image>) to be scanned")
    String dockerImage = "";

    @Parameter(names = { "-w", "--withCmd"}, description = "Starts the container with a specific command (-w <command>) (only works with -i)")
    String withCmd = "";

    @Parameter(names = { "-I", "--interactive"}, description = "Starts the container in interactive mode catching tty & attaching stdin (only works with -i)")
    Boolean interactive = false;
}
