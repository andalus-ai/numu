package tech.kayys.syirkah.survey.domain.question;

import tech.kayys.syirkah.survey.domain.Guard;
import tech.kayys.syirkah.survey.domain.id.QuestionId;
import tech.kayys.syirkah.survey.domain.logic.Condition;
import tech.kayys.syirkah.survey.domain.quiz.Scoring;

import java.util.ArrayList;
import java.util.List;

/** Immutable question definition. Null-free: absent logic = Condition.ALWAYS, absent scoring = Scoring.NONE. */
public record Question(QuestionId id, String title, String description, boolean required, QuestionType type,
                       Condition visibleIf, Scoring scoring, String explanation) {
    public Question {
        Guard.notNull(id, "question id");
        title = Guard.notBlank(title, "question title");
        Guard.notNull(type, "question type");
        description = description == null ? "" : description;
        visibleIf = visibleIf == null ? Condition.ALWAYS : visibleIf;
        scoring = scoring == null ? Scoring.NONE : scoring;
        explanation = explanation == null ? "" : explanation;
    }

    public static Question of(String id, String title, QuestionType type) {
        return new Question(new QuestionId(id), title, "", false, type, null, null, null);
    }

    public Question mandatory() { return new Question(id, title, description, true, type, visibleIf, scoring, explanation); }
    public Question described(String d) { return new Question(id, title, d, required, type, visibleIf, scoring, explanation); }
    public Question showIf(Condition c) { return new Question(id, title, description, required, type, c, scoring, explanation); }
    public Question scored(Scoring s) { return new Question(id, title, description, required, type, visibleIf, s, explanation); }
    public Question explained(String e) { return new Question(id, title, description, required, type, visibleIf, scoring, e); }

    /** Definition-level problems (bad options, scoring that does not fit the type, ...). */
    public List<String> problems() {
        List<String> p = new ArrayList<>(type.definitionProblems());
        p.addAll(scoring.compatibilityProblems(type));
        return p;
    }
}
