package com.bibliometria.api.error;

import com.bibliometria.api.service.AnalysisNotPossibleException;
import com.bibliometria.api.service.ArticleNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        ConstraintViolationException.class,
        HttpMessageNotReadableException.class
    })
    public ResponseEntity<ProblemDetail> handleBadRequest(
            Exception exception, HttpServletRequest request) {
        ProblemDetail problem =
                problem(
                        HttpStatus.BAD_REQUEST,
                        "Solicitud inválida",
                        "La solicitud no tiene el formato esperado o contiene datos inválidos.",
                        request);
        if (exception instanceof MethodArgumentNotValidException validationException) {
            List<Map<String, String>> errors =
                    validationException.getBindingResult().getFieldErrors().stream()
                            .sorted(Comparator.comparing(error -> error.getField()))
                            .map(
                                    error ->
                                            Map.of(
                                                    "name",
                                                    error.getField(),
                                                    "reason",
                                                    "El valor no cumple las restricciones."))
                            .toList();
            problem.setProperty("invalidParams", errors);
        }
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({ArticleNotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ProblemDetail> handleNotFound(
            Exception exception, HttpServletRequest request) {
        ProblemDetail problem =
                problem(
                        HttpStatus.NOT_FOUND,
                        "Recurso no encontrado",
                        exception instanceof ArticleNotFoundException
                                ? exception.getMessage()
                                : "La ruta solicitada no existe.",
                        request);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(problem);
    }

    @ExceptionHandler({AnalysisNotPossibleException.class, IllegalArgumentException.class})
    public ResponseEntity<ProblemDetail> handleUnprocessable(
            Exception exception, HttpServletRequest request) {
        String detail =
                exception instanceof AnalysisNotPossibleException
                        ? exception.getMessage()
                        : "No fue posible calcular el resultado con los datos proporcionados.";
        ProblemDetail problem =
                problem(HttpStatusCode.valueOf(422), "Análisis no procesable", detail, request);
        return ResponseEntity.status(422).body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(
            Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOGGER.error("Error interno en petición correlationId={}", correlationId, exception);
        ProblemDetail problem =
                problem(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Error interno",
                        "Ocurrió un error inesperado. Use el ID de correlación para solicitar soporte.",
                        request);
        return ResponseEntity.internalServerError().body(problem);
    }

    private ProblemDetail problem(
            HttpStatusCode status, String title, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("correlationId", correlationId(request));
        return problem;
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value instanceof String id ? id : "no-disponible";
    }
}
