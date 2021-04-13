package io.neonbee.config;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

/**
 * Converter and mapper for {@link io.neonbee.config.ServerConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.ServerConfig} original class using Vert.x codegen.
 */
public class ServerConfigConverter {

    static void fromJson(Iterable<java.util.Map.Entry<String, Object>> json, ServerConfig obj) {
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
            case "correlationStrategy":
                if (member.getValue() instanceof String) {
                    obj.setCorrelationStrategy(
                            io.neonbee.config.ServerConfig.CorrelationStrategy.valueOf((String) member.getValue()));
                }
                break;
            case "endpointConfigs":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<io.neonbee.config.EndpointConfig> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof JsonObject)
                            list.add(new io.neonbee.config.EndpointConfig((io.vertx.core.json.JsonObject) item));
                    });
                    obj.setEndpointConfigs(list);
                }
                break;
            case "sessionCookieName":
                if (member.getValue() instanceof String) {
                    obj.setSessionCookieName((String) member.getValue());
                }
                break;
            case "sessionHandling":
                if (member.getValue() instanceof String) {
                    obj.setSessionHandling(
                            io.neonbee.config.ServerConfig.SessionHandling.valueOf((String) member.getValue()));
                }
                break;
            case "timeout":
                if (member.getValue() instanceof Number) {
                    obj.setTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "timeoutErrorCode":
                if (member.getValue() instanceof Number) {
                    obj.setTimeoutErrorCode(((Number) member.getValue()).intValue());
                }
                break;
            }
        }
    }

    static void toJson(ServerConfig obj, JsonObject json) {
        toJson(obj, json.getMap());
    }

    static void toJson(ServerConfig obj, java.util.Map<String, Object> json) {
        if (obj.getAuthChainConfig() != null) {
            JsonArray array = new JsonArray();
            obj.getAuthChainConfig().forEach(item -> array.add(item.toJson()));
            json.put("authChainConfig", array);
        }
        if (obj.getCorrelationStrategy() != null) {
            json.put("correlationStrategy", obj.getCorrelationStrategy().name());
        }
        if (obj.getEndpointConfigs() != null) {
            JsonArray array = new JsonArray();
            obj.getEndpointConfigs().forEach(item -> array.add(item.toJson()));
            json.put("endpointConfigs", array);
        }
        if (obj.getSessionCookieName() != null) {
            json.put("sessionCookieName", obj.getSessionCookieName());
        }
        if (obj.getSessionHandling() != null) {
            json.put("sessionHandling", obj.getSessionHandling().name());
        }
        json.put("timeout", obj.getTimeout());
        json.put("timeoutErrorCode", obj.getTimeoutErrorCode());
    }
}
