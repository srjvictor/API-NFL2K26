package com.nfl.api.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.accept.InvalidApiVersionException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Tratamento global de exceções: converte qualquer erro em um {@link ApiError}
 * com o status HTTP adequado e uma mensagem clara.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<ApiError> handleBusinessRule(BusinessRuleException ex, HttpServletRequest req) {
        return build(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage(), req);
    }

    /** Falha de Bean Validation em um @RequestBody @Valid. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<ApiError.FieldErrorItem> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldErrorItem(fe.getField(), fe.getDefaultMessage(), fe.getRejectedValue()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Dados de entrada inválidos", req, fields);
    }

    /** Falha de validação em parâmetros de método (@RequestParam, @PathVariable com constraints). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiError> handleMethodValidation(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<ApiError.FieldErrorItem> fields = new ArrayList<>();
        for (ParameterValidationResult result : ex.getParameterValidationResults()) {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err ->
                    fields.add(new ApiError.FieldErrorItem(name, err.getDefaultMessage(), result.getArgument())));
        }
        return build(HttpStatus.BAD_REQUEST, "Parâmetros inválidos", req, fields);
    }

    /** Violação de constraint detectada pelo Hibernate Validator ao persistir uma entidade. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest req) {
        List<ApiError.FieldErrorItem> fields = ex.getConstraintViolations().stream()
                .map(v -> new ApiError.FieldErrorItem(v.getPropertyPath().toString(), v.getMessage(), v.getInvalidValue()))
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Dados inválidos", req, fields);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST,
                "Corpo da requisição ausente ou malformado (verifique o JSON, datas e valores de enums)", req);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String expected = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "?";
        String msg = "Valor '%s' inválido para o parâmetro '%s' (tipo esperado: %s)"
                .formatted(ex.getValue(), ex.getName(), expected);
        return build(HttpStatus.BAD_REQUEST, msg, req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + ex.getParameterName(), req);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiError> handleMissingHeader(MissingRequestHeaderException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Header obrigatório ausente: " + ex.getHeaderName(), req);
    }

    /** Ordenação por propriedade inexistente (ex.: ?sort=foo). */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handleBadSort(PropertyReferenceException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Propriedade de ordenação inválida: " + ex.getPropertyName(), req);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, "Operação viola uma restrição de integridade dos dados", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " não suportado neste recurso", req);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type não suportado. Use application/json", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, "Rota não encontrada", req);
    }

    /** Versão não suportada no header X-API-Version. */
    @ExceptionHandler(InvalidApiVersionException.class)
    public ResponseEntity<ApiError> handleInvalidVersion(InvalidApiVersionException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, "Versão da API não suportada: '" + ex.getVersion()
                + "'. Valores aceitos no header X-API-Version: 1 (padrão) ou 2.", req);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleResponseStatus(ResponseStatusException ex, HttpServletRequest req) {
        HttpStatusCode code = ex.getStatusCode();
        String reason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        return build(HttpStatus.valueOf(code.value()), reason, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        log.error("Erro inesperado em {} {}", req.getMethod(), req.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado. Tente novamente mais tarde.", req);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req) {
        return build(status, message, req, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req,
                                           List<ApiError.FieldErrorItem> fields) {
        ApiError body = new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), message,
                req.getRequestURI(), fields);
        // Content-Type explícito: garante serialização mesmo quando o cliente pede application/hal+json
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
