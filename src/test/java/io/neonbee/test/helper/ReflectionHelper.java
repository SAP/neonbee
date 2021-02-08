package io.neonbee.test.helper;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import io.vertx.junit5.VertxTestContext.ExecutionBlock;

@SuppressWarnings("TypeParameterUnusedInFormals")
public final class ReflectionHelper {

    /**
     * Returns the value of a private field.
     *
     * @param fieldHolder The object which contains the field
     * @param fieldName   The field which is holding the value
     * @return The value of the field
     */
    public static <T> T getValueOfPrivateField(Object fieldHolder, String fieldName)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        return getValueOfPrivateField(resolveClass(fieldHolder), fieldHolder, fieldName, false);
    }

    /**
     * Returns the value of a private field. This method should be used, when the object which holds the field is not
     * directly the class which declares the field. E.g. in case of extensions.
     *
     * @param clazz       The class to resolve the field
     * @param fieldHolder The Object which contains the field
     * @param fieldName   The field which is holding the value
     * @param removeFinal Pass true to remove the final modifier of the field, before it is read the first time
     *                    otherwise it is cached with the final value in the FieldAccessor.
     * @return The value of the field
     */
    @SuppressWarnings("unchecked")
    private static <T> T getValueOfPrivateField(Class<?> clazz, Object fieldHolder, String fieldName,
            boolean removeFinal)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field fieldToModify = clazz.getDeclaredField(fieldName);
        fieldToModify.setAccessible(true);
        if (removeFinal) {
            removeFinalModifier(fieldToModify);
        }

        return (T) fieldToModify.get(fieldHolder);
    }

    /**
     * Returns the value of a private field. This method should be used, when the object which holds the field is not
     * directly the class which declares the field. E.g. in case of extensions.
     *
     * @param clazz     The class which contains the field
     * @param fieldName The field which is holding the value
     * @return The value of the field
     */
    public static <T> T getValueOfPrivateStaticField(Class<?> clazz, String fieldName)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        return getValueOfPrivateField(clazz, null, fieldName, false);
    }

    /**
     * Set the value of a field which is private and final.
     *
     * @param objectToModify The object which contains the field
     * @param fieldName      The target field
     * @param valueToSet     The value to set
     *
     * @return An ExecutionBlock that resets the field to its value before the modification happened.
     */
    public static ExecutionBlock setValueOfPrivateField(Object objectToModify, String fieldName, Object valueToSet)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Object oldValue = getValueOfPrivateField(resolveClass(objectToModify), objectToModify, fieldName, true);
        setValueOfPrivateField(resolveClass(objectToModify), objectToModify, fieldName, valueToSet);

        return () -> setValueOfPrivateField(resolveClass(objectToModify), objectToModify, fieldName, oldValue);

    }

    /**
     * Set the value of a field which is private and final. This method should be used, when the object to modify is not
     * directly the class which declares the field. E.g. in case of extensions.
     *
     * @param clazz          The class to resolve the field
     * @param objectToModify The object which contains the field
     * @param fieldName      The target field
     * @param valueToSet     The value to set
     */
    private static void setValueOfPrivateField(Class<?> clazz, Object objectToModify, String fieldName,
            Object valueToSet)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field fieldToModify = clazz.getDeclaredField(fieldName);
        fieldToModify.setAccessible(true);
        removeFinalModifier(fieldToModify);
        fieldToModify.set(objectToModify, valueToSet);
    }

    /**
     * Set the value of a field which is private and final.
     *
     * @param clazz      The class which contains the field
     * @param fieldName  The target field
     * @param valueToSet The value to set
     *
     * @return An ExecutionBlock that resets the field to its value before the modification happened.
     */
    public static ExecutionBlock setValueOfPrivateStaticField(Class<?> clazz, String fieldName, Object valueToSet)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Object oldValue = getValueOfPrivateField(clazz, null, fieldName, true);

        setValueOfPrivateField(clazz, null, fieldName, valueToSet);
        return () -> setValueOfPrivateField(clazz, null, fieldName, oldValue);
    }

    private static Class<?> resolveClass(Object object) {
        Class<?> c = Class.class.isInstance(object) ? (Class<?>) object : object.getClass();
        return c.isAnonymousClass() ? resolveClass(c.getSuperclass()) : c;
    }

    private static void removeFinalModifier(Field field)
            throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException {
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private ReflectionHelper() {
        // Utils class no need to instantiate
    }
}
