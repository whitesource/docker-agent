package org.whitesource.docker;

import ch.qos.logback.classic.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.whitesource.fs.StatusCode;
import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.LOG_LEVEL_KEY;

/**
 * @author eugen.horovitz
 */
public class Connector {

    /* --- Static members --- */

    private static final Logger logger = LoggerFactory.getLogger(Connector.class);
    private static final String INFO = "info";

    /* --- Public methods --- */

    public StatusCode getStatusCode(Properties configProps, CommandLineArgs commandLineArgs, String[] args) {
        try {
            // read log level from configuration file
            ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
            String logLevel = configProps.getProperty(LOG_LEVEL_KEY, INFO);
            root.setLevel(Level.toLevel(logLevel, Level.INFO));

            // run the agent
            DockerAgent dockerAgent = new DockerAgent(configProps, commandLineArgs, args);
            StatusCode statusCode = dockerAgent.sendRequest();
            if (statusCode!=StatusCode.SUCCESS)
                return statusCode;
        }
        catch (IllegalArgumentException e) {
            e.printStackTrace(System.out);
            return StatusCode.CLIENT_FAILURE;
        }
        catch (Exception e) {
            e.printStackTrace(System.out);
            return StatusCode.CLIENT_FAILURE;
        }
        return StatusCode.SUCCESS;
    }
}
