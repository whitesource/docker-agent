package org.whitesource.docker;

import com.beust.jcommander.JCommander;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.fs.StatusCode;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.ORG_TOKEN_PROPERTY_KEY;

/**
 * @author eugen.horovitz
 */
public class ConfigManager {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(ConfigManager.class);

    private static JCommander jCommander;

    /* --- Public methods --- */

    public PropertiesResult getProperties(String[] args, CommandLineArgs commandLineArgs) {
        Properties configProps = new Properties();
        jCommander = new JCommander(commandLineArgs, args);
        // validate args // TODO use jCommander validators
        if (commandLineArgs.help) {
            jCommander.usage();
            return new PropertiesResult(null,StatusCode.SUCCESS);
        }
        // read configuration properties
        StatusCode parserStatus = readAndValidateConfigFile(commandLineArgs.configFilePath, configProps);
        return new PropertiesResult(configProps,parserStatus);
    }

    /* --- Private methods --- */

    private static StatusCode readAndValidateConfigFile(String configFilePath, Properties configProps) {
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
            logger.error("Error occurred when reading from " + configFilePath, e);
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
                return StatusCode.CLIENT_FAILURE; // TODO this may throw SecurityException. Return null instead
            }
        }
        return StatusCode.SUCCESS;
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