package br.com.outsera.goldenraspberry.util;

import java.util.Objects;

public final class ObjectUtil {

    private ObjectUtil() {

    }

    public static Boolean isNull(Object objeto) {

        return Objects.isNull(objeto);
    }

    public static Boolean isNotNull(Object objeto) {

        return Objects.nonNull(objeto);
    }

    public static Boolean isEquals(Object objeto1, Object objeto2) {

        return Objects.equals(objeto1, objeto2);
    }

}
