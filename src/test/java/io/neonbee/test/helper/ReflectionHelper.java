package io.neonbee.test.helper;

import static java.lang.invoke.MethodHandles.lookup;
import static java.lang.invoke.MethodHandles.privateLookupIn;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
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

    /**
     *
     * @param <T>         The expected type of the value
     * @param fieldHolder The object which contains the field
     * @param fieldName   The field which is holding the value
     * @return The value of the field
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    public static <T> T getValueOfPrivateField(Object fieldHolder, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
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
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    @SuppressWarnings("unchecked")
    private static <T> T getValueOfPrivateField(Class<?> clazz, Object fieldHolder, String fieldName,
            boolean removeFinal) throws NoSuchFieldException, IllegalAccessException {
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
     * @param <T>       The expected type of the value
     * @param clazz     The class which contains the field
     * @param fieldName The field which is holding the value
     * @return The value of the field
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    public static <T> T getValueOfPrivateStaticField(Class<?> clazz, String fieldName)
            throws NoSuchFieldException, IllegalAccessException {
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
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    public static ExecutionBlock setValueOfPrivateField(Object objectToModify, String fieldName, Object valueToSet)
            throws NoSuchFieldException, IllegalAccessException {
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
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    private static void setValueOfPrivateField(Class<?> clazz, Object objectToModify, String fieldName,
            Object valueToSet) throws NoSuchFieldException, IllegalAccessException {
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
     * @throws NoSuchFieldException   If no such field exists in the fieldHolder
     * @throws IllegalAccessException If JVM doesn't grant access to the field
     */
    public static ExecutionBlock setValueOfPrivateStaticField(Class<?> clazz, String fieldName, Object valueToSet)
            throws NoSuchFieldException, IllegalAccessException {
        Object oldValue = getValueOfPrivateField(clazz, null, fieldName, true);

        setValueOfPrivateField(clazz, null, fieldName, valueToSet);
        return () -> setValueOfPrivateField(clazz, null, fieldName, oldValue);
    }

    private static Class<?> resolveClass(Object object) {
        Class<?> c = Class.class.isInstance(object) ? (Class<?>) object : object.getClass();
        return c.isAnonymousClass() ? resolveClass(c.getSuperclass()) : c;
    }

    private static void removeFinalModifier(Field field) throws NoSuchFieldException, IllegalAccessException {
        Lookup lookup = privateLookupIn(Field.class, lookup());
        VarHandle modifiers = lookup.findVarHandle(Field.class, "modifiers", int.class);
        modifiers.set(field, field.getModifiers() & ~Modifier.FINAL);
    }

    private ReflectionHelper() {
        // Utils class no need to instantiate
    }
}
