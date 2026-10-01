package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.dimazigel.yfinance.YFinance;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Enforces the API boundary: Feign and Jackson are {@code implementation} dependencies and the
 * {@code internal} packages are not exported, so no type from any of them may appear in a public or
 * protected member of a public class in an exported package (return/parameter types, generic type
 * arguments, field types, constructor parameters, or a direct supertype/interface). A consumer on
 * the module path could not even name such a type. Also pins the coarser classpath-level check
 * from the Feign/Jackson 3 migration: Retrofit and Jackson 2 databind must be gone entirely.
 */
class DependencyHygieneTest {

    private static final String BASE = "io.github.dimazigel.yfinance";
    private static final String INTERNAL = BASE + ".internal";

    @Test
    void retrofitAndJackson2DatabindAreNotOnTheClasspath() {
        assertThat(present("retrofit2.Retrofit")).as("Retrofit").isFalse();
        assertThat(present("com.fasterxml.jackson.databind.ObjectMapper")).as("Jackson 2 databind").isFalse();
        assertThat(present("tools.jackson.databind.json.JsonMapper")).as("Jackson 3 databind").isTrue();
        assertThat(present("com.fasterxml.jackson.annotation.JsonIgnoreProperties")).as("Jackson annotations").isTrue();
    }

    private static boolean present(String className) {
        try {
            Class.forName(className, false, DependencyHygieneTest.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Everything under {@code internal} is unexported (see {@code module-info.java}), so it is not API. */
    private static boolean isInternal(Class<?> clazz) {
        return clazz.getPackageName().startsWith(INTERNAL);
    }

    @Test
    void exportedPackagesExposeNoFeignJacksonOrInternalTypes() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : mainClasses()) {
            if (!Modifier.isPublic(clazz.getModifiers()) || isInternal(clazz)) {
                continue;
            }
            checkSupertypes(clazz, violations);
            checkConstructors(clazz, violations);
            checkMethods(clazz, violations);
            checkFields(clazz, violations);
        }
        assertThat(violations).as("public members of exported classes exposing Feign, Jackson or internal types").isEmpty();
    }

    /**
     * {@code YahooDecoder}, {@code YahooErrorDecoder} and {@code YahooInvocationHandlerFactory} implement
     * Feign SPIs, so their {@code implements} clause is unavoidably a Feign type; they stay out of the
     * hygiene check above only because they are package-private. Pin that so a future change that makes
     * one of them public fails loudly here instead of silently widening the public API.
     */
    @Test
    void feignSpiImplementationsStayPackagePrivate() throws Exception {
        for (String name : List.of("YahooDecoder", "YahooErrorDecoder", "YahooInvocationHandlerFactory")) {
            Class<?> clazz = Class.forName(INTERNAL + ".http." + name, false, YFinance.class.getClassLoader());
            assertThat(Modifier.isPublic(clazz.getModifiers()))
                    .as("%s implements a Feign SPI; making it public would leak a Feign type", name)
                    .isFalse();
        }
    }

    private static void checkSupertypes(Class<?> clazz, List<String> violations) {
        Type superclass = clazz.getGenericSuperclass();
        if (superclass != null) {
            reportIfForeign(violations, clazz, "supertype", superclass.getTypeName());
        }
        for (Type iface : clazz.getGenericInterfaces()) {
            reportIfForeign(violations, clazz, "interface", iface.getTypeName());
        }
    }

    private static void checkConstructors(Class<?> clazz, List<String> violations) {
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            if (isPublicOrProtected(ctor.getModifiers()) && !ctor.isSynthetic()) {
                checkParameters(clazz, ctor, violations);
            }
        }
    }

    private static void checkMethods(Class<?> clazz, List<String> violations) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (isPublicOrProtected(method.getModifiers()) && !method.isSynthetic() && !method.isBridge()) {
                reportIfForeign(violations, clazz, "return type of " + method.getName() + "()",
                        method.getGenericReturnType().getTypeName());
                checkParameters(clazz, method, violations);
            }
        }
    }

    private static void checkParameters(Class<?> clazz, Executable member, List<String> violations) {
        Type[] paramTypes = member.getGenericParameterTypes();
        for (int i = 0; i < paramTypes.length; i++) {
            reportIfForeign(violations, clazz, "parameter " + i + " of " + member.getName() + "(...)",
                    paramTypes[i].getTypeName());
        }
    }

    private static void checkFields(Class<?> clazz, List<String> violations) {
        for (Field field : clazz.getDeclaredFields()) {
            if (isPublicOrProtected(field.getModifiers()) && !field.isSynthetic()) {
                reportIfForeign(violations, clazz, "field " + field.getName(), field.getGenericType().getTypeName());
            }
        }
    }

    private static boolean isPublicOrProtected(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
    }

    private static void reportIfForeign(List<String> violations, Class<?> clazz, String where, String typeName) {
        if (typeName.contains("tools.jackson.") || typeName.contains("feign.") || typeName.contains(INTERNAL + ".")) {
            violations.add(clazz.getName() + " " + where + ": " + typeName);
        }
    }

    /** Every class file under the library's package on the {@code main} classpath, loaded without initializing. */
    private static List<Class<?>> mainClasses() throws IOException {
        Path root = mainClassesRoot();
        ClassLoader loader = YFinance.class.getClassLoader();
        var classes = new ArrayList<Class<?>>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String relative = root.relativize(file).toString();
                String binaryName = relative
                        .substring(0, relative.length() - ".class".length())
                        .replace('/', '.')
                        .replace('\\', '.');
                if (!binaryName.startsWith(BASE)) {
                    continue;
                }
                try {
                    classes.add(Class.forName(binaryName, false, loader));
                } catch (ClassNotFoundException | NoClassDefFoundError e) {
                    throw new AssertionError("Could not load " + binaryName + " from " + root, e);
                }
            }
        }
        return classes;
    }

    private static Path mainClassesRoot() {
        URL location = YFinance.class.getProtectionDomain().getCodeSource().getLocation();
        try {
            Path path = Paths.get(location.toURI());
            if (!Files.isDirectory(path)) {
                throw new IllegalStateException(
                        "Expected " + path + " (YFinance's code source) to be the main classes directory, "
                                + "not a jar; adjust this test if the test task's classpath changed.");
            }
            return path;
        } catch (URISyntaxException e) {
            throw new UncheckedIOException(new IOException(e));
        }
    }
}
