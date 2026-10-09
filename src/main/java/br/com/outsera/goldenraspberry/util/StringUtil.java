package br.com.outsera.goldenraspberry.util;

public final class StringUtil {

    private StringUtil() {

    }

    public static final String STRING_VAZIA = "";

    public static Boolean isNullOrEmpty(String valor) {

        return ObjectUtil.isNull(valor) || valor.isBlank();
    }

    public static Boolean notEmpty(String valor) {

        return ObjectUtil.isNotNull(valor) && !valor.isBlank();
    }

}
