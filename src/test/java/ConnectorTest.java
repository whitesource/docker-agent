import org.junit.Assert;
import org.junit.Test;
import org.whitesource.docker.CommandLineArgs;
import org.whitesource.docker.ConfigManager;
import org.whitesource.docker.Connector;
import org.whitesource.docker.PropertiesResult;
import org.whitesource.fs.StatusCode;

import java.util.Properties;

import static org.whitesource.agent.ConfigPropertyKeys.*;


/**
 * @author eugen.horovitz
 */
public class ConnectorTest {

    public static final String DOCKER_WITH_DOCKER_TLS_VERIFY = "docker.withDockerTlsVerify";
    public static final String TRUE = "true";
    public static final String WINDOWS = "Windows";
    public static final String OS_NAME = "os.name";
    public static final String ALPINE = "alpine";
    public static final String COMMAND_I = "-i";
    public static final String FALSE = "false";

    @Test
    public void shouldReturnRightStatus() {
        CommandLineArgs commandLineArgs = new CommandLineArgs();
        Connector dockerConnector = new Connector();
        StatusCode statusCode;
        ConfigManager configManager = new ConfigManager();
        PropertiesResult propsResult = configManager.getProperties(new String[]{COMMAND_I, ALPINE}, commandLineArgs);
        Assert.assertEquals(propsResult.getStatus(), StatusCode.SUCCESS);
        Properties props = propsResult.getConfigProps();
        String os = System.getProperty(OS_NAME);

        //Check if the operating system is windows or linux for windows the port will be 2376 and for linux 2375
        if (os.startsWith(WINDOWS)) {
            props.setProperty(DOCKER_WITH_DOCKER_TLS_VERIFY, TRUE);
            statusCode = dockerConnector.getStatusCode(props, commandLineArgs);
            Assert.assertEquals(StatusCode.SUCCESS, statusCode);
        } else {
            props.setProperty(DOCKER_WITH_DOCKER_TLS_VERIFY, TRUE);
            statusCode = dockerConnector.getStatusCode(props, commandLineArgs);
            Assert.assertEquals(StatusCode.SUCCESS, statusCode);
        }

        //this should be check after setting up policies in the server
        props.setProperty(CHECK_POLICIES_PROPERTY_KEY, FALSE);
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
