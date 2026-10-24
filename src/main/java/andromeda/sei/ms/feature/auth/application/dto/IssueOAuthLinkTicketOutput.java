package andromeda.sei.ms.feature.auth.application.dto;

public record IssueOAuthLinkTicketOutput(String ticket, long expiresIn) {}
