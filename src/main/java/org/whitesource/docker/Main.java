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

import org.whitesource.fs.StatusCode;

/**
 * Entry point for the docker agent.
 *
 * @author tom.shapira
 */
public class Main {
    /* --- Static members --- */

    private static final CommandLineArgs commandLineArgs = new CommandLineArgs();

    /* --- Main --- */

    public static void main(String[] args) {
        ConfigManager configManager = new ConfigManager();
        PropertiesResult propsResult = configManager.getProperties(args, commandLineArgs);

        if(isValidResult(propsResult))
            System.exit(propsResult.getStatus().getValue());

        Connector dockerConnector = new Connector();
        StatusCode statusCode = dockerConnector.getStatusCode(propsResult.getConfigProps(), commandLineArgs);
        System.exit(statusCode.getValue());
    }

    private static boolean isValidResult(PropertiesResult propsResult) {
        return (propsResult.getStatus() == StatusCode.SUCCESS && propsResult == null)
                || propsResult.getStatus() != StatusCode.SUCCESS;
    }
}
