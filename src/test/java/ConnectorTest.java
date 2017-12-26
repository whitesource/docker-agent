import org.junit.Assert;
import org.junit.Test;
import org.whitesource.docker.CommandLineArgs;
import org.whitesource.docker.ConfigManager;
import org.whitesource.docker.Connector;
import org.whitesource.docker.PropertiesResult;
import org.whitesource.fs.StatusCode;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;
import static org.whitesource.docker.DockerAgent.DOCKER_URL;

/**
 * @author eugen.horovitz
 */
public class ConnectorTest {

    @Test
    public void shouldReturnRightStatus() {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        Connector dockerConnector = new Connector();

        ConfigManager configManager = new ConfigManager();

        PropertiesResult propsResult = configManager.getProperties(new String[]{"-i", "alpine"}, commandLineArgs);
        Assert.assertEquals(propsResult.getStatus(), StatusCode.SUCCESS);

        Properties props = propsResult.getConfigProps();
        //props.setProperty(CHECK_POLICIES_PROPERTY_KEY, "true");
        props.setProperty(DOCKER_URL, "tcp://127.0.0.1:2375");
        StatusCode statusCode = dockerConnector.getStatusCode(props, commandLineArgs);
        Assert.assertEquals(StatusCode.POLICY_VIOLATION, statusCode);

        //this should be check after setting up policies in the server
        props.setProperty(CHECK_POLICIES_PROPERTY_KEY, "false");
        statusCode = dockerConnector.getStatusCode(props, commandLineArgs);
        Assert.assertEquals(StatusCode.SUCCESS, statusCode);

        props.setProperty(PROXY_HOST_PROPERTY_KEY, "localhost");
        props.setProperty(PROXY_PORT_PROPERTY_KEY, "8089");
        props.setProperty(PROXY_USER_PROPERTY_KEY, "no-name");
        props.setProperty(PROXY_PASS_PROPERTY_KEY, "wrong-pass");
        statusCode = dockerConnector.getStatusCode(props, commandLineArgs);
        Assert.assertEquals(StatusCode.CONNECTION_FAILURE, statusCode);
    }
}
