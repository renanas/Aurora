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
    public ProblemDetail handleNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Não encontrado", exception.code(), exception.getMessage());
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException exception) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, "Regra de negócio violada", exception.code(), exception.getMessage());
    }

    @ExceptionHandler(OverlapDetectedException.class)
    public ProblemDetail handleOverlap(OverlapDetectedException exception) {
        int total = exception.conflicts().size();
        String detail = total == 1
                ? "O horário se sobrepõe a 1 sessão existente."
                : "O horário se sobrepõe a " + total + " sessões existentes.";
        ProblemDetail problemDetail = problem(HttpStatus.CONFLICT, "Horário em conflito", exception.code(), detail);
        problemDetail.setProperty("conflicts", exception.conflicts().stream().map(GlobalExceptionHandler::toBody).toList());
        return problemDetail;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentModification(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Edição simultânea", "CONCURRENT_MODIFICATION",
                "Este registro foi alterado em outro lugar. Atualize a tela e tente novamente.");
    }

    // ---------- erro inesperado ----------

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception, HttpServletRequest request) {
        Throwable root = rootCause(exception);
        log.error("Erro inesperado: type={} rootType={} method={} path={} frames={}",
                exception.getClass().getName(), root.getClass().getName(),
                request.getMethod(), request.getRequestURI(), topFrames(root));
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno", "INTERNAL_ERROR",
                "Ocorreu um erro inesperado. Tente novamente.");
    }

    // ---------- erros de entrada (framework) ----------

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<FieldProblem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> new FieldProblem(fieldError.getField(), fieldError.getDefaultMessage()))
                .toList();
        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, "Dados inválidos", "VALIDATION_ERROR",
                "Um ou mais campos são inválidos.");
        problemDetail.setProperty("errors", errors);
        return createResponseEntity(problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Não repassar ex.getMessage(): pode citar trechos do corpo enviado.
        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, "Requisição inválida", "VALIDATION_ERROR",
                "O corpo da requisição está malformado.");
        return createResponseEntity(problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        // Não repassar ex.getMessage(): ela ecoa o valor recebido.
        ProblemDetail problemDetail = problem(HttpStatus.BAD_REQUEST, "Parâmetro inválido", "VALIDATION_ERROR",
                "Um parâmetro da requisição tem formato inválido.");
        return createResponseEntity(problemDetail, headers, HttpStatus.BAD_REQUEST, request);
    }

    /** Garante "code" e "requestId" também nos erros que o Spring MVC monta sozinho. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(exception, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problemDetail) {
            Map<String, Object> props = problemDetail.getProperties();
            if (props == null || !props.containsKey("code")) {
                problemDetail.setProperty("code", "REQUEST_ERROR");
            }
            if (props == null || !props.containsKey("requestId")) {
                problemDetail.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
            }
        }
        return response;
    }

    // ---------- utilitários ----------

    private static ProblemDetail problem(HttpStatus status, String title, String code, String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setProperty("code", code);
        problemDetail.setProperty("requestId", MDC.get(RequestIdFilter.MDC_KEY));
        return problemDetail;
    }

    private static ConflictBody toBody(Conflict conflict) {
        return new ConflictBody(conflict.sessionId().toString(), conflict.patientName(),
                LOCAL_DATE_TIME.format(conflict.startsAt()), LOCAL_DATE_TIME.format(conflict.endsAt()));
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /** Só nomes de classe/método/linha: úteis para depurar e sem dados pessoais. */
    private static String topFrames(Throwable throwable) {
        return Arrays.stream(throwable.getStackTrace())
                .limit(8)
                .map(StackTraceElement::toString)
                .collect(Collectors.joining(" <- "));
    }

    record FieldProblem(String field, String message) {
    }

    record ConflictBody(String sessionId, String patientName, String startsAt, String endsAt) {
    }
}
