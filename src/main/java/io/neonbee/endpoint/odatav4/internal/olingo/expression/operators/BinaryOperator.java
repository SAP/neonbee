package io.neonbee.endpoint.odatav4.internal.olingo.expression.operators;

import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.GREATER_THAN;
import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.IS_EQUAL;
import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.LESS_THAN;
import static io.neonbee.endpoint.odatav4.internal.olingo.edm.EdmConstants.PRIMITIVE_BOOLEAN;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.endpoint.odatav4.internal.olingo.expression.operands.ExpressionVisitorOperand;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

@SuppressWarnings("checkstyle:MissingJavadocMethod")
public class BinaryOperator {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final RoutingContext routingContext;

    private final ExpressionVisitorOperand leftOperand;

    private ExpressionVisitorOperand rightOperand;

    private List<ExpressionVisitorOperand> rightOperands;

    public BinaryOperator(RoutingContext routingContext, ExpressionVisitorOperand leftOperand,
            List<ExpressionVisitorOperand> rightOperands) throws ODataApplicationException {
        this.routingContext = routingContext;
        this.rightOperands = rightOperands.stream().map(operand -> {
            try {
                return operand.setType();
            } catch (ODataApplicationException e) {
                LOGGER.correlateWith(routingContext).error("Can't set type of operand", e);
            }
            return null;
        }).filter(Objects::nonNull).toList();
        this.leftOperand = leftOperand.setType();
    }

    public BinaryOperator(RoutingContext routingContext, ExpressionVisitorOperand leftOperand,
            ExpressionVisitorOperand rightOperand) throws ODataApplicationException {
        this.routingContext = routingContext;
        this.leftOperand = leftOperand.setType().normalizeTypes(rightOperand.setType());
        this.rightOperand = rightOperand.setType().normalizeTypes(leftOperand);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("leftOperand: {}", leftOperand);
            LOGGER.correlateWith(routingContext).trace("rightOperand: {}", rightOperand);
        }
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

    public ExpressionVisitorOperand andOperator() throws ODataApplicationException {
        if (leftOperand.isBooleanType() && rightOperand.isBooleanType()) {
            Boolean result = null;
            if (Boolean.TRUE.equals(leftOperand.getValue()) && Boolean.TRUE.equals(rightOperand.getValue())) {
                result = true;
            } else if (Boolean.FALSE.equals(leftOperand.getValue()) || Boolean.FALSE.equals(rightOperand.getValue())) {
                result = false;
            }
            return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
        } else {
            String message = "And operator needs two binary operands";
            LOGGER.correlateWith(routingContext).error(message);
            throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    @SuppressWarnings("unchecked")
    private boolean binaryComparison(int... expected) {
        int result;
        if (leftOperand.isNull() && rightOperand.isNull()) {
            result = 0;
        } else {
            if (leftOperand.isIntegerType()) {
                result = leftOperand.getTypedValue(BigInteger.class)
                        .compareTo(rightOperand.getTypedValue(BigInteger.class));
            } else if (leftOperand.isDecimalType()) {
                result = leftOperand.getTypedValue(BigDecimal.class)
                        .compareTo(rightOperand.getTypedValue(BigDecimal.class));
            } else if ((leftOperand.getValue().getClass() == rightOperand.getValue().getClass())
                    && (leftOperand.getValue() instanceof Comparable<?>)) {
                result = ((Comparable<Object>) leftOperand.getValue()).compareTo(rightOperand.getValue());
            } else {
                result = leftOperand.getValue().equals(rightOperand.getValue()) ? 0 : 1;
            }
        }
        return Arrays.stream(expected).anyMatch(i -> i == result);
    }

    public ExpressionVisitorOperand equalsOperator() {
        boolean result = isBinaryComparisonNecessary() && binaryComparison(IS_EQUAL);
        return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand greaterEqualsOperator() {
        boolean result = isBinaryComparisonNecessary() && binaryComparison(GREATER_THAN, IS_EQUAL);
        return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand greaterThanOperator() {
        boolean result = isBinaryComparisonNecessary() && binaryComparison(GREATER_THAN);
        return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand inOperator() {
        if ((rightOperands != null) && !rightOperands.isEmpty()
                && rightOperands.stream().anyMatch(rightOperandTyped -> rightOperandTyped.getTypedValue(String.class)
                        .equals(leftOperand.getTypedValue(String.class)))) {
            return new ExpressionVisitorOperand(routingContext, true, PRIMITIVE_BOOLEAN);
        }
        return new ExpressionVisitorOperand(routingContext, false, PRIMITIVE_BOOLEAN);
    }

    private boolean isBinaryComparisonNecessary() {
        return !(leftOperand.isNull() ^ rightOperand.isNull());
    }

    public ExpressionVisitorOperand lessEqualsOperator() {
        boolean result = isBinaryComparisonNecessary() && binaryComparison(LESS_THAN, IS_EQUAL);
        return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand lessThanOperator() {
        boolean result = isBinaryComparisonNecessary() && binaryComparison(LESS_THAN);
        return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand notEqualsOperator() {
        ExpressionVisitorOperand equalsOperator = equalsOperator();
        return new ExpressionVisitorOperand(routingContext, !(Boolean) equalsOperator.getValue(), PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand orOperator() throws ODataApplicationException {
        if (leftOperand.is(PRIMITIVE_BOOLEAN) && rightOperand.is(PRIMITIVE_BOOLEAN)) {
            Boolean result = null;
            if (Boolean.TRUE.equals(leftOperand.getValue()) || Boolean.TRUE.equals(rightOperand.getValue())) {
                result = true;
            } else if (Boolean.FALSE.equals(leftOperand.getValue()) && Boolean.FALSE.equals(rightOperand.getValue())) {
                result = false;
            }
            return new ExpressionVisitorOperand(routingContext, result, PRIMITIVE_BOOLEAN);
        } else {
            String message = "Or operator needs two binary operands";
            LOGGER.correlateWith(routingContext).error(message);
            throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }
}
