package io.github.dimazigel.yfinance.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Retrofit and Jackson 2 databind must be gone from the runtime classpath (annotations 2.x are expected). */
class DependencyHygieneTest {

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
}
