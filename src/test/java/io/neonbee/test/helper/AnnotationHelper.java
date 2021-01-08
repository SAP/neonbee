package io.neonbee.test.helper;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.olingo.commons.api.edm.provider.CsdlAnnotation;
import org.apache.olingo.commons.api.edm.provider.CsdlAnnotations;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlCollection;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlConstantExpression;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlConstantExpression.ConstantExpressionType;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlExpression;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlPath;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlPropertyValue;
import org.apache.olingo.commons.api.edm.provider.annotation.CsdlRecord;

/**
 * This is utility class for dealing with {@link CsdlAnnotations}
 */
public class AnnotationHelper {

    /**
     * Utility method to create a {@link CsdlAnnotations} for string constant
     *
     * @param target   target of annotation
     * @param term     term of an annotation
     * @param property property name of an annotation
     * @param value    property value of an annotation
     * @return a {@link CsdlAnnotations}
     */
    public static CsdlAnnotations createConstantAnnotations(String target, String term, String property, String value) {
        return createConstantAnnotations(target, term, property, CsdlConstantExpression.ConstantExpressionType.String,
                value);
    }

    /**
     * Utility method to create a {@link CsdlAnnotations} for constant
     *
     * @param target   target of annotation
     * @param term     term of an annotation
     * @param property property name of an annotation
     * @param type     type of the value
     * @param value    property value of an annotation
     * @return a {@link CsdlAnnotations}
     */
    public static CsdlAnnotations createConstantAnnotations(String target, String term, String property,
            ConstantExpressionType type, String value) {
        CsdlConstantExpression expression = new CsdlConstantExpression(type);
        expression.setValue(value);
        return createAnnotations(target, term, property, expression);
    }

    /**
     * Utility method to create a {@link CsdlAnnotations} for path
     *
     * @param target   target of annotation
     * @param term     term of an annotation
     * @param property property name of an annotation
     * @param value    property value
     * @return a {@link CsdlAnnotations}
     */
    public static CsdlAnnotations createPathAnnotations(String target, String term, String property, String value) {
        CsdlPath expression = new CsdlPath();
        expression.setValue(value);
        return createAnnotations(target, term, property, expression);
    }

    /**
     * Utility method to create a {@link CsdlAnnotations} for collection of constants
     *
     * @param target   target of annotation
     * @param term     term of an annotation
     * @param property property name of an annotation
     * @param values   collection item values of an annotation
     * @return a {@link CsdlAnnotations}
     */
    public static CsdlAnnotations createConstantCollectionAnnotations(String target, String term, String property,
            String... values) {
        CsdlCollection expression = new CsdlCollection();
        expression.setItems(Arrays.stream(values).map(value -> {
            CsdlConstantExpression item =
                    new CsdlConstantExpression(CsdlConstantExpression.ConstantExpressionType.String);
            item.setValue(value);
            return item;
        }).collect(Collectors.toList()));
        return createAnnotations(target, term, property, expression);
    }

    /**
     * Utility method to create a {@link CsdlAnnotations} for collection of constants
     *
     * @param target   target of annotation
     * @param term     term of an annotation
     * @param property property name of an annotation
     * @param values   collection item values for paths of an annotation
     * @return a {@link CsdlAnnotations}
     */
    public static CsdlAnnotations createPathCollectionAnnotations(String target, String term, String property,
            String... values) {
        CsdlCollection expression = new CsdlCollection();
        expression.setItems(Arrays.stream(values).map(value -> {
            CsdlPath path = new CsdlPath();
            path.setValue(value);
            return path;
        }).collect(Collectors.toList()));
        return createAnnotations(target, term, property, expression);
    }

    /**
     * Utility method to create a {@link CsdlAnnotations}
     *
     * @param target     target of annotation
     * @param term       term of an annotation
     * @param property   property name of an annotation
     * @param expression expression of an annotation
     * @return a {@link CsdlAnnotations}
     */
    public static CsdlAnnotations createAnnotations(String target, String term, String property,
            CsdlExpression expression) {
        CsdlAnnotations annotations = new CsdlAnnotations();
        annotations.setTarget(target);

        CsdlAnnotation annotation = new CsdlAnnotation();
        annotation.setTerm(term);

        CsdlRecord record = new CsdlRecord();
        CsdlPropertyValue propertyValue = new CsdlPropertyValue();
        propertyValue.setProperty(property);

        propertyValue.setValue(expression);
        record.setPropertyValues(List.of(propertyValue));
        annotation.setExpression(record);
        annotations.setAnnotations(List.of(annotation));
        return annotations;
    }
}
