package Web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Envia e-mails transacionais usando a API HTTPS da Brevo. */
@Component
public class ServicoEmailBrevo {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10)).build();
    private final String remetente;
    private final String chave;

    public ServicoEmailBrevo(@Value("${app.mail.from:}") String remetente,
            @Value("${app.mail.brevo-api-key:}") String chave) {
        this.remetente = remetente;
        this.chave = chave;
    }

    public void enviar(String destinatario, String assunto, String texto) throws IOException, InterruptedException {
        if (chave.isBlank() || remetente.isBlank()) {
            System.err.println("Brevo não configurada: BREVO_API_KEY "
                    + (chave.isBlank() ? "ausente" : "presente") + ", MAIL_FROM "
                    + (remetente.isBlank() ? "ausente" : "presente"));
            throw new IllegalStateException("O envio de e-mail não está configurado.");
        }
        String corpo = "{\"sender\":{\"name\":\"Make Your Health\",\"email\":" + json(remetente)
                + "},\"to\":[{\"email\":" + json(destinatario)
                + "}],\"subject\":" + json(assunto) + ",\"textContent\":" + json(texto) + "}";
        HttpRequest pedido = HttpRequest.newBuilder(URI.create("https://api.brevo.com/v3/smtp/email"))
                .timeout(java.time.Duration.ofSeconds(15))
                .header("accept", "application/json")
                .header("api-key", chave)
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(corpo, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resposta = HTTP.send(pedido,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resposta.statusCode() < 200 || resposta.statusCode() >= 300) {
            System.err.println("Falha no envio pela Brevo: HTTP " + resposta.statusCode());
            throw new IllegalStateException("A Brevo respondeu HTTP " + resposta.statusCode());
        }
    }

    private static String json(String valor) {
        StringBuilder resultado = new StringBuilder("\"");
        for (int i = 0; i < valor.length(); i++) {
            char c = valor.charAt(i);
            switch (c) {
                case '"' -> resultado.append("\\\"");
                case '\\' -> resultado.append("\\\\");
                case '\n' -> resultado.append("\\n");
                case '\r' -> resultado.append("\\r");
                case '\t' -> resultado.append("\\t");
                default -> {
                    if (c < 0x20) resultado.append(String.format("\\u%04x", (int) c));
                    else resultado.append(c);
                }
            }
        }
        return resultado.append('"').toString();
    }
}
