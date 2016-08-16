/*
 * Copyright 2016 FabricMC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.fabricmc.base.launch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LaunchArguments {
    private Map<String, String> arguments = new HashMap<>();

    public LaunchArguments(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--")) {
                this.arguments.put(arg.substring(2, arg.length()), args[i + 1]);
            }
        }
    }

    public String getArgument(String key) {
        if (containsArgument(key)) {
            return this.arguments.get(key);
        } else {
            return "";
        }
    }

    public void removeArgument(String key) {
        this.arguments.remove(key);
    }

    public boolean containsArgument(String key) {
        return this.arguments.containsKey(key);
    }

    public void addArgument(String key, String value) {
        this.arguments.put(key, value);
    }

    public String[] getArguments() {
        String[] args = new String[this.arguments.size() * 2];
        List<Map.Entry<String, String>> entries = new ArrayList<>(this.arguments.entrySet());
        for (int i = 0; i < arguments.size(); i++) {
            Map.Entry<String, String> entry = entries.get(i);
            args[i * 2] = "--" + entry.getKey();
            args[i * 2 + 1] = entry.getValue();
        }
        return args;
    }
}
