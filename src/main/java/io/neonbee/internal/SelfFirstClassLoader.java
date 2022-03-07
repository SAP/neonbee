package io.neonbee.internal;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;

public class SelfFirstClassLoader extends URLClassLoader {
    @VisibleForTesting
    final Predicate<String> parentPreferredPredicate;

    private final Map<String, Class<?>> loadedClasses = new ConcurrentHashMap<>();

    /**
     * Creates a SelfFirstClassLoader which tries to load classes from its own class path first. If a class can't be
     * found in its own class path, it tries to load the class from the parent ClassLoader.
     *
     * @param urls   The class path of the SelfFirstClassLoader
     * @param parent The parent ClassLoader
     */
    public SelfFirstClassLoader(URL[] urls, ClassLoader parent) {
        this(urls, parent, Collections.emptyList());
    }

    /**
     * Creates a SelfFirstClassLoader which tries to load classes from its own class path first. If a class can't be
     * found in its own class path, it tries to load the class from the parent ClassLoader. With this constructor it is
     * possible to pass a List of classes which should be still loaded from parent ClassLoader. If a passed String is
     * empty or null, it will be filtered out.
     *
     * <p>
     * <b>Attention:</b> It is possible to use wildcards for the List of classes passed in <i>parentPreferred</i>.
     * <p>
     * Example:
     *
     * <pre>
     * io.neonbee.*      -&gt; Would load all classes in this package from parent.
     * io.neonbee.Data*  -&gt; Would load all classes in package io.neonbee which starts with Data from parent.
     * *               -&gt; Would load all classes from parent. (If this is required, a normal ClassLoader might fit better)
     * </pre>
     *
     * @param urls            The class path of the SelfFirstClassLoader
     * @param parent          The parent ClassLoader
     * @param parentPreferred The classes which should be loaded from parent, if found.
     */
    public SelfFirstClassLoader(URL[] urls, ClassLoader parent, List<String> parentPreferred) {
        super(urls, parent);

        this.parentPreferredPredicate = getClassNamePredicate(parentPreferred);
    }

    @VisibleForTesting
    static Predicate<String> getClassNamePredicate(List<String> classNames) {
        String pattern = classNames
                .stream().filter(Predicate.not(Strings::isNullOrEmpty)).map(className -> Arrays
                        .stream(className.split("\\*", -1)).map(Pattern::quote).collect(Collectors.joining(".*")))
                .collect(Collectors.joining("|"));
        return !pattern.isEmpty() ? Pattern.compile(pattern).asMatchPredicate() : Predicates.alwaysFalse();
    }

    @SuppressWarnings("PMD.EmptyCatchBlock")
    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        Class<?> classToReturn = loadedClasses.get(name);

        // Check if this class already loaded by this class loader
        if (classToReturn != null) {
            return classToReturn;
        }

        // If the class version loaded by the parent is preferred, then use super.loadClass
        // of super because it has parent first approach.
        if (loadFromParent(name)) {
            try {
                classToReturn = getParent().loadClass(name);
                loadedClasses.put(name, classToReturn);
                return classToReturn;
            } catch (ClassNotFoundException e) {
                // Fall through
            }
        }

        // Try and load with this class loader
        try {
            classToReturn = findClass(name);
        } catch (ClassNotFoundException e) {
            // Class loader does not find class, try with parent again
            classToReturn = getParent().loadClass(name);
        }

        if (resolve) {
            resolveClass(classToReturn);
        }

        loadedClasses.put(name, classToReturn);
        return classToReturn;
    }

    @Override
    public URL getResource(String name) {
        // First check this classloader
        URL url = findResource(name);

        // Then try the parent if not found
        if (url == null) {
            url = getParent().getResource(name);
        }

        return url;
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        // First get resources from this classloader
        List<URL> resources = Collections.list(findResources(name));

        // Then add resources from the parent
        if (getParent() != null) {
            Enumeration<URL> parentResources = getParent().getResources(name);
            if (parentResources.hasMoreElements()) {
                resources.addAll(Collections.list(parentResources));
            }
        }

        return Collections.enumeration(resources);
    }

    @VisibleForTesting
    boolean loadFromParent(String className) {
        return parentPreferredPredicate.test(className);
    }
}
