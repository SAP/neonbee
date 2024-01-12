package io.neonbee.endpoint.odatav4.internal.olingo.expression.operators;

import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.PRIMITIVE_BOOLEAN;

import java.util.Locale;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.endpoint.odatav4.internal.olingo.expression.operands.ExpressionVisitorOperand;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

public class UnaryOperator {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final RoutingContext routingContext;

    private final ExpressionVisitorOperand operand;

    /**
     * Creates a new UnaryOperator.
     *
     * @param routingContext the current routingContext
     * @param operand        the operand for the unary operation
     * @throws ODataApplicationException In case that the type of the operand can't be set @see
     *                                   {@link ExpressionVisitorOperand#setType()}.
     */
    public UnaryOperator(RoutingContext routingContext, ExpressionVisitorOperand operand)
            throws ODataApplicationException {
        this.routingContext = routingContext;
        this.operand = operand.setType();
    }

    @Override
    @SuppressWarnings({ "deprecation", "removal", "Finalize", "checkstyle:NoFinalizer" })
    protected final void finalize() { // NOPMD prevent a finalizer attack
        // note: this empty final finalize method is required, due to at least one of this classes constructors throw
        // (an) exception(s) and are thus vulnerable to finalizer attacks, see [1] and [2]. as the use of finalize
        // methods is discouraged anyways, we favour this solution, compared to making the whole class final
        // [1]https://spotbugs.readthedocs.io/en/stable/bugDescriptions.html#ct-be-wary-of-letting-constructors-throw-exceptions-ct-constructor-throw
        // [2]https://wiki.sei.cmu.edu/confluence/display/java/OBJ11-J.+Be+wary+of+letting+constructors+throw+exceptions
    }

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    public ExpressionVisitorOperand notOperation() throws ODataApplicationException {
        if (operand.isNull()) {
            return operand;
        } else if (operand.is(PRIMITIVE_BOOLEAN)) {
            return new ExpressionVisitorOperand(routingContext, !operand.getTypedValue(Boolean.class),
                    operand.getType());
        } else {
            LOGGER.correlateWith(routingContext).error("Unsupported type: {}", operand.getType());
            throw new ODataApplicationException("Unsupported type: " + operand.getType(),
                    HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }
}
