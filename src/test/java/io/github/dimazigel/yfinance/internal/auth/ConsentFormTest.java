package io.github.dimazigel.yfinance.internal.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ConsentFormTest {

    @Test
    void readsTheTwoHiddenInputsWhateverTheAttributeOrderOrQuoting() {
        String html = "<form><input type=\"hidden\" name=\"csrfToken\" value=\"tok&amp;1\">\n"
                + "<INPUT value='sess-2' type='hidden' name='sessionId' />"
                + "<input type=\"hidden\" name=\"other\" value=\"x\"></form>";

        assertThat(ConsentForm.parse(html)).contains(new ConsentForm("tok&1", "sess-2"));
    }

    @Test
    void aPageWithoutBothInputsIsNotAConsentForm() {
        assertThat(ConsentForm.parse("<html><body>Yahoo home</body></html>")).isEmpty();
        assertThat(ConsentForm.parse("<input name=\"csrfToken\" value=\"tok\">")).as("no sessionId").isEmpty();
        assertThat(ConsentForm.parse("<input name=\"csrfToken\"><input name=\"sessionId\" value=\"s\">")).as("no value").isEmpty();
        assertThat(ConsentForm.parse("")).isEmpty();
    }
}
