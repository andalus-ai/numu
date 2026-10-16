package tech.kayys.numou.crm.interfaces.rest;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import tech.kayys.numou.crm.application.api.CrmService;
import tech.kayys.numou.crm.application.api.command.CreateEmailTemplateCommand;
import tech.kayys.numou.crm.application.api.command.SendEmailCommand;
import tech.kayys.numou.crm.application.api.command.CreateEmailCampaignCommand;
import tech.kayys.numou.crm.application.api.command.StartEmailCampaignCommand;
import tech.kayys.numou.crm.domain.identifier.CampaignId;
import tech.kayys.numou.crm.domain.identifier.EmailMessageId;
import tech.kayys.numou.crm.domain.identifier.EmailTemplateId;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * REST API for email management.
 */
@Path("/api/v1/email")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Email API", description = "Email management endpoints")
public class EmailResource {

    @Inject
    CrmService crmService;

    @POST
    @Path("/send")
    @Operation(summary = "Send an email")
    @APIResponse(responseCode = "200", description = "Email sent")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public CompletionStage<Response> sendEmail(@Valid SendEmailRequest request) {
        SendEmailCommand command = SendEmailCommand.builder()
            .fromEmail(request.getFromEmail())
            .fromName(request.getFromName())
            .to(request.getTo())
            .cc(request.getCc())
            .bcc(request.getBcc())
            .subject(request.getSubject())
            .body(request.getBody())
            .htmlBody(request.getHtmlBody())
            .replyTo(request.getReplyTo())
            .attachments(request.getAttachments())
            .variables(request.getVariables())
            .build();

        return crmService.sendEmail(command)
            .thenApply(messageId -> Response
                .ok(new SendEmailResponse(messageId))
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/templates")
    @Operation(summary = "Create an email template")
    @APIResponse(responseCode = "201", description = "Template created")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public CompletionStage<Response> createTemplate(@Valid CreateTemplateRequest request) {
        CreateEmailTemplateCommand command = CreateEmailTemplateCommand.builder()
            .name(request.getName())
            .subject(request.getSubject())
            .body(request.getBody())
            .htmlBody(request.getHtmlBody())
            .category(request.getCategory())
            .fromEmail(request.getFromEmail())
            .fromName(request.getFromName())
            .replyTo(request.getReplyTo())
            .build();

        return crmService.createEmailTemplate(command)
            .thenApply(templateId -> Response
                .created(URI.create("/api/v1/email/templates/" + templateId.getValue()))
                .entity(new CreateTemplateResponse(templateId))
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/campaigns")
    @Operation(summary = "Create an email campaign")
    @APIResponse(responseCode = "201", description = "Campaign created")
    @APIResponse(responseCode = "400", description = "Invalid input")
    public CompletionStage<Response> createCampaign(@Valid CreateCampaignRequest request) {
        CreateEmailCampaignCommand command = CreateEmailCampaignCommand.builder()
            .name(request.getName())
            .subject(request.getSubject())
            .templateId(request.getTemplateId())
            .recipientGroups(request.getRecipientGroups())
            .scheduledAt(request.getScheduledAt())
            .build();

        return crmService.createEmailCampaign(command)
            .thenApply(campaignId -> Response
                .created(URI.create("/api/v1/email/campaigns/" + campaignId.getValue()))
                .entity(new CreateCampaignResponse(campaignId))
                .build()
            )
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.BAD_REQUEST)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    @POST
    @Path("/campaigns/{id}/start")
    @Operation(summary = "Start an email campaign")
    @APIResponse(responseCode = "200", description = "Campaign started")
    @APIResponse(responseCode = "404", description = "Campaign not found")
    public CompletionStage<Response> startCampaign(@PathParam("id") UUID id) {
        CampaignId campaignId = CampaignId.of(id);
        StartEmailCampaignCommand command = new StartEmailCampaignCommand(campaignId);

        return crmService.startEmailCampaign(command)
            .thenApply(response -> Response.ok().build())
            .exceptionally(throwable -> {
                if (throwable.getCause() instanceof IllegalArgumentException) {
                    return Response.status(Response.Status.NOT_FOUND)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                if (throwable.getCause() instanceof IllegalStateException) {
                    return Response.status(Response.Status.CONFLICT)
                        .entity(throwable.getCause().getMessage())
                        .build();
                }
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).build();
            });
    }

    // =========================================================================
    // Request/Response DTOs
    // =========================================================================

    public static class SendEmailRequest {
        private String fromEmail;
        private String fromName;
        private List<String> to;
        private List<String> cc;
        private List<String> bcc;
        private String subject;
        private String body;
        private String htmlBody;
        private String replyTo;
        private List<String> attachments;
        private Map<String, String> variables;

        public String getFromEmail() { return fromEmail; }
        public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        public List<String> getTo() { return to; }
        public void setTo(List<String> to) { this.to = to; }
        public List<String> getCc() { return cc; }
        public void setCc(List<String> cc) { this.cc = cc; }
        public List<String> getBcc() { return bcc; }
        public void setBcc(List<String> bcc) { this.bcc = bcc; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getHtmlBody() { return htmlBody; }
        public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
        public String getReplyTo() { return replyTo; }
        public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
        public List<String> getAttachments() { return attachments; }
        public void setAttachments(List<String> attachments) { this.attachments = attachments; }
        public Map<String, String> getVariables() { return variables; }
        public void setVariables(Map<String, String> variables) { this.variables = variables; }
    }

    public static class CreateTemplateRequest {
        private String name;
        private String subject;
        private String body;
        private String htmlBody;
        private String category;
        private String fromEmail;
        private String fromName;
        private String replyTo;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getBody() { return body; }
        public void setBody(String body) { this.body = body; }
        public String getHtmlBody() { return htmlBody; }
        public void setHtmlBody(String htmlBody) { this.htmlBody = htmlBody; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getFromEmail() { return fromEmail; }
        public void setFromEmail(String fromEmail) { this.fromEmail = fromEmail; }
        public String getFromName() { return fromName; }
        public void setFromName(String fromName) { this.fromName = fromName; }
        public String getReplyTo() { return replyTo; }
        public void setReplyTo(String replyTo) { this.replyTo = replyTo; }
    }

    public static class CreateCampaignRequest {
        private String name;
        private String subject;
        private String templateId;
        private List<String> recipientGroups;
        private Instant scheduledAt;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public List<String> getRecipientGroups() { return recipientGroups; }
        public void setRecipientGroups(List<String> recipientGroups) { this.recipientGroups = recipientGroups; }
        public Instant getScheduledAt() { return scheduledAt; }
        public void setScheduledAt(Instant scheduledAt) { this.scheduledAt = scheduledAt; }
    }

    public static class SendEmailResponse {
        private final String messageId;

        public SendEmailResponse(EmailMessageId messageId) {
            this.messageId = messageId.toString();
        }

        public String getMessageId() { return messageId; }
    }

    public static class CreateTemplateResponse {
        private final String templateId;

        public CreateTemplateResponse(EmailTemplateId templateId) {
            this.templateId = templateId.toString();
        }

        public String getTemplateId() { return templateId; }
    }

    public static class CreateCampaignResponse {
        private final String campaignId;

        public CreateCampaignResponse(CampaignId campaignId) {
            this.campaignId = campaignId.toString();
        }

        public String getCampaignId() { return campaignId; }
    }
}