package br.com.outsera.goldenraspberry.exception;

import java.util.Arrays;

import br.com.outsera.goldenraspberry.enumerator.ErroEnum;

public class BusinessServiceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErroEnum erroEnum;

    public BusinessServiceException(ErroEnum erroEnum, Object... parametros) {

        this(erroEnum, null, parametros);
    }

    public BusinessServiceException(ErroEnum erroEnum, Throwable causa, Object... parametros) {

        super(erroEnum.getCodigo() + " " + Arrays.toString(parametros), causa);

        this.erroEnum = erroEnum;
    }

    public ErroEnum getErroEnum() {

        return this.erroEnum;
    }

}
