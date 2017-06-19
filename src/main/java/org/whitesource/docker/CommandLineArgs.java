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
import com.beust.jcommander.converters.IParameterSplitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Command line arguments.
 *
 * @author tom.shapira
 */
public class CommandLineArgs {

    public static class SemiColonSplitter implements IParameterSplitter {
        public List<String> split(String value) {
            return Arrays.asList(value.split(";"));
        }
    }

    /* --- Static members --- */

    private static final String CONFIG_FILE_NAME = "whitesource-docker-agent.config";

    /* --- Parameters --- */

    @Parameter(names = "--help", help = true)
    public boolean help = false;

    @Parameter(names = "-c", description = "Config file path")
    String configFilePath = CONFIG_FILE_NAME;

    @Parameter(names = {"-i", "--image"}, description = "Docker image (-i <image>) to be scanned")
    String dockerImage = "";

    @Parameter(names = {"-w", "--withCmd"}, splitter = SemiColonSplitter.class, description = "Starts the container with specific commands semicolon delimited (-w <command>) (only works with -i)")
    List<String> withCmd = new ArrayList<>();

}
