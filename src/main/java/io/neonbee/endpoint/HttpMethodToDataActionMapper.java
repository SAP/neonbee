package io.neonbee.endpoint;

import static io.neonbee.data.DataAction.CREATE;
import static io.neonbee.data.DataAction.DELETE;
import static io.neonbee.data.DataAction.READ;
import static io.neonbee.data.DataAction.UPDATE;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.HEAD;
import static io.vertx.core.http.HttpMethod.PATCH;
import static io.vertx.core.http.HttpMethod.POST;
import static io.vertx.core.http.HttpMethod.PUT;

import io.neonbee.data.DataAction;
import io.vertx.core.http.HttpMethod;

/**
 * Utility class to map HTTP methods to DataActions.
 */
public class HttpMethodToDataActionMapper {

    /**
     * Maps an HTTP method to a DataAction.
     *
     * @param method The HTTP method
     * @return The corresponding DataAction, or null if no mapping exists
     */
    public static DataAction mapMethodToAction(HttpMethod method) {
        if (POST.equals(method)) {
            return CREATE;
        } else if (HEAD.equals(method) || GET.equals(method)) {
            return READ;
        } else if (PUT.equals(method) || PATCH.equals(method)) {
            return UPDATE;
        } else if (HttpMethod.DELETE.equals(method)) {
            return DELETE;
        } else {
            return null;
        }
    }
}
