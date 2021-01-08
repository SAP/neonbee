package io.neonbee.internal.processor.odata.expression.operators;

import static io.neonbee.internal.processor.odata.edm.EdmConstants.GREATER_THAN;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.IS_EQUAL;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.LESS_THAN;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_BOOLEAN;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.internal.processor.odata.expression.operands.ExpressionVisitorOperand;
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
        }).filter(Objects::nonNull).collect(Collectors.toList());
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

    public ExpressionVisitorOperand andOperator() throws ODataApplicationException {
        Boolean result = null;
        if (leftOperand.isBooleanType() && rightOperand.isBooleanType()) {
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
            result = 0; // null is equal to null
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
        Boolean result = null;
        if (leftOperand.is(PRIMITIVE_BOOLEAN) && rightOperand.is(PRIMITIVE_BOOLEAN)) {
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
