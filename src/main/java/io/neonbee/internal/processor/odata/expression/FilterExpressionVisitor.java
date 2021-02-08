package io.neonbee.internal.processor.odata.expression;

import static io.neonbee.internal.processor.odata.edm.EdmHelper.throwNotImplementedODataException;

import java.util.List;
import java.util.Optional;

import org.apache.olingo.commons.api.data.Entity;
import org.apache.olingo.commons.api.data.Property;
import org.apache.olingo.commons.api.edm.EdmEnumType;
import org.apache.olingo.commons.api.edm.EdmProperty;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.server.api.ODataApplicationException;
import org.apache.olingo.server.api.uri.UriResource;
import org.apache.olingo.server.api.uri.UriResourceProperty;
import org.apache.olingo.server.api.uri.queryoption.expression.BinaryOperatorKind;
import org.apache.olingo.server.api.uri.queryoption.expression.Expression;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitException;
import org.apache.olingo.server.api.uri.queryoption.expression.ExpressionVisitor;
import org.apache.olingo.server.api.uri.queryoption.expression.Literal;
import org.apache.olingo.server.api.uri.queryoption.expression.Member;
import org.apache.olingo.server.api.uri.queryoption.expression.MethodKind;
import org.apache.olingo.server.api.uri.queryoption.expression.UnaryOperatorKind;

import io.neonbee.internal.processor.odata.edm.EdmHelper;
import io.neonbee.internal.processor.odata.expression.operands.ExpressionVisitorOperand;
import io.neonbee.internal.processor.odata.expression.operators.BinaryOperator;
import io.neonbee.internal.processor.odata.expression.operators.DateFunctionMethodCallOperator;
import io.neonbee.internal.processor.odata.expression.operators.StringFunctionMethodCallOperator;
import io.neonbee.internal.processor.odata.expression.operators.UnaryOperator;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

public class FilterExpressionVisitor implements ExpressionVisitor<ExpressionVisitorOperand> {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private final RoutingContext routingContext;

    private final Entity entity;

    /**
     * Creates a new FilterExpressionVisitor.
     *
     * @param routingContext the current routingContext
     * @param entity         the entity to filter for
     */
    public FilterExpressionVisitor(RoutingContext routingContext, Entity entity) {
        this.routingContext = routingContext;
        this.entity = entity;
    }

    @Override
    public ExpressionVisitorOperand visitBinaryOperator(BinaryOperatorKind operator, ExpressionVisitorOperand left,
            List<ExpressionVisitorOperand> right) throws ExpressionVisitException, ODataApplicationException {
        if (BinaryOperatorKind.IN.equals(operator)) {
            return new BinaryOperator(routingContext, left, right).inOperator();
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitBinaryOperator(BinaryOperatorKind operator, ExpressionVisitorOperand left,
            ExpressionVisitorOperand right) throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("Operator: {}", operator);
        }

        BinaryOperator binaryOperator = new BinaryOperator(routingContext, left, right);
        switch (operator) {
        case AND:
            return binaryOperator.andOperator();
        case OR:
            return binaryOperator.orOperator();
        case EQ:
            return binaryOperator.equalsOperator();
        case NE:
            return binaryOperator.notEqualsOperator();
        case GE:
            return binaryOperator.greaterEqualsOperator();
        case GT:
            return binaryOperator.greaterThanOperator();
        case LE:
            return binaryOperator.lessEqualsOperator();
        case LT:
            return binaryOperator.lessThanOperator();
        case IN:
            return binaryOperator.inOperator();
        // TODO: Add HAS, ADD, SUB, MUL, DIV, MOD here
        default:
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(routingContext).debug("Operator '{}' is not yet implemented.", operator);
            }
            return throwNotImplementedODataException();
        }
    }

    @Override
    public ExpressionVisitorOperand visitLiteral(Literal literal)
            throws ExpressionVisitException, ODataApplicationException {
        String literalText = EdmHelper.extractValueFromLiteral(literal.getText());
        EdmType literalType = literal.getType();
        if (LOGGER.correlateWith(routingContext).isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("literal type: {}, literal text: {}", literalType, literalText);
        }
        return new ExpressionVisitorOperand(routingContext, literalText, literalType);
    }

    @Override
    public ExpressionVisitorOperand visitUnaryOperator(UnaryOperatorKind operator, ExpressionVisitorOperand operand)
            throws ExpressionVisitException, ODataApplicationException {
        UnaryOperator unaryOperator = new UnaryOperator(routingContext, operand);
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("Unary Operator: {}", unaryOperator);
        }
        if (UnaryOperatorKind.NOT.equals(operator)) {
            return unaryOperator.notOperation();
        }
        // TODO: Add MINUS here
        if (LOGGER.isDebugEnabled()) {
            LOGGER.correlateWith(routingContext).debug("Unary Operator '{}' is not yet implemented.", unaryOperator);
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitMember(Member member)
            throws ExpressionVisitException, ODataApplicationException {
        /*
         * See https://docs.oasis-open.org/odata/odata/v4.01/odata-v4.01-part2-url-conventions.html#sec_URLComponents
         * for details about how the OData URL broken down into its component parts.
         */
        List<UriResource> uriResourceParts = member.getResourcePath().getUriResourceParts();
        // initialPart contains the part with the entity resource (+ key if exists) in the example link from above
        // it would be the "Categories(1)" part of the resource path
        UriResource initialPart = uriResourceParts.get(0);
        if (initialPart instanceof UriResourceProperty) {
            UriResourceProperty uriResourceProperty =
                    Optional.ofNullable((UriResourceProperty) initialPart).orElseThrow();
            EdmProperty edmProperty = Optional.ofNullable(uriResourceProperty.getProperty()).orElseThrow();
            Property property = Optional.ofNullable(entity.getProperty(edmProperty.getName())).orElseThrow();
            if (property.isPrimitive()) {
                return new ExpressionVisitorOperand(routingContext, property.getValue(), edmProperty.getType(),
                        edmProperty);
            }
            return throwNotImplementedODataException();
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitMethodCall(MethodKind methodCall, List<ExpressionVisitorOperand> parameters)
            throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("Method Call: {}", methodCall);
        }
        switch (methodCall) {
        case ENDSWITH:
            return new StringFunctionMethodCallOperator(routingContext, parameters).endsWith();
        case INDEXOF:
            return new StringFunctionMethodCallOperator(routingContext, parameters).indexOf();
        case STARTSWITH:
            return new StringFunctionMethodCallOperator(routingContext, parameters).startsWith();
        case TOLOWER:
            return new StringFunctionMethodCallOperator(routingContext, parameters).toLower();
        case TOUPPER:
            return new StringFunctionMethodCallOperator(routingContext, parameters).toUpper();
        case TRIM:
            return new StringFunctionMethodCallOperator(routingContext, parameters).trim();
        case SUBSTRING:
            return new StringFunctionMethodCallOperator(routingContext, parameters).substring();
        case CONTAINS:
            return new StringFunctionMethodCallOperator(routingContext, parameters).contains();
        case CONCAT:
            return new StringFunctionMethodCallOperator(routingContext, parameters).concat();
        case LENGTH:
            return new StringFunctionMethodCallOperator(routingContext, parameters).length();
        case YEAR:
            return new DateFunctionMethodCallOperator(routingContext, parameters).year();
        case MONTH:
            return new DateFunctionMethodCallOperator(routingContext, parameters).month();
        case DAY:
            return new DateFunctionMethodCallOperator(routingContext, parameters).day();
        case HOUR:
            return new DateFunctionMethodCallOperator(routingContext, parameters).hour();
        case MINUTE:
            return new DateFunctionMethodCallOperator(routingContext, parameters).minute();
        case SECOND:
            return new DateFunctionMethodCallOperator(routingContext, parameters).second();
        case FRACTIONALSECONDS:
            return new DateFunctionMethodCallOperator(routingContext, parameters).fractionalseconds();
        // TODO: Add ROUND, FLOOR, CEILING here
        default:
            if (LOGGER.isDebugEnabled()) {
                LOGGER.correlateWith(routingContext).debug("Method Call '{}' is not yet implemented.", methodCall);
            }
            return throwNotImplementedODataException();
        }
    }

    @Override
    public ExpressionVisitorOperand visitTypeLiteral(EdmType type)
            throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("Visiting Type Literal '{}' is not yet implemented.", type);
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitAlias(String aliasName)
            throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("Visiting Alias '{}' is not yet implemented.", aliasName);
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitEnum(EdmEnumType type, List<String> enumValues)
            throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext)
                    .trace("Visiting Enum of type '{}' with values '{}' is not yet implemented.", type, enumValues);
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitLambdaExpression(String lambdaFunction, String lambdaVariable,
            Expression expression) throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace(
                    "Visiting Lambda Expression with function '{}' and variable '{}' is not yet implemented.",
                    lambdaFunction, lambdaVariable);
        }
        return throwNotImplementedODataException();
    }

    @Override
    public ExpressionVisitorOperand visitLambdaReference(String variableName)
            throws ExpressionVisitException, ODataApplicationException {
        if (LOGGER.isTraceEnabled()) {
            LOGGER.correlateWith(routingContext).trace("Visiting Lambda Reference '{}' is not yet implemented.",
                    variableName);
        }
        return throwNotImplementedODataException();
    }
}
