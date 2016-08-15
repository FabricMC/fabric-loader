package chorusmc.base.launch;

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
