package io.neonbee.config;

import java.util.Base64;

import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

/**
 * Converter and mapper for {@link io.neonbee.config.AuthHandlerConfig}. NOTE: This class has been automatically
 * generated from the {@link io.neonbee.config.AuthHandlerConfig} original class using Vert.x codegen.
 */
public class AuthHandlerConfigConverter {

    private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;

    private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, AuthHandlerConfig obj) {
        for (java.util.Map.Entry<String, Object> member : json) {
            switch (member.getKey()) {
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
        if (obj.getAuthProviderConfig() != null) {
            json.put("authProviderConfig", obj.getAuthProviderConfig().toJson());
        }
        if (obj.getType() != null) {
            json.put("type", obj.getType().name());
        }
    }
}
