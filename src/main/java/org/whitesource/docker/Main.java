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

import ch.qos.logback.classic.Level;
import com.beust.jcommander.JCommander;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;

/**
 * Entry point for the docker agent.
 *
 * @author tom.shapira
 */
public class Main {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final CommandLineArgs commandLineArgs = new CommandLineArgs();
    private static final String INFO = "info";

    private static JCommander jCommander;

    /* --- Main --- */

    public static void main(String[] args) {
        jCommander = new JCommander(commandLineArgs, args);
        // validate args // TODO use jCommander validators
        // TODO add usage command

        // read configuration properties
        Properties configProps = readAndValidateConfigFile(commandLineArgs.configFilePath);

        // read log level from configuration file
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        String logLevel = configProps.getProperty(LOG_LEVEL_KEY, INFO);
        root.setLevel(Level.toLevel(logLevel, Level.INFO));

        // run the agent
        DockerAgent dockerAgent = new DockerAgent(configProps, commandLineArgs);
        dockerAgent.sendRequest();
    }

    /* --- Private methods --- */

    private static Properties readAndValidateConfigFile(String configFilePath) {
        Properties configProps = new Properties();
        InputStream inputStream = null;
        boolean foundError = false;
        try {
            inputStream = new FileInputStream(configFilePath);
            configProps.load(inputStream);
            foundError = validateConfigProps(configProps, configFilePath);
        } catch (FileNotFoundException e) {
            logger.error("Failed to open " + configFilePath + " for reading", e);
            foundError = true;
        } catch (IOException e) {
            logger.error("Error occurred when reading from " + configFilePath , e);
            foundError = true;
        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    logger.warn("Failed to close " + configFilePath + "InputStream", e);
                }
            }
            if (foundError) {
                System.exit(-1); // TODO this may throw SecurityException. Return null instead
            }
        }
        return configProps;
    }

    private static boolean validateConfigProps(Properties configProps, String configFilePath) {
        boolean foundError = false;
        if (StringUtils.isBlank(configProps.getProperty(ORG_TOKEN_PROPERTY_KEY))) {
            foundError = true;
            logger.error("Could not retrieve {} property from {}", ORG_TOKEN_PROPERTY_KEY, configFilePath);
        }
        return foundError;
    }
}
