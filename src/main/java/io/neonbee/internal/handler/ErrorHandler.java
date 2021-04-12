package io.neonbee.internal.handler;

import static io.neonbee.internal.handler.CorrelationIdHandler.getCorrelationId;
import static io.neonbee.internal.helper.BufferHelper.readResourceToBuffer;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.net.HttpURLConnection;
import java.util.List;
import java.util.Objects;

import io.neonbee.data.DataException;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;

/**
 * Similar to the io.vertx.ext.web.handler.impl.ErrorHandlerImpl, w/ minor adoptions for error text and template.
 */
public final class ErrorHandler implements Handler<RoutingContext> {
    /**
     * The default template to use for rendering.
     */
    public static final String DEFAULT_ERROR_HANDLER_TEMPLATE = "META-INF/neonbee/error-template.html";

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final int HTTP_ERROR_CODE_LOWER_LIMIT = 100;

    private static final int HTTP_ERROR_CODE_UPPER_LIMIT = 1000;

    /**
     * Cached template for rendering the HTML errors.
     */
    private final String errorTemplate;

    /**
     * Convenience method as similar other Vertx handler implementations (e.g. ErrorHandler)
     *
     * @return The ErrorHandler
     */
    public static ErrorHandler create() {
        return new ErrorHandler(DEFAULT_ERROR_HANDLER_TEMPLATE);
    }

    private ErrorHandler(String errorTemplateName) {
        Objects.requireNonNull(errorTemplateName);
        this.errorTemplate = readResourceToBuffer(errorTemplateName).toString();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        Throwable failure = routingContext.failure();
        if (failure != null) {
            LOGGER.correlateWith(routingContext).error(failure.getMessage(), failure);
        }
        String errorMessage = null;
        int errorCode = routingContext.statusCode();
        if (errorCode == -1 && failure instanceof DataException) {
            errorCode = ((DataException) failure).failureCode();
        }
        // treat errorCodes >= 100 and errorCodes <= 1000 as HTTP errors
        if (errorCode < HTTP_ERROR_CODE_LOWER_LIMIT || errorCode >= HTTP_ERROR_CODE_UPPER_LIMIT) {
            errorCode = HttpURLConnection.HTTP_INTERNAL_ERROR;
            errorMessage = INTERNAL_SERVER_ERROR.reasonPhrase();
        }
        // HttpServerResponse will look-up a appropriate error code if statusMessage has not been explicitly set
        if (errorCode != -1 && errorMessage == null) {
            routingContext.response().setStatusCode(errorCode);
            errorMessage = routingContext.response().getStatusMessage();
        }
        answerWithError(routingContext, errorCode, errorMessage);
    }

    private void answerWithError(RoutingContext routingContext, int errorCode, String errorMessage) {
        routingContext.response().setStatusCode(errorCode);
        if (!sendErrorResponseMIME(routingContext, errorCode, errorMessage)
                && !sendErrorAcceptMIME(routingContext, errorCode, errorMessage)) {
            sendError(routingContext, "text/plain", errorCode, errorMessage); // fallback plain/text
        }
    }

    private boolean sendErrorResponseMIME(RoutingContext routingContext, int errorCode, String errorMessage) {
        // does the response already set the mime type?
        String mime = routingContext.response().headers().get(CONTENT_TYPE);
        return mime != null && sendError(routingContext, mime, errorCode, errorMessage);
    }

    private boolean sendErrorAcceptMIME(RoutingContext routingContext, int errorCode, String errorMessage) {
        // respect the client accept order
        List<MIMEHeader> acceptableMimes = routingContext.parsedHeaders().accept();
        for (MIMEHeader accept : acceptableMimes) {
            if (sendError(routingContext, accept.value(), errorCode, errorMessage)) {
                return true;
            }
        }
        return false;
    }

    private boolean sendError(RoutingContext routingContext, String mime, int errorCode, String errorMessage) {
        String correlationId = getCorrelationId(routingContext);
        HttpServerResponse response = routingContext.response();
        if (mime.startsWith("text/html")) {
            response.putHeader(CONTENT_TYPE, "text/html");
            response.end(errorTemplate.replace("{errorCode}", Integer.toString(errorCode))
                    .replace("{errorMessage}", errorMessage).replace("{correlationId}", correlationId));
            return true;
        } else if (mime.startsWith("application/json")) {
            JsonObject jsonError = new JsonObject();
            jsonError.put("code", errorCode).put("message", errorMessage);
            if (correlationId != null) {
                jsonError.put("correlationId", correlationId);
            }
            response.putHeader(CONTENT_TYPE, "application/json");
            response.end(new JsonObject().put("error", jsonError).encode());
            return true;
        } else if (mime.startsWith("text/plain")) {
            response.putHeader(CONTENT_TYPE, "text/plain");
            StringBuilder builder = new StringBuilder();
            builder.append("Error ").append(errorCode).append(": ").append(errorMessage);
            if (correlationId != null) {
                builder.append(" (Correlation ID: ").append(correlationId).append(')');
            }
            response.end(builder.toString());
            return true;
        }
        return false;
    }
}
