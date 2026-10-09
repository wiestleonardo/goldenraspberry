package br.com.outsera.goldenraspberry.enumerator;

import org.springframework.http.HttpStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErroEnum {

    ARQUIVO_FILME_NAO_ENCONTRADO("ARQUIVO_FILME_NAO_ENCONTRADO", "erro.arquivo-filme-nao-encontrado", HttpStatus.INTERNAL_SERVER_ERROR),
    ARQUIVO_FILME_ILEGIVEL("ARQUIVO_FILME_ILEGIVEL", "erro.arquivo-filme-ilegivel", HttpStatus.INTERNAL_SERVER_ERROR),
    CABECALHO_ARQUIVO_FILME_INVALIDO("CABECALHO_ARQUIVO_FILME_INVALIDO", "erro.cabecalho-arquivo-filme-invalido",
            HttpStatus.INTERNAL_SERVER_ERROR),
    RECURSO_NAO_ENCONTRADO("RECURSO_NAO_ENCONTRADO", "erro.recurso-nao-encontrado", HttpStatus.NOT_FOUND),
    METODO_NAO_SUPORTADO("METODO_NAO_SUPORTADO", "erro.metodo-nao-suportado", HttpStatus.METHOD_NOT_ALLOWED),
    REQUISICAO_INVALIDA("REQUISICAO_INVALIDA", "erro.requisicao-invalida", HttpStatus.BAD_REQUEST),
    ERRO_NAO_TRATADO("ERRO_NAO_TRATADO", "erro.nao-tratado", HttpStatus.INTERNAL_SERVER_ERROR);

    private final String codigo;

    private final String chaveMensagem;

    private final HttpStatus httpStatus;

}
