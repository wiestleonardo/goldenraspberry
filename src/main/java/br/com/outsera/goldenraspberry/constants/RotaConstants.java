package br.com.outsera.goldenraspberry.constants;

public final class RotaConstants {

    private RotaConstants() {

    }

    public static final String CONTEXTO = "/api";

    public static final class Produtor {

        private Produtor() {

        }

        public static final String BASE = CONTEXTO + "/produtores";
        public static final String INTERVALOS_PREMIOS = "/intervalosPremios";
    }

}
