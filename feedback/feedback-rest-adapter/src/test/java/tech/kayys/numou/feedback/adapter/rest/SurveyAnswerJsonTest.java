package tech.kayys.numou.feedback.adapter.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import tech.kayys.numou.survey.domain.answer.AnswerValue;

import static org.junit.jupiter.api.Assertions.*;

class SurveyAnswerJsonTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void parsesSupportedAnswerVariants() throws Exception {
        assertEquals(new AnswerValue.Text("hello"), parse("{\"kind\":\"text\",\"value\":\"hello\"}"));
        assertEquals(new AnswerValue.Numeric(4.5), parse("{\"kind\":\"number\",\"value\":4.5}"));
        assertEquals(new AnswerValue.Bool(true), parse("{\"kind\":\"bool\",\"value\":true}"));
        assertEquals(new AnswerValue.Day(java.time.LocalDate.parse("2026-11-17")),
                parse("{\"kind\":\"day\",\"value\":\"2026-11-17\"}"));
        assertEquals(1, ((AnswerValue.Choice) parse("{\"kind\":\"choice\",\"selected\":[\"a\"]}")).selected().size());
        assertEquals(2, ((AnswerValue.Ranking) parse("{\"kind\":\"ranking\",\"order\":[\"a\",\"b\"]}")).order().size());
        assertEquals(1, ((AnswerValue.Matrix) parse("{\"kind\":\"matrix\",\"cells\":{\"row\":[\"col\"]}}")).cells().size());
    }

    @Test
    void rejectsMalformedAndNonFiniteAnswers() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> parse("{\"kind\":\"number\",\"value\":\"NaN\"}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"kind\":\"choice\",\"selected\":\"a\"}"));
        assertThrows(IllegalArgumentException.class, () -> parse("{\"kind\":\"unknown\"}"));
        assertThrows(IllegalArgumentException.class, () -> parse("[]"));
    }

    private AnswerValue parse(String value) throws Exception {
        return SurveyAnswerJson.parse(json.readTree(value));
    }
}
