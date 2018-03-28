package org.whitesource.docker;

import org.whitesource.contracts.PluginInfo;

public class DockerAgentInfo implements PluginInfo {

    private static final String AGENT_TYPE = "docker-agent";
    private static final String AGENT_VERSION = "2.6.4";
    private static final String PLUGIN_VERSION = "18.2.2";

    @Override
    public String getAgentType() {
        return AGENT_TYPE;
    }

    @Override
    public String getAgentVersion() {
        return AGENT_VERSION;
    }

    @Override
    public String getPluginVersion() {
        return PLUGIN_VERSION;
    }
}
