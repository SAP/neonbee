package io.neonbee.config;

import java.util.Base64;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

/**
 * Converter and mapper for {@link io.neonbee.config.EndpointConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.EndpointConfig} original class using Vert.x codegen.
 */
public class EndpointConfigConverter {

    private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;

    private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, EndpointConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
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
            case "type":
                if (member.getValue() instanceof String) {
                    obj.setType((String) member.getValue());
                }
                break;
            }
        }
    }

    static void toJson(EndpointConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(EndpointConfig obj, java.util.Map<String, Object> json) {
        if (obj.getAuthChainConfig() != null) {
            JsonArray array = new JsonArray();
            obj.getAuthChainConfig().forEach(item -> array.add(item.toJson()));
            json.put("authChainConfig", array);
        }
        if (obj.getBasePath() != null) {
            json.put("basePath", obj.getBasePath());
        }
        if (obj.isEnabled() != null) {
            json.put("enabled", obj.isEnabled());
        }
        if (obj.getType() != null) {
            json.put("type", obj.getType());
        }
    }
}
