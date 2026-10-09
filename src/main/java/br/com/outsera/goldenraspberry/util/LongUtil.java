package br.com.outsera.goldenraspberry.util;

import java.util.Objects;

public final class LongUtil {

    private LongUtil() {

    }

    public static final Long ZERO = 0L;
    public static final Long UM = 1L;

    public static Long toLong(String valor) {

        if (StringUtil.isNullOrEmpty(valor)) {

            return null;
        }

        try {

            return Long.valueOf(valor.strip());

        } catch (NumberFormatException numberFormatException) {

            return null;
        }
    }

    public static Boolean igual(Long valor1, Long valor2) {

        return Objects.equals(valor1, valor2);
    }

    public static Boolean isNullOrZero(Long valor) {

        return ObjectUtil.isNull(valor) || ZERO.equals(valor);
    }

    public static Boolean menor(Long valor1, Long valor2) {

        return ObjectUtil.isNotNull(valor1) && ObjectUtil.isNotNull(valor2) && valor1 < valor2;
    }

    public static Long soma(Long parcela1, Long parcela2) {

        return Math.addExact(parcela1, parcela2);
    }

    public static Long menos(Long minuendo, Long subtraendo) {

        return Math.subtractExact(minuendo, subtraendo);
    }

}
