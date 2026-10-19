package tech.kayys.numou.survey.domain.survey;

import tech.kayys.numou.survey.domain.Guard;
import tech.kayys.numou.survey.domain.id.SectionId;
import tech.kayys.numou.survey.domain.question.Question;

import java.util.List;

public record Section(SectionId id, String title, String description, List<Question> questions) {
    public Section {
        Guard.notNull(id, "section id");
        title = Guard.notBlank(title, "section title");
        description = description == null ? "" : description;
        questions = List.copyOf(questions);
    }

    public Section withQuestions(List<Question> qs) { return new Section(id, title, description, qs); }
}
