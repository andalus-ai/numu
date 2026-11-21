package tech.kayys.numou.survey.domain.survey;

import tech.kayys.numou.foundation.domain.tenant.TenantId;
import tech.kayys.numou.survey.domain.id.SurveyId;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, versioned reusable survey blueprint; respondent data is never stored in a template. */
public record SurveyTemplate(
        String code,
        int version,
        String title,
        String description,
        SurveyKind kind,
        List<Section> sections,
        SurveySettings settings) {

    public SurveyTemplate {
        Objects.requireNonNull(code, "code cannot be null");
        code = code.trim().toLowerCase(java.util.Locale.ROOT);
        if (!code.matches("[a-z][a-z0-9._-]{1,63}")) throw new IllegalArgumentException("Invalid template code");
        if (version < 1) throw new IllegalArgumentException("template version must be positive");
        if (title == null || title.isBlank()) throw new IllegalArgumentException("template title cannot be blank");
        title = title.trim();
        description = description == null ? "" : description;
        Objects.requireNonNull(kind, "kind cannot be null");
        sections = List.copyOf(Objects.requireNonNull(sections, "sections cannot be null"));
        if (sections.isEmpty()) throw new IllegalArgumentException("template must contain at least one section");
        settings = settings == null ? SurveySettings.defaults() : settings;

        Set<String> sectionIds = new HashSet<>();
        Set<String> questionIds = new HashSet<>();
        sections.forEach(section -> {
            if (!sectionIds.add(section.id().value())) throw new IllegalArgumentException("Duplicate section id: " + section.id());
            section.questions().forEach(question -> {
                if (!questionIds.add(question.id().value())) throw new IllegalArgumentException("Duplicate question id: " + question.id());
                if (!question.problems().isEmpty()) throw new IllegalArgumentException(
                        "Invalid template question " + question.id() + ": " + question.problems());
            });
        });
    }

    /** Creates a new independent draft; future template edits cannot mutate this survey. */
    public Survey instantiate(SurveyId surveyId, TenantId tenantId, Instant now) {
        Survey survey = Survey.draftFromTemplate(surveyId, tenantId, this, now);
        List<String> problems = survey.publishProblems();
        if (!problems.isEmpty()) throw new IllegalArgumentException("Template cannot produce a publishable survey: " + problems);
        return survey;
    }
}