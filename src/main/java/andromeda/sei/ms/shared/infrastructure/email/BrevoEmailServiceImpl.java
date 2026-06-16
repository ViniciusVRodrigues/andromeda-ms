package andromeda.sei.ms.shared.infrastructure.email;

import andromeda.sei.ms.shared.application.port.EmailService;
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

    public BrevoEmailServiceImpl(BrevoConfig brevoConfig,
                                 @Value("${app.email.enabled:true}") boolean emailEnabled) {
        this.brevoConfig = brevoConfig;
        this.emailEnabled = emailEnabled;
    }

    @Override
    public void sendOtp(String email, String code) {
        if (!emailEnabled) {
            log.debug("email disabled — skipping OTP send to {}", email);
            return;
        }
        String subject = "Seu código de verificação Andromeda";
        String body = "Seu código de verificação é: " + code + "\n\nEsse código expira em 15 minutos.";
        send(email, subject, body);
    }

    @Override
    public void sendInvite(String email, String name, String inviteLink) {
        if (!emailEnabled) {
            log.debug("email disabled — skipping invite send to {}", email);
            return;
        }
        String subject = "Você foi convidado para o Andromeda";
        String body = "Olá" + (name != null ? ", " + name : "") + "!\n\n"
                + "Você foi convidado para ingressar na plataforma Andromeda.\n\n"
                + "Clique no link abaixo para completar seu cadastro:\n" + inviteLink;
        send(email, subject, body);
    }

    private void send(String toEmail, String subject, String body) {
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
        message.setTextContent(body);

        try {
            new TransactionalEmailsApi().sendTransacEmail(message);
        } catch (ApiException ex) {
            throw new RuntimeException("Failed to send email to " + toEmail + ": " + ex.getResponseBody(), ex);
        }
    }
}
