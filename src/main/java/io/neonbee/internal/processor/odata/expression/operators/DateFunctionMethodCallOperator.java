package io.neonbee.internal.processor.odata.expression.operators;

import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DATE;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DATE_TIME_OFFSET;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_DECIMAL;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_INT32;
import static io.neonbee.internal.processor.odata.edm.EdmConstants.PRIMITIVE_TIME_OF_DAY;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.apache.olingo.commons.api.edm.EdmPrimitiveType;
import org.apache.olingo.commons.api.edm.EdmType;
import org.apache.olingo.commons.api.http.HttpStatusCode;
import org.apache.olingo.server.api.ODataApplicationException;

import io.neonbee.internal.processor.odata.edm.EdmPrimitiveNull;
import io.neonbee.internal.processor.odata.expression.EntityComparison;
import io.neonbee.internal.processor.odata.expression.operands.ExpressionVisitorOperand;
import io.neonbee.logging.LoggingFacade;
import io.vertx.ext.web.RoutingContext;

@SuppressWarnings("checkstyle:MissingJavadocMethod")
public class DateFunctionMethodCallOperator extends MethodCallOperator implements EntityComparison {
    private static final LoggingFacade LOGGER = LoggingFacade.create();

    private static final long ONE_SECOND_AS_NANOS = TimeUnit.SECONDS.toNanos(1);

    public DateFunctionMethodCallOperator(RoutingContext routingContext, List<ExpressionVisitorOperand> parameters) {
        super(routingContext, parameters);
    }

    @SuppressWarnings("JavaInstantGetSecondsGetNano")
    public ExpressionVisitorOperand fractionalseconds() throws ODataApplicationException {
        return dateFunction((instant, operand) -> {
            if (operand.getValue() instanceof Timestamp) {
                return new BigDecimal(operand.getTypedValue(Timestamp.class).getNanos())
                        .divide(BigDecimal.valueOf(ONE_SECOND_AS_NANOS));
            } else {
                return new BigDecimal(instant.getNano()).divide(BigDecimal.valueOf(ONE_SECOND_AS_NANOS));
            }
        }, PRIMITIVE_DECIMAL, PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_TIME_OF_DAY);
    }

    public ExpressionVisitorOperand second() throws ODataApplicationException {
        return dateFunction((instant, operand) -> instant.atZone(ZoneId.systemDefault()).getSecond(), PRIMITIVE_INT32,
                PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_TIME_OF_DAY);
    }

    public ExpressionVisitorOperand hour() throws ODataApplicationException {
        return dateFunction((instant, operand) -> instant.atZone(ZoneId.systemDefault()).getHour(), PRIMITIVE_INT32,
                PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_TIME_OF_DAY);
    }

    public ExpressionVisitorOperand minute() throws ODataApplicationException {
        return dateFunction((instant, operand) -> instant.atZone(ZoneId.systemDefault()).getMinute(), PRIMITIVE_INT32,
                PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_TIME_OF_DAY);
    }

    public ExpressionVisitorOperand day() throws ODataApplicationException {
        return dateFunction((instant, operand) -> instant.atZone(ZoneId.systemDefault()).getDayOfMonth(),
                PRIMITIVE_INT32, PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_DATE);
    }

    public ExpressionVisitorOperand month() throws ODataApplicationException {
        return dateFunction((instant, operand) -> instant.atZone(ZoneId.systemDefault()).getMonthValue(),
                PRIMITIVE_INT32, PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_DATE);
    }

    public ExpressionVisitorOperand year() throws ODataApplicationException {
        return dateFunction((instant, operand) -> instant.atZone(ZoneId.systemDefault()).getYear(), PRIMITIVE_INT32,
                PRIMITIVE_DATE_TIME_OFFSET, PRIMITIVE_DATE);
    }

    private ExpressionVisitorOperand dateFunction(DateFunction dateFunction, EdmType returnType,
            EdmPrimitiveType... expectedTypes) throws ODataApplicationException {
        ExpressionVisitorOperand operand = parameters.get(0).setType();
        if (operand.isNull()) {
            return new ExpressionVisitorOperand(routingContext, null, EdmPrimitiveNull.getInstance());
        } else {
            if (operand.is(expectedTypes) && (operand.is(PRIMITIVE_DATE) || operand.is(PRIMITIVE_DATE_TIME_OFFSET)
                    || operand.is(PRIMITIVE_TIME_OF_DAY))) {
                Object value = operand.getValue();
                Instant instant = dateTimeObjectToInstant(routingContext, value);
                return new ExpressionVisitorOperand(routingContext, dateFunction.perform(instant, operand), returnType);
            } else {
                LOGGER.correlateWith(routingContext).error("Invalid type");
                throw new ODataApplicationException("Invalid type", HttpStatusCode.BAD_REQUEST.getStatusCode(),
                        Locale.ENGLISH);
            }
        }
    }
}
