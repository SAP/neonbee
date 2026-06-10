package io.neonbee.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.EndpointConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.EndpointConfig} original class using Vert.x codegen.
 */
public class EndpointConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, EndpointConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "type":
                if (member.getValue() instanceof String) {
                    obj.setType((String) member.getValue());
                }
                break;
            case "basePath":
                if (member.getValue() instanceof String) {
                    obj.setBasePath((String) member.getValue());
                }
                break;
            case "enabled":
                if (member.getValue() instanceof Boolean) {
                    obj.setEnabled((Boolean) member.getValue());
                }
                break;
            case "authChainConfig":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<io.neonbee.config.AuthHandlerConfig> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof JsonObject)
                            list.add(new io.neonbee.config.AuthHandlerConfig((io.vertx.core.json.JsonObject) item));
                    });
                    obj.setAuthChainConfig(list);
                }
                break;
            }
        }
    }

    static void toJson(EndpointConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(EndpointConfig obj, java.util.Map<String, Object> json) {
        if (obj.getType() != null) {
            json.put("type", obj.getType());
        }
        if (obj.getBasePath() != null) {
            json.put("basePath", obj.getBasePath());
        }
        if (obj.isEnabled() != null) {
            json.put("enabled", obj.isEnabled());
        }
        if (obj.getAuthChainConfig() != null) {
            JsonArray array = new JsonArray();
            obj.getAuthChainConfig().forEach(item -> array.add(item.toJson()));
            json.put("authChainConfig", array);
        }
    }
}
