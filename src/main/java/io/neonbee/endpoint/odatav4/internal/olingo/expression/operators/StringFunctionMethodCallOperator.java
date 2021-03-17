package io.neonbee.internal.processor.odata.expression.operators;

import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_BOOLEAN;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_INT32;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_STRING;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.internal.processor.odata.edm.EdmPrimitiveNull;
import io.neonbee.internal.processor.odata.expression.operands.ExpressionVisitorOperand;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

@SuppressWarnings("checkstyle:MissingJavadocMethod")
public class StringFunctionMethodCallOperator extends MethodCallOperator {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    public StringFunctionMethodCallOperator(RoutingContext routingContext, List<ExpressionVisitorOperand> parameters) {
        super(routingContext, parameters);
    }

    public ExpressionVisitorOperand startsWith() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).startsWith(params.get(1)), PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand endsWith() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).endsWith(params.get(1)), PRIMITIVE_BOOLEAN);
    }

    public ExpressionVisitorOperand indexOf() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).indexOf(params.get(1)), PRIMITIVE_INT32);
    }

    public ExpressionVisitorOperand length() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).length(), PRIMITIVE_INT32);
    }

    // See https://issues.oasis-open.org/browse/ODATA-781
    @SuppressWarnings("checkstyle:magicnumber")
    public ExpressionVisitorOperand substring() throws ODataApplicationException {
        ExpressionVisitorOperand valueOperand = parameters.get(0).setType();
        ExpressionVisitorOperand startOperand = parameters.get(1).setType();

        if (!startOperand.isIntegerType()) {
            startOperand = startOperand.setType(PRIMITIVE_INT32);
        }

        if (valueOperand.isNull() || startOperand.isNull()) {
            return new ExpressionVisitorOperand(routingContext, null, PRIMITIVE_STRING);
        } else if (valueOperand.is(PRIMITIVE_STRING)) {
            String value = valueOperand.getTypedValue(String.class);
            int start = Math.max(0, Math.min(startOperand.getTypedValue(BigInteger.class).intValue(), value.length()));
            int end = value.length();

            if (parameters.size() == 3) {
                ExpressionVisitorOperand lengthOperand = parameters.get(2).setType(PRIMITIVE_INT32);
                if (lengthOperand.isNull()) {
                    return new ExpressionVisitorOperand(routingContext, null, PRIMITIVE_STRING);
                } else if (lengthOperand.isIntegerType()) {
                    end = Math.max(0,
                            Math.min(start + lengthOperand.getTypedValue(BigInteger.class).intValue(), value.length()));
                } else {
                    String message = "Third substring parameter should be Edm.Int32";
                    LOGGER.correlateWith(routingContext).error(message);
                    throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(),
                            Locale.ENGLISH);
                }
            }
            return new ExpressionVisitorOperand(routingContext, value.substring(start, end), PRIMITIVE_STRING);
        } else {
            String message = "Substring has invalid parameters. First parameter should be Edm.String,"
                    + " second parameter should be Edm.Int32";
            LOGGER.correlateWith(routingContext).error(message);
            throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(), Locale.ENGLISH);
        }
    }

    public ExpressionVisitorOperand toLower() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).toLowerCase(Locale.ENGLISH), PRIMITIVE_STRING);
    }

    public ExpressionVisitorOperand toUpper() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).toUpperCase(Locale.ENGLISH), PRIMITIVE_STRING);
    }

    public ExpressionVisitorOperand trim() throws ODataApplicationException {
        return stringFunction(params -> params.get(0).trim(), PRIMITIVE_STRING);
    }

    public ExpressionVisitorOperand concat() throws ODataApplicationException {
        return stringFunction(params -> params.get(0) + params.get(1), PRIMITIVE_STRING);
    }

    public ExpressionVisitorOperand contains() throws ODataApplicationException {
        return stringFunction(parameters -> parameters.get(0).contains(parameters.get(1)), PRIMITIVE_BOOLEAN);
    }

    private List<String> getParametersAsString() throws ODataApplicationException {
        List<String> result = new ArrayList<>();
        for (ExpressionVisitorOperand parameter : parameters) {
            ExpressionVisitorOperand operand = parameter.setType();
            if (operand.isNull()) {
                result.add(null);
            } else if (operand.is(PRIMITIVE_STRING)) {
                result.add(operand.getTypedValue(String.class));
            } else {
                String message = "Invalid parameter. Expected parameter of type Edm.String.";
                LOGGER.correlateWith(routingContext).error(message);
                throw new ODataApplicationException(message, HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
            }
        }
        return result;
    }

    private ExpressionVisitorOperand stringFunction(StringFunction stringFunction, EdmType returnValue)
            throws ODataApplicationException {
        List<String> stringParameters = getParametersAsString();
        if (stringParameters.contains(null)) {
            return new ExpressionVisitorOperand(routingContext, null, EdmPrimitiveNull.getInstance());
        } else {
            return new ExpressionVisitorOperand(routingContext, stringFunction.perform(stringParameters), returnValue);
        }
    }
}
