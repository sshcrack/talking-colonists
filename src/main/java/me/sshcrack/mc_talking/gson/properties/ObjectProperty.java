package me.sshcrack.mc_talking.gson.properties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ObjectProperty extends Property {
    private HashMap<String, Property> properties = new HashMap<>();

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<String> required = new ArrayList<>();

    public ObjectProperty() {
        super("object");
    }

    public ObjectProperty(HashMap<String, Property> properties) {
        super("object");
        setProperties(properties);
    }

    public HashMap<String, Property> getProperties() {
        return properties;
    }

    public void setProperties(HashMap<String, Property> properties) {
        this.properties = properties;
        properties.forEach((key, property) -> {
            required.clear();
            if (property.isRequired()) {
                required.add(key);
            }
        });
    }

    public void addProperty(String key, Property property) {
        properties.put(key, property);

        if (property.isRequired()) {
            required.add(key);
        } else {
            required.remove(key);
        }
    }
}
