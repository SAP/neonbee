package io.neonbee.internal.processor.odata.expression.operators;

import java.time.Instant;
import java.util.List;

import io.neonbee.internal.processor.odata.expression.operands.ExpressionVisitorOperand;
import io.vertx.ext.web.RoutingContext;

/**
 * Currently 2 different types of functions are supported for method calls: DateFunctions implemented in @see
 * io.neonbee.internal.processor.odata.expression.operators.DateFunctionMethodCallOperator and StringFunction in.
 *
 * @see io.neonbee.internal.processor.odata.expression.operators.StringFunctionMethodCallOperator
 */
abstract class MethodCallOperator { // NOPMD abstract class w/o abstract methods as it should not be directly used
    protected final RoutingContext routingContext;

    protected final List<ExpressionVisitorOperand> parameters;

    MethodCallOperator(RoutingContext routingContext, List<ExpressionVisitorOperand> parameters) {
        this.routingContext = routingContext;
        this.parameters = parameters;
    }

    protected interface DateFunction {
        Object perform(Instant instant, ExpressionVisitorOperand operand);
    }

    protected interface StringFunction {
        Object perform(List<String> params);
    }
}
