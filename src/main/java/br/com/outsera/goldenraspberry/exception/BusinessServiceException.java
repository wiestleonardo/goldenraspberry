package br.com.outsera.goldenraspberry.exception;

import java.util.Arrays;
import java.util.List;

import br.com.outsera.goldenraspberry.enumerator.ErroEnum;
import lombok.Getter;

@Getter
public class BusinessServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErroEnum erroEnum;

    private final List<String> listaParametro;

    public BusinessServiceException(ErroEnum erroEnum, Object... parametros) {

        this(erroEnum, null, parametros);
    }

    public BusinessServiceException(ErroEnum erroEnum, Throwable causa, Object... parametros) {

        super(erroEnum.getCodigo() + " " + Arrays.toString(parametros), causa);

        this.erroEnum = erroEnum;
        this.listaParametro = Arrays.stream(parametros).map(String::valueOf).toList();
    }

}
