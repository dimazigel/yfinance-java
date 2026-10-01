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
 * Enforces the boundary AGENTS.md documents for {@code api/}: Feign and Jackson are {@code
 * implementation} dependencies, so no type from either may appear in a public or protected member
 * of a public class in an API package (return/parameter types, generic type arguments, field
 * types, constructor parameters, or a direct supertype/interface). Annotations are not checked —
 * the {@code api/} interfaces carry {@code @RequestLine}/{@code @Param} by design, and {@code api/}
 * is itself excluded below. Also pins the coarser classpath-level check from the Feign/Jackson 3
 * migration: Retrofit and Jackson 2 databind must be gone entirely.
 */
class DependencyHygieneTest {

    private static final String BASE = "io.github.dimazigel.yfinance";

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

    /**
     * Mirrors {@code tasks.javadoc}'s {@code exclude(...)} list in {@code build.gradle.kts}: those
     * packages/classes are internal, so a Feign or Jackson type in one of them is not a hygiene
     * violation. Keep the two lists in sync.
     */
    private static boolean isExcluded(Class<?> topLevel) {
        String pkg = topLevel.getPackageName();
        String name = topLevel.getSimpleName();
        if (isOrIsSubPackage(pkg, "assembly") || isOrIsSubPackage(pkg, "dto")
                || isOrIsSubPackage(pkg, "mapper") || isOrIsSubPackage(pkg, "api")
                || isOrIsSubPackage(pkg, "auth") || isOrIsSubPackage(pkg, "service")) {
            return true;
        }
        if (pkg.equals(BASE + ".http")) {
            return name.endsWith("Interceptor")
                    || name.equals("RawQuoteClient")
                    || name.startsWith("YahooFeign")
                    || name.equals("YahooJsonMapper")
                    || name.equals("RawAwareNumberModule")
                    || name.equals("YahooClientFactory")
                    || name.equals("CallBudget")
                    || name.equals("RateLimitBudgetExceeded");
        }
        if (pkg.equals(BASE + ".batch")) {
            return name.equals("FanOut");
        }
        return false;
    }

    private static boolean isOrIsSubPackage(String pkg, String simpleBase) {
        String base = BASE + "." + simpleBase;
        return pkg.equals(base) || pkg.startsWith(base + ".");
    }

    @Test
    void apiPackagesExposeNoFeignOrJacksonTypes() throws Exception {
        List<String> violations = new ArrayList<>();
        for (Class<?> clazz : mainClasses()) {
            if (!Modifier.isPublic(clazz.getModifiers()) || isExcluded(topLevelOf(clazz))) {
                continue;
            }
            checkSupertypes(clazz, violations);
            checkConstructors(clazz, violations);
            checkMethods(clazz, violations);
            checkFields(clazz, violations);
        }
        assertThat(violations).as("public members of API-package classes exposing Feign or Jackson types").isEmpty();
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
            Class<?> clazz = Class.forName(BASE + ".http." + name, false, YFinance.class.getClassLoader());
            assertThat(Modifier.isPublic(clazz.getModifiers()))
                    .as("%s implements a Feign SPI; making it public would leak a Feign type", name)
                    .isFalse();
        }
    }

    private static Class<?> topLevelOf(Class<?> clazz) {
        Class<?> top = clazz;
        while (top.getEnclosingClass() != null) {
            top = top.getEnclosingClass();
        }
        return top;
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
        if (typeName.contains("tools.jackson.") || typeName.contains("feign.")) {
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
