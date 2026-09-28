package Web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adiciona cabecalhos defensivos a todas as paginas servidas pela aplicacao. */
@Component
public class FiltroSeguranca extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest pedido, HttpServletResponse resposta, FilterChain cadeia)
            throws ServletException, IOException {
        resposta.setHeader("X-Content-Type-Options", "nosniff");
        resposta.setHeader("X-Frame-Options", "DENY");
        resposta.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        resposta.setHeader("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; script-src 'self' 'unsafe-inline'");
        cadeia.doFilter(pedido, resposta);
    }
}
