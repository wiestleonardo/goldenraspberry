package br.com.outsera.goldenraspberry.config;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import br.com.outsera.goldenraspberry.dto.ErroDTO;
import br.com.outsera.goldenraspberry.enumerator.ErroEnum;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private static final Locale LOCALE_PORTUGUES_BRASIL = Locale.of("pt", "BR");

    private final MessageSource messageSource;

    public ApiExceptionHandler(MessageSource messageSource) {

        this.messageSource = messageSource;
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> tratarExcecaoNaoPrevista(Exception exception) {

        log.error("Falha não prevista ao processar a requisição", exception);

        return ResponseEntity.status(ErroEnum.ERRO_NAO_TRATADO.getHttpStatus()).body(this.montarErroDTO(ErroEnum.ERRO_NAO_TRATADO));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body, HttpHeaders httpHeaders,
            HttpStatusCode httpStatusCode, WebRequest webRequest) {

        return ResponseEntity.status(httpStatusCode).headers(httpHeaders).body(this.montarErroDTO(this.identificarErro(httpStatusCode)));
    }

    private ErroEnum identificarErro(HttpStatusCode httpStatusCode) {

        if (httpStatusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {

            return ErroEnum.RECURSO_NAO_ENCONTRADO;
        }

        if (httpStatusCode.isSameCodeAs(HttpStatus.METHOD_NOT_ALLOWED)) {

            return ErroEnum.METODO_NAO_SUPORTADO;
        }

        return ErroEnum.REQUISICAO_INVALIDA;
    }

    private ErroDTO montarErroDTO(ErroEnum erroEnum) {

        return new ErroDTO(erroEnum.getCodigo(), this.messageSource.getMessage(erroEnum.getChaveMensagem(), null, LOCALE_PORTUGUES_BRASIL));
    }

}
