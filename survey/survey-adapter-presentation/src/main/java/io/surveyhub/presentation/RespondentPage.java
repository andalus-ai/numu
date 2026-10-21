package io.surveyhub.presentation;

import io.surveyhub.domain.survey.Presentation;
import io.surveyhub.domain.survey.Survey;

/**
 * Self-contained, themeable respondent web page (no external assets; works as a share link or in an iframe).
 * It talks to /api/v1/public/{token}; question definitions are fetched at runtime so answer keys never ship in the page.
 */
public final class RespondentPage {
    private static final String CSS = Resources.text("respondent.css");
    private static final String JS = Resources.text("respondent.js");

    private RespondentPage() {}

    public static String render(Survey s, String shareToken) {
        Presentation p = s.presentation();
        String vars = ":root{--primary:" + p.primaryColor() + ";--bg:" + p.backgroundColor() + ";--text:" + p.textColor()
                + ";--font:" + p.fontFamily() + ";}";
        String config = "{\"token\":" + Html.jsonString(shareToken)
                + ",\"title\":" + Html.jsonString(s.title())
                + ",\"description\":" + Html.jsonString(s.description())
                + ",\"welcome\":" + Html.jsonString(p.welcomeMessage())
                + ",\"logo\":" + Html.jsonString(p.logoUrl())
                + ",\"layout\":" + Html.jsonString(p.layout().name())
                + ",\"showProgress\":" + p.showProgress()
                + ",\"thankYou\":" + Html.jsonString(s.settings().thankYouMessage()) + "}";
        return "<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">"
                + "<title>" + Html.esc(s.title()) + "</title>"
                + "<style>" + vars + CSS + "</style></head><body>"
                + "<main id=\"app\"></main><noscript><p style=\"padding:24px\">Please enable JavaScript to answer this survey.</p></noscript>"
                + "<script>window.SURVEY_CONFIG=" + config + ";</script>"
                + "<script>" + JS + "</script></body></html>";
    }
}
