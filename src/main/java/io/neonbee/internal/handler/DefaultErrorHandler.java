package io.neonbee.internal.handler;

import static io.neonbee.internal.handler.CorrelationIdHandler.getCorrelationId;
import static io.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import io.neonbee.NeonBee;
import io.neonbee.data.DataException;
import io.neonbee.handler.ErrorHandler;
import io.neonbee.internal.helper.FileSystemHelper;
import io.neonbee.logging.LoggingFacade;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.MIMEHeader;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BadRequestException;

/**
 * Similar to the io.vertx.ext.web.handler.impl.ErrorHandlerImpl, w/ minor adoptions for error text and template.
 */
public class DefaultErrorHandler implements ErrorHandler {
    /**
     * The default template to use for rendering.
     */
    public static final Path DEFAULT_ERROR_HANDLER_TEMPLATE = Path.of("META-INF/neonbee/error-template.html");

    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final int HTTP_ERROR_CODE_LOWER_LIMIT = 100;

    private static final int HTTP_ERROR_CODE_UPPER_LIMIT = 1000;

    /**
     * Cached template for rendering the HTML errors.
     */
    private String errorTemplate;

    /**
     * Initializes the ErrorHandler with the passed template.
     *
     * @param neonBee the related NeonBee instance
     *
     * @return A Future with the initialized ErrorHandler
     */
    @Override
    public Future<ErrorHandler> initialize(NeonBee neonBee) {
        return readErrorTemplate(neonBee).map(errorTemplate -> {
            this.errorTemplate = errorTemplate;
            return this;
        });
    }

    private Future<String> readErrorTemplate(NeonBee neonBee) {
        String errorTemplatePath = neonBee.getServerConfig().getErrorHandlerTemplate();
        Path p = errorTemplatePath == null ? DEFAULT_ERROR_HANDLER_TEMPLATE : Path.of(errorTemplatePath);
        return FileSystemHelper.readFile(neonBee.getVertx(), p).map(Buffer::toString);
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
            errorCode = INTERNAL_SERVER_ERROR.code();
            errorMessage = INTERNAL_SERVER_ERROR.reasonPhrase();
        }
        // HttpServerResponse will look-up a appropriate error code if statusMessage has not been explicitly set
        if (errorCode != -1 && errorMessage == null) {
            routingContext.response().setStatusCode(errorCode);
            errorMessage = routingContext.response().getStatusMessage();
        }
        // Use meaningful error messages in case that error comes from Vert.x Web Validation
        if (failure instanceof BadRequestException) {
            errorMessage = failure.getMessage();
        }
        answerWithError(routingContext, errorCode, errorMessage);
    }

    /**
     * Creates a String with the text/plain error response.
     *
     * @param routingContext the routing context
     * @param errorCode      the error code of the response
     * @param errorMessage   the error message of the response
     * @return A String with the plain text error response.
     */
    protected String createPlainResponse(RoutingContext routingContext, int errorCode, String errorMessage) {
        StringBuilder builder =
                new StringBuilder().append("Error ").append(errorCode).append(": ").append(errorMessage);
        String correlationId = getCorrelationId(routingContext);
        if (correlationId != null) {
            builder.append(" (Correlation ID: ").append(correlationId).append(')');
        }
        return builder.toString();
    }

    /**
     * Creates a String with the application/json error response.
     *
     * @param routingContext the routing context
     * @param errorCode      the error code of the response
     * @param errorMessage   the error message of the response
     * @return A JsonObject with the error response.
     */
    protected JsonObject createJsonResponse(RoutingContext routingContext, int errorCode, String errorMessage) {
        JsonObject error = new JsonObject().put("code", errorCode).put("message", errorMessage);
        Optional.ofNullable(getCorrelationId(routingContext)).ifPresent(id -> error.put("correlationId", id));
        return new JsonObject().put("error", error);
    }

    /**
     * Creates a String with the text/html error response.
     *
     * @param routingContext the routing context
     * @param errorCode      the error code of the response
     * @param errorMessage   the error message of the response
     * @return A String with the html encoded error response.
     */
    protected String createHtmlResponse(RoutingContext routingContext, int errorCode, String errorMessage) {
        return errorTemplate.replace("{errorCode}", Integer.toString(errorCode)).replace("{errorMessage}", errorMessage)
                .replace("{correlationId}", getCorrelationId(routingContext));
    }

    private boolean sendError(RoutingContext routingContext, String mime, int errorCode, String errorMessage) {
        HttpServerResponse response = routingContext.response();
        if (mime.startsWith("text/html")) {
            response.putHeader(CONTENT_TYPE, "text/html");
            response.end(createHtmlResponse(routingContext, errorCode, errorMessage));
        } else if (mime.startsWith("application/json")) {
            response.putHeader(CONTENT_TYPE, "application/json");
            response.end(createJsonResponse(routingContext, errorCode, errorMessage).toBuffer());
        } else if (mime.startsWith("text/plain")) {
            response.putHeader(CONTENT_TYPE, "text/plain");
            response.end(createPlainResponse(routingContext, errorCode, errorMessage));
        } else {
            return false;
        }
        return true;
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
}
