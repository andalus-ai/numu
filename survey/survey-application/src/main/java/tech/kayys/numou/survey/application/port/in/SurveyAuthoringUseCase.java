package tech.kayys.numou.survey.application.port.in;

import tech.kayys.numou.survey.application.TenantContext;
import tech.kayys.numou.survey.domain.id.QuestionId;
import tech.kayys.numou.survey.domain.id.SectionId;
import tech.kayys.numou.survey.domain.id.SurveyId;
import tech.kayys.numou.survey.domain.question.Question;
import tech.kayys.numou.survey.domain.quiz.QuizSettings;
import tech.kayys.numou.survey.domain.survey.Survey;
import tech.kayys.numou.survey.domain.survey.SurveyKind;
import tech.kayys.numou.survey.domain.survey.SurveySettings;
import tech.kayys.numou.survey.domain.survey.SurveyStatus;

import java.util.List;

/** Compose a survey/quiz: sections, questions of any format, logic, scoring, settings. Only DRAFT surveys are editable. */
public interface SurveyAuthoringUseCase {
    Survey createSurvey(TenantContext ctx, CreateSurvey cmd);

    Survey updateDetails(TenantContext ctx, SurveyId id, String title, String description);

    Survey addSection(TenantContext ctx, AddSection cmd);

    Survey addQuestion(TenantContext ctx, AddQuestion cmd);

    Survey updateQuestion(TenantContext ctx, SurveyId id, Question question);

    Survey removeQuestion(TenantContext ctx, SurveyId id, QuestionId question);

    Survey moveQuestion(TenantContext ctx, MoveQuestion cmd);

    Survey updateSettings(TenantContext ctx, SurveyId id, SurveySettings settings);

    Survey configureQuiz(TenantContext ctx, SurveyId id, QuizSettings quiz);

    Survey getSurvey(TenantContext ctx, SurveyId id);

    /** @param statusOrNull null = all statuses */
    List<Survey> listSurveys(TenantContext ctx, SurveyStatus statusOrNull);

    record CreateSurvey(String title, String description, SurveyKind kind) {}

    record AddSection(SurveyId survey, String title, String description) {}

    record AddQuestion(SurveyId survey, SectionId section, Question question) {}

    record MoveQuestion(SurveyId survey, QuestionId question, SectionId toSection, int index) {}
}
