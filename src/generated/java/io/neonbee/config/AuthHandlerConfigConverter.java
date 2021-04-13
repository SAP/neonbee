package io.neonbee.config;

import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.AuthHandlerConfig}. NOTE: This class has been automatically
 * generated from the {@link io.neonbee.config.AuthHandlerConfig} original class using Vert.x codegen.
 */
public class AuthHandlerConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, AuthHandlerConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
            case "additionalConfig":
                if (member.getValue() instanceof JsonObject) {
                    obj.setAdditionalConfig(((JsonObject) member.getValue()).copy());
                }
                break;
            case "authProviderConfig":
                if (member.getValue() instanceof JsonObject) {
                    obj.setAuthProviderConfig(new io.neonbee.config.AuthProviderConfig(
                            (io.vertx.core.json.JsonObject) member.getValue()));
                }
                break;
            case "type":
                if (member.getValue() instanceof String) {
                    obj.setType(
                            io.neonbee.config.AuthHandlerConfig.AuthHandlerType.valueOf((String) member.getValue()));
                }
                break;
            }
        }
    }

    static void toJson(AuthHandlerConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(AuthHandlerConfig obj, java.util.Map<String, Object> json) {
        if (obj.getAdditionalConfig() != null) {
            json.put("additionalConfig", obj.getAdditionalConfig());
        }
        if (obj.getAuthProviderConfig() != null) {
            json.put("authProviderConfig", obj.getAuthProviderConfig().toJson());
        }
        if (obj.getType() != null) {
            json.put("type", obj.getType().name());
        }
    }
}
