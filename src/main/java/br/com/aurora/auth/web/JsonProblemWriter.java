package br.com.aurora.auth.web;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Escreve erros no mesmo formato do GlobalExceptionHandler (Problem Details com "code" estável)
 * para as respostas geradas pelos filtros de segurança, que ficam fora do Spring MVC.
 *
 * Todos os argumentos são constantes do código (sem aspas, sem dado do usuário): por isso a
 * montagem do JSON é feita à mão e nada da requisição é ecoado.
 */
final class JsonProblemWriter {

    private JsonProblemWriter() {
    }

    static void write(HttpServletResponse response, int status, String title, String code, String detail)
            throws IOException {
        String body = "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\",\"code\":\"" + code + "\"}";
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.setContentLength(bytes.length);
        response.getOutputStream().write(bytes);
    }
}
