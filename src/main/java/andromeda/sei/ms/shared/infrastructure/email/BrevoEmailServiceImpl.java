package andromeda.sei.ms.shared.infrastructure.email;

import andromeda.sei.ms.shared.application.port.EmailService;
import andromeda.sei.ms.shared.application.port.FrontendUrlResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import sendinblue.ApiClient;
import sendinblue.ApiException;
import sendinblue.Configuration;
import sibApi.TransactionalEmailsApi;
import sibModel.SendSmtpEmail;
import sibModel.SendSmtpEmailSender;
import sibModel.SendSmtpEmailTo;

import java.util.List;

@Component
public class BrevoEmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(BrevoEmailServiceImpl.class);

    private final BrevoConfig brevoConfig;
    private final boolean emailEnabled;
    private final FrontendUrlResolver frontendUrlResolver;

    public BrevoEmailServiceImpl(BrevoConfig brevoConfig,
                                 @Value("${app.email.enabled:true}") boolean emailEnabled,
                                 FrontendUrlResolver frontendUrlResolver) {
        this.brevoConfig = brevoConfig;
        this.emailEnabled = emailEnabled;
        this.frontendUrlResolver = frontendUrlResolver;
    }

    @Override
    public void sendOtp(String email, String code) {
        if (!emailEnabled) {
            log.debug("email disabled — skipping OTP send to {}", email);
            return;
        }
        send(email, "Seu código de verificação Andromeda", buildOtpHtml(code));
    }

    @Override
    public void sendInvite(String email, String name, String inviteLink) {
        if (!emailEnabled) {
            log.debug("email disabled — skipping invite send to {}", email);
            return;
        }
        send(email, "Você foi convidado para o Andromeda", buildInviteHtml(name, inviteLink));
    }

    // ── Shared email shell ────────────────────────────────────────────────────

    private String emailShell(String heroLabel, String heroTitle, String bodyRows) {
        return """
                <!DOCTYPE html>
                <html lang="pt-BR">
                <head>
                  <meta charset="UTF-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <link rel="preconnect" href="https://fonts.googleapis.com">
                  <link href="https://fonts.googleapis.com/css2?family=DM+Serif+Display:ital@0;1&family=JetBrains+Mono:wght@400;800&display=swap" rel="stylesheet">
                </head>
                <body style="margin:0;padding:0;background:#0e131c;">

                  <!-- ── Page wrapper ── -->
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0e131c;min-height:100vh;">
                    <tr>
                      <td align="center" valign="top" style="padding:0;">

                        <!-- ── Header bar ── -->
                        <table width="100%%" cellpadding="0" cellspacing="0" style="background:#151B26;border-bottom:1px solid #1e2a3a;">
                          <tr>
                            <td align="center" style="padding:20px 24px;">
                              <table width="560" cellpadding="0" cellspacing="0">
                                <tr>
                                  <td>
                                    <img src="%s/assets/wordmark-mono.svg"
                                         alt="Andrômeda"
                                         width="120" height="18"
                                         style="display:block;border:0;outline:none;text-decoration:none;">
                                  </td>
                                  <td align="right">
                                    <span style="font-family:'JetBrains Mono',ui-monospace,monospace;font-size:10px;font-weight:800;letter-spacing:0.22em;text-transform:uppercase;color:#3d4f63;">SEI Platform</span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>

                        <!-- ── Hero ── -->
                        <table width="100%%" cellpadding="0" cellspacing="0" style="background:#151B26;">
                          <tr>
                            <td align="center" style="padding:56px 24px 48px;">
                              <p style="margin:0 0 12px;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:10px;font-weight:800;letter-spacing:0.28em;text-transform:uppercase;color:#C68B66;">%s</p>
                              <h1 style="margin:0;font-family:'DM Serif Display',Georgia,serif;font-size:42px;font-weight:400;color:#F4E3C9;line-height:1.15;letter-spacing:-0.01em;">%s</h1>
                            </td>
                          </tr>
                        </table>

                        <!-- ── Diagonal separator ── -->
                        <table width="100%%" cellpadding="0" cellspacing="0" style="background:#151B26;">
                          <tr><td style="height:1px;background:#1e2a3a;font-size:0;line-height:0;">&nbsp;</td></tr>
                        </table>

                        <!-- ── Content card ── -->
                        <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0e131c;padding:48px 24px 64px;">
                          <tr>
                            <td align="center">
                              <table width="560" cellpadding="0" cellspacing="0" style="background:#151B26;border:1px solid #1e2a3a;border-radius:14px;overflow:hidden;max-width:560px;">
                                %s
                                <!-- ── Card footer ── -->
                                <tr><td style="height:1px;background:#1e2a3a;font-size:0;line-height:0;">&nbsp;</td></tr>
                                <tr>
                                  <td style="padding:16px 32px;">
                                    <span style="font-family:'JetBrains Mono',ui-monospace,monospace;font-size:10px;font-weight:800;letter-spacing:0.22em;text-transform:uppercase;color:#3d4f63;">Andrômeda · Software Engineering Intelligence · TCC BES</span>
                                  </td>
                                </tr>
                              </table>
                            </td>
                          </tr>
                        </table>

                      </td>
                    </tr>
                  </table>

                </body>
                </html>
                """.formatted(frontendUrlResolver.resolve(), heroLabel, heroTitle, bodyRows);
    }

    // ── OTP ───────────────────────────────────────────────────────────────────

    private String buildOtpHtml(String code) {
        String rows = """
                <tr>
                  <td style="padding:36px 32px 32px;">
                    <p style="margin:0 0 6px;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:10px;font-weight:800;letter-spacing:0.22em;text-transform:uppercase;color:#5c6478;">Seu código</p>
                    <p style="margin:0 0 28px;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:13px;font-weight:400;color:#9a8e7e;line-height:1.7;">
                      Use o código abaixo para verificar seu e-mail e continuar o acesso à plataforma.
                    </p>

                    <!-- Code block -->
                    <table width="100%%" cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                      <tr>
                        <td align="center" style="background:#0e131c;border:1px solid #2e3a4e;border-radius:10px;padding:32px 24px;">
                          <span style="font-family:'JetBrains Mono',ui-monospace,monospace;font-size:48px;font-weight:800;letter-spacing:0.30em;color:#C68B66;">%s</span>
                        </td>
                      </tr>
                    </table>

                    <p style="margin:0;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:12px;font-weight:400;color:#5c6478;line-height:1.8;">
                      &#x2022; Válido por <span style="color:#F4E3C9;font-weight:800;">15 minutos</span><br>
                      &#x2022; Não compartilhe este código com ninguém<br>
                      &#x2022; Se você não solicitou este acesso, ignore este e-mail
                    </p>
                  </td>
                </tr>
                """.formatted(code);
        return emailShell("Verificação de e-mail", "Confirme seu acesso.", rows);
    }

    // ── Invite ────────────────────────────────────────────────────────────────

    private String buildInviteHtml(String name, String inviteLink) {
        String displayName = (name != null && !name.isBlank()) ? name : null;
        String greeting = displayName != null ? "Olá, " + displayName + "." : "Olá.";
        String rows = """
                <tr>
                  <td style="padding:36px 32px 32px;">
                    <p style="margin:0 0 6px;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:10px;font-weight:800;letter-spacing:0.22em;text-transform:uppercase;color:#5c6478;">Mensagem da plataforma</p>
                    <h2 style="margin:0 0 16px;font-family:'DM Serif Display',Georgia,serif;font-size:24px;font-weight:400;color:#F4E3C9;line-height:1.3;">%s</h2>
                    <p style="margin:0 0 32px;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:13px;font-weight:400;color:#9a8e7e;line-height:1.8;">
                      Você foi convidado para ingressar na <span style="color:#F4E3C9;font-weight:800;">Andrômeda</span> — a plataforma de Software Engineering Intelligence da sua organização.<br><br>
                      Clique no botão abaixo para criar sua conta e começar.
                    </p>

                    <!-- CTA button -->
                    <table cellpadding="0" cellspacing="0" style="margin-bottom:28px;">
                      <tr>
                        <td style="border-radius:999px;background:#C68B66;">
                          <a href="%s"
                             style="display:inline-block;padding:14px 32px;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:11px;font-weight:800;letter-spacing:0.18em;text-transform:uppercase;color:#0e131c;text-decoration:none;">
                            Criar minha conta &rarr;
                          </a>
                        </td>
                      </tr>
                    </table>

                    <p style="margin:0;font-family:'JetBrains Mono',ui-monospace,monospace;font-size:11px;font-weight:400;color:#3d4f63;line-height:1.7;">
                      Se o botão não funcionar, copie e cole este endereço no seu navegador:<br>
                      <a href="%s" style="color:#C68B66;text-decoration:none;word-break:break-all;">%s</a>
                    </p>
                  </td>
                </tr>
                """.formatted(greeting, inviteLink, inviteLink, inviteLink);
        return emailShell("Convite", "Você foi convidado.", rows);
    }

    private void send(String toEmail, String subject, String htmlBody) {
        ApiClient client = Configuration.getDefaultApiClient();
        client.setApiKey(brevoConfig.getApiKey());

        SendSmtpEmailSender sender = new SendSmtpEmailSender();
        sender.setEmail(brevoConfig.getSenderEmail());
        sender.setName(brevoConfig.getSenderName());

        SendSmtpEmailTo recipient = new SendSmtpEmailTo();
        recipient.setEmail(toEmail);

        SendSmtpEmail message = new SendSmtpEmail();
        message.setSender(sender);
        message.setTo(List.of(recipient));
        message.setSubject(subject);
        message.setHtmlContent(htmlBody);

        try {
            new TransactionalEmailsApi().sendTransacEmail(message);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to send email to " + toEmail + ": " + ex.getResponseBody(), ex);
        }
    }
}
