package io.neonbee.config;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.MicrometerRegistryConfig}. NOTE: This class has been automatically
 * generated from the {@link io.neonbee.config.MicrometerRegistryConfig} original class using Vert.x codegen.
 */
public class MicrometerRegistryConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, MicrometerRegistryConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "className":
                if (member.getValue() instanceof String) {
                    obj.setClassName((String) member.getValue());
                }
                break;
            case "config":
                if (member.getValue() instanceof JsonObject) {
                    obj.setConfig(((JsonObject) member.getValue()).copy());
                }
                break;
            }
        }
    }

    static void toJson(MicrometerRegistryConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(MicrometerRegistryConfig obj, java.util.Map<String, Object> json) {
        if (obj.getClassName() != null) {
            json.put("className", obj.getClassName());
        }
        if (obj.getConfig() != null) {
            json.put("config", obj.getConfig());
        }
    }
}
