package io.surveyhub.adapter.rest;

import com.fasterxml.jackson.databind.JsonNode;
import io.surveyhub.application.TenantContext;
import io.surveyhub.application.port.in.PublishingUseCase;
import io.surveyhub.application.port.in.SurveyAuthoringUseCase;
import io.surveyhub.application.port.in.SurveyAuthoringUseCase.AddQuestion;
import io.surveyhub.application.port.in.SurveyAuthoringUseCase.AddSection;
import io.surveyhub.application.port.in.SurveyAuthoringUseCase.CreateSurvey;
import io.surveyhub.application.port.in.TemplateUseCase;
import io.surveyhub.domain.id.SectionId;
import io.surveyhub.domain.id.SurveyId;
import io.surveyhub.domain.invitation.Invitation;
import io.surveyhub.domain.survey.AccessMode;
import io.surveyhub.domain.survey.Survey;
import io.surveyhub.domain.survey.SurveyKind;
import io.surveyhub.presentation.Html;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Look & feel, distribution (share link, embed, invitations), templates, duplicate, JSON import/export. */
@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class PublishingResource {
    private final PublishingUseCase publishing;
    private final SurveyAuthoringUseCase authoring;
    private final TemplateUseCase templates;
    private final RequestTenant tenant;
    private final String configuredBaseUrl;

    @Inject
    public PublishingResource(PublishingUseCase publishing, SurveyAuthoringUseCase authoring, TemplateUseCase templates,
                              RequestTenant tenant, @ConfigProperty(name = "survey.public-base-url", defaultValue = "") String baseUrl) {
        this.publishing = publishing;
        this.authoring = authoring;
        this.templates = templates;
        this.tenant = tenant;
        this.configuredBaseUrl = baseUrl;
    }

    private String baseUrl(UriInfo uri) {
        String b = configuredBaseUrl.isBlank() ? uri.getBaseUri().toString() : configuredBaseUrl;
        return b.endsWith("/") ? b.substring(0, b.length() - 1) : b;
    }

    // ------------------------------------------------------------------ presentation & access

    /** Body: any subset of {primaryColor, backgroundColor, textColor, fontFamily, logoUrl, layout, showProgress, welcomeMessage}. */
    @PUT @Path("surveys/{id}/presentation")
    public Map<String, Object> presentation(@PathParam("id") String id, JsonNode body) {
        TenantContext ctx = tenant.get();
        Survey current = authoring.getSurvey(ctx, new SurveyId(id));
        return SurveyJson.render(publishing.updatePresentation(ctx, current.id(),
                SurveyJson.parsePresentation(body, current.presentation())));
    }

    /** Body: {"access":"PRIVATE"|"PUBLIC_LINK"|"INVITE_ONLY"} */
    @PUT @Path("surveys/{id}/access")
    public Map<String, Object> access(@PathParam("id") String id, JsonNode body) {
        return SurveyJson.render(publishing.configureAccess(tenant.get(), new SurveyId(id),
                AccessMode.valueOf(body.path("access").asText("PRIVATE").toUpperCase())));
    }

    /** Share link + iframe embed code (available once published and not PRIVATE). */
    @GET @Path("surveys/{id}/share")
    public Map<String, Object> share(@PathParam("id") String id, @Context UriInfo uri) {
        Survey s = authoring.getSurvey(tenant.get(), new SurveyId(id));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("access", s.accessMode());
        m.put("status", s.status());
        if (s.shareToken().isPresent() && s.accessMode() != AccessMode.PRIVATE) {
            String url = baseUrl(uri) + "/p/" + s.shareToken().get();
            m.put("url", url);
            m.put("embedCode", "<iframe src=\"" + Html.esc(url) + "\" title=\"" + Html.esc(s.title())
                    + "\" style=\"width:100%;height:720px;border:0\" loading=\"lazy\"></iframe>");
        }
        return m;
    }

    // ------------------------------------------------------------------ invitations

    /** Body: {"emails":["a@x.com", ...]} */
    @POST @Path("surveys/{id}/invitations")
    public Response invite(@PathParam("id") String id, JsonNode body, @Context UriInfo uri) {
        List<String> emails = new ArrayList<>();
        body.path("emails").forEach(n -> emails.add(n.asText()));
        List<Invitation> created = publishing.invite(tenant.get(), new SurveyId(id), emails, baseUrl(uri));
        return Response.status(201).entity(created.stream().map(PublishingResource::render).toList()).build();
    }

    @GET @Path("surveys/{id}/invitations")
    public List<Map<String, Object>> invitations(@PathParam("id") String id) {
        return publishing.invitations(tenant.get(), new SurveyId(id)).stream().map(PublishingResource::render).toList();
    }

    private static Map<String, Object> render(Invitation i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("email", i.email());
        m.put("used", i.used());
        m.put("createdAt", i.createdAt().toString());
        if (i.usedAt() != null) m.put("usedAt", i.usedAt().toString());
        return m; // the token itself is only ever delivered via the notification channel
    }

    // ------------------------------------------------------------------ templates, duplicate, import/export

    @GET @Path("templates")
    public List<Map<String, Object>> templates() {
        return templates.list().stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.id());
            m.put("name", t.name());
            m.put("category", t.category());
            m.put("purpose", t.purpose());
            m.put("description", t.description());
            m.put("kind", t.kind());
            m.put("questions", t.questions());
            return m;
        }).toList();
    }

    /** Body (optional): {"title":"..."} */
    @POST @Path("templates/{templateId}/instantiate")
    public Response fromTemplate(@PathParam("templateId") String templateId, JsonNode body) {
        Survey s = templates.instantiate(tenant.get(), templateId, body == null ? null : body.path("title").asText(null));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    @POST @Path("surveys/{id}/duplicate")
    public Response duplicate(@PathParam("id") String id, JsonNode body) {
        Survey s = authoring.duplicate(tenant.get(), new SurveyId(id), body == null ? null : body.path("title").asText(null));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }

    /** Full definition including answer keys (management only). Round-trips through {@link #importDefinition}. */
    @GET @Path("surveys/{id}/definition")
    public Map<String, Object> definition(@PathParam("id") String id) {
        return SurveyJson.render(authoring.getSurvey(tenant.get(), new SurveyId(id)));
    }

    /** Creates a new draft from a definition JSON ({title, kind, sections:[{title,questions:[...]}], settings?, quiz?, presentation?}). */
    @POST @Path("surveys/import")
    public Response importDefinition(JsonNode def) {
        TenantContext ctx = tenant.get();
        SurveyKind kind = def.hasNonNull("kind") ? SurveyKind.valueOf(def.get("kind").asText().toUpperCase()) : SurveyKind.SURVEY;
        Survey s = authoring.createSurvey(ctx, new CreateSurvey(def.path("title").asText(null), def.path("description").asText(""), kind));
        for (JsonNode sec : def.path("sections")) {
            s = authoring.addSection(ctx, new AddSection(s.id(), sec.path("title").asText(null), sec.path("description").asText("")));
            SectionId sid = s.sections().get(s.sections().size() - 1).id();
            for (JsonNode q : sec.path("questions"))
                s = authoring.addQuestion(ctx, new AddQuestion(s.id(), sid, SurveyJson.parseQuestion(q, null)));
        }
        if (def.has("settings")) s = authoring.updateSettings(ctx, s.id(), SurveyJson.parseSettings(def.get("settings"), s.settings()));
        if (def.has("quiz") && kind == SurveyKind.QUIZ)
            s = authoring.configureQuiz(ctx, s.id(), SurveyJson.parseQuiz(def.get("quiz"), s.quizSettings().orElseThrow()));
        if (def.has("presentation"))
            s = publishing.updatePresentation(ctx, s.id(), SurveyJson.parsePresentation(def.get("presentation"), s.presentation()));
        return Response.status(201).entity(SurveyJson.render(s)).build();
    }
}
