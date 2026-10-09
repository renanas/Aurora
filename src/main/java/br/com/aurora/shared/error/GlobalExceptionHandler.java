package br.com.aurora.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Converte exceções em Problem Details (RFC 9457) com um campo "code" estável.
 *
 * Regras de privacidade:
 *  - o corpo da requisição nunca é ecoado na resposta nem registrado em log;
 *  - o log de erro inesperado NÃO registra a mensagem da exceção (mensagens de banco incluem valores,
 *    como o telefone) nem a query string da URL (pode conter o nome buscado);
 *  - stack trace nunca vai ao cliente.
 *
 * Quando entrar Spring Security (incremento 14), AccessDeniedException e AuthenticationException
 * precisarão de tratamento próprio para não cair no 500 genérico.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final DateTimeFormatter LOCAL_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

    // ---------- erros do domínio ----------

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Não encontrado", ex.code(), ex.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", ex.code(), ex.getMessage());
    }

    @ExceptionHandler(OverlapDetectedException.class)
    public ProblemDetail handleOverlap(OverlapDetectedException ex) {
        int total = ex.conflicts().size();
        String detail = total == 1
                ? "O horário se sobrepõe a 1 sessão existente."
                : "O horário se sobrepõe a " + total + " sessões existentes.";
        ProblemDetail pd = problem(HttpStatus.CONFLICT, "Horário em conflito", ex.code(), detail);
        pd.setProperty("conflicts", ex.conflicts().stream().map(GlobalExceptionHandler::toBody).toList());
        return pd;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Edição simultânea", "CONCURRENT_MODIFICATION",
                "Este registro foi alterado em outro lugar. Atualize a tela e tente novamente.");
    }

    // ---------- erro inesperado ----------

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        Throwable root = rootCause(ex);
        log.error("Erro inesperado: type={} rootType={} method={} path={} frames={}",
                ex.getClass().getName(), root.getClass().getName(),
                request.getMethod(), request.getRequestURI(), topFrames(root));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "INTERNAL_ERROR",
                "Ocorreu um erro inesperado. Tente novamente.");
    }

    // ---------- erros de entrada (framework) ----------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldProblem(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "VALIDATION_ERROR",
                "Um ou mais campos são inválidos.");
        pd.setProperty("errors", errors);
        return createResponseEntity(pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Não repassar ex.getMessage(): pode citar trechos do corpo enviado.
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Requisição inválida", "VALIDATION_ERROR",
                "O corpo da requisição está malformado.");
        return createResponseEntity(pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Não repassar ex.getMessage(): ela ecoa o valor recebido.
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, "Parâmetro inválido", "VALIDATION_ERROR",
                "Um parâmetro da requisição tem formato inválido.");
        return createResponseEntity(pd, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Garante "code" e "requestId" também nos erros que o Spring MVC monta sozinho. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail pd) {
            Map<String, Object> props = pd.getProperties();
            if (props == null || !props.containsKey("code")) {
                pd.setProperty("code", "REQUEST_ERROR");
            }
            if (props == null || !props.containsKey("requestId")) {
                pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
            }
        }
        return response;
    }

    // ---------- utilitários ----------

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(title);
        pd.setProperty("code", code);
        pd.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return pd;
    }

    private static ConflictBody toBody(Conflict c) {
        return new ConflictBody(c.sessionId().toString(), c.patientName(),
                LOCAL_DATE_TIME.format(c.startsAt()), LOCAL_DATE_TIME.format(c.endsAt()));
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Só nomes de classe/método/linha: úteis para depurar e sem dados pessoais. */
    private static String topFrames(Throwable t) {
        return Arrays.stream(t.getStackTrace())
                .limit(8)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" <- "));
    }

    record FieldProblem(String field, String message) {
    }

    record ConflictBody(String sessionId, String patientName, String startsAt, String endsAt) {
    }
}
