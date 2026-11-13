package tech.kayys.numou.survey.domain.survey;

import org.junit.jupiter.api.Test;
import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.SectionId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.question.QuestionType;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SurveyTemplateTest {
    @Test
    void templateInstantiatesIndependentDraftWithVersionedSurveyIdentity() {
        Instant now = Instant.parse("2026-11-11T10:00:00Z");
        Section section = new Section(new SectionId("experience"), "Experience", "",
                List.of(Question.of("satisfaction", "How satisfied are you?", new QuestionType.Rating(5)).mandatory()));
        SurveyTemplate template = new SurveyTemplate("post-service-csat", 2,
                "Post-service satisfaction", "Reusable CSAT questionnaire",
                SurveyKind.SURVEY, List.of(section), SurveySettings.defaults());

        Survey survey = template.instantiate(SurveyId.of("survey-1"), TenantId.generate(), now);

        assertEquals(SurveyStatus.DRAFT, survey.status());
        assertEquals(1, survey.version());
        assertEquals("Post-service satisfaction", survey.title());
        assertEquals(1, survey.questionCount());
        assertTrue(survey.publishProblems().isEmpty());
    }

    @Test
    void rejectsDuplicateQuestionIdsAndInvalidTemplates() {
        SectionId sectionId = new SectionId("section");
        Section duplicateQuestions = new Section(sectionId, "Section", "", List.of(
                Question.of("same", "First", new QuestionType.Rating(5)),
                Question.of("same", "Second", new QuestionType.Rating(5))));
        assertThrows(IllegalArgumentException.class, () -> new SurveyTemplate("duplicate-questions", 1,
                "Duplicate", "", SurveyKind.SURVEY, List.of(duplicateQuestions), null));
    }
}