package org.whitesource.docker;

import org.whitesource.fs.StatusCode;

import java.util.Properties;

/**
 * @author eugen.horovitz
 */
public class PropertiesResult {

    /* --- Members --- */

    private final Properties configProps;
    private final StatusCode status;

    /* --- Constructors --- */

    public PropertiesResult(Properties configProps, StatusCode parserStatus) {
        this.configProps = configProps;
        this.status = parserStatus;
    }

    /* --- Getters --- */

    public Properties getConfigProps() {
        return configProps;
    }

    public StatusCode getStatus() {
        return status;
    }
}
