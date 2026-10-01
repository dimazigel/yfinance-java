package io.github.dimazigel.yfinance.internal.auth;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * The two hidden inputs of Yahoo's cookie-consent page that have to be posted back to accept it.
 *
 * @param csrfToken value of the {@code csrfToken} input
 * @param sessionId value of the {@code sessionId} input
 */
record ConsentForm(String csrfToken, String sessionId) {

    private static final Pattern INPUT = Pattern.compile("<input\\b[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern ATTRIBUTE = Pattern.compile("([\\w-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)')");

    /**
     * Reads the form out of {@code html}, or empty when the page is not a consent form: either
     * input is missing or has no value. Attribute order and quoting do not matter.
     */
    static Optional<ConsentForm> parse(String html) {
        String csrfToken = null;
        String sessionId = null;
        Matcher input = INPUT.matcher(html);
        while (input.find()) {
            String name = null;
            String value = null;
            Matcher attribute = ATTRIBUTE.matcher(input.group());
            while (attribute.find()) {
                String text = attribute.group(2) != null ? attribute.group(2) : attribute.group(3);
                String key = attribute.group(1).toLowerCase(Locale.ROOT);
                if (key.equals("name")) {
                    name = text;
                } else if (key.equals("value")) {
                    value = text;
                }
            }
            if (value == null || value.isEmpty()) {
                continue;
            }
            if ("csrfToken".equals(name)) {
                csrfToken = unescape(value);
            } else if ("sessionId".equals(name)) {
                sessionId = unescape(value);
            }
        }
        return of(csrfToken, sessionId);
    }

    private static Optional<ConsentForm> of(@Nullable String csrfToken, @Nullable String sessionId) {
        return csrfToken == null || sessionId == null ? Optional.empty() : Optional.of(new ConsentForm(csrfToken, sessionId));
    }

    // The character references an attribute value can carry; the ampersand last, so nothing is unescaped twice.
    private static String unescape(String value) {
        return value.replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }
}
