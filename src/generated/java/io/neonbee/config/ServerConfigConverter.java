package io.neonbee.config;

import java.util.Base64;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.impl.JsonUtil;

/**
 * Converter and mapper for {@link io.neonbee.config.ServerConfig}. NOTE: This class has been automatically generated
 * from the {@link io.neonbee.config.ServerConfig} original class using Vert.x codegen.
 */
public class ServerConfigConverter {

    private static final Base64.Decoder BASE64_DECODER = JsonUtil.BASE64_DECODER;

    private static final Base64.Encoder BASE64_ENCODER = JsonUtil.BASE64_ENCODER;

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
            case "corsConfig":
                if (member.getValue() instanceof JsonObject) {
                    obj.setCorsConfig(
                            new io.neonbee.config.CorsConfig((io.vertx.core.json.JsonObject) member.getValue()));
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
            case "errorHandlerClassName":
                if (member.getValue() instanceof String) {
                    obj.setErrorHandlerClassName((String) member.getValue());
                }
                break;
            case "errorHandlerTemplate":
                if (member.getValue() instanceof String) {
                    obj.setErrorHandlerTemplate((String) member.getValue());
                }
                break;
            case "handlerFactoriesClassNames":
                if (member.getValue() instanceof JsonArray) {
                    java.util.ArrayList<java.lang.String> list = new java.util.ArrayList<>();
                    ((Iterable<Object>) member.getValue()).forEach(item -> {
                        if (item instanceof String)
                            list.add((String) item);
                    });
                    obj.setHandlerFactoriesClassNames(list);
                }
                break;
            case "httpOnlySessionCookie":
                if (member.getValue() instanceof Boolean) {
                    obj.setHttpOnlySessionCookie((Boolean) member.getValue());
                }
                break;
            case "minSessionIdLength":
                if (member.getValue() instanceof Number) {
                    obj.setMinSessionIdLength(((Number) member.getValue()).intValue());
                }
                break;
            case "secureSessionCookie":
                if (member.getValue() instanceof Boolean) {
                    obj.setSecureSessionCookie((Boolean) member.getValue());
                }
                break;
            case "sessionCookieName":
                if (member.getValue() instanceof String) {
                    obj.setSessionCookieName((String) member.getValue());
                }
                break;
            case "sessionCookiePath":
                if (member.getValue() instanceof String) {
                    obj.setSessionCookiePath((String) member.getValue());
                }
                break;
            case "sessionCookieSameSitePolicy":
                if (member.getValue() instanceof String) {
                    obj.setSessionCookieSameSitePolicy(
                            io.vertx.core.http.CookieSameSite.valueOf((String) member.getValue()));
                }
                break;
            case "sessionHandling":
                if (member.getValue() instanceof String) {
                    obj.setSessionHandling(
                            io.neonbee.config.ServerConfig.SessionHandling.valueOf((String) member.getValue()));
                }
                break;
            case "sessionTimeout":
                if (member.getValue() instanceof Number) {
                    obj.setSessionTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "timeout":
                if (member.getValue() instanceof Number) {
                    obj.setTimeout(((Number) member.getValue()).intValue());
                }
                break;
            case "timeoutStatusCode":
                if (member.getValue() instanceof Number) {
                    obj.setTimeoutStatusCode(((Number) member.getValue()).intValue());
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
        if (obj.getCorsConfig() != null) {
            json.put("corsConfig", obj.getCorsConfig().toJson());
        }
        if (obj.getEndpointConfigs() != null) {
            JsonArray array = new JsonArray();
            obj.getEndpointConfigs().forEach(item -> array.add(item.toJson()));
            json.put("endpointConfigs", array);
        }
        if (obj.getErrorHandlerClassName() != null) {
            json.put("errorHandlerClassName", obj.getErrorHandlerClassName());
        }
        if (obj.getErrorHandlerTemplate() != null) {
            json.put("errorHandlerTemplate", obj.getErrorHandlerTemplate());
        }
        if (obj.getHandlerFactoriesClassNames() != null) {
            JsonArray array = new JsonArray();
            obj.getHandlerFactoriesClassNames().forEach(item -> array.add(item));
            json.put("handlerFactoriesClassNames", array);
        }
        json.put("httpOnlySessionCookie", obj.isHttpOnlySessionCookie());
        json.put("minSessionIdLength", obj.getMinSessionIdLength());
        json.put("secureSessionCookie", obj.isSecureSessionCookie());
        if (obj.getSessionCookieName() != null) {
            json.put("sessionCookieName", obj.getSessionCookieName());
        }
        if (obj.getSessionCookiePath() != null) {
            json.put("sessionCookiePath", obj.getSessionCookiePath());
        }
        if (obj.getSessionCookieSameSitePolicy() != null) {
            json.put("sessionCookieSameSitePolicy", obj.getSessionCookieSameSitePolicy().name());
        }
        if (obj.getSessionHandling() != null) {
            json.put("sessionHandling", obj.getSessionHandling().name());
        }
        json.put("sessionTimeout", obj.getSessionTimeout());
        json.put("timeout", obj.getTimeout());
        json.put("timeoutStatusCode", obj.getTimeoutStatusCode());
    }
}
