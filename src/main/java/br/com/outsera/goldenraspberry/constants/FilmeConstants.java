package br.com.outsera.goldenraspberry.constants;

import java.util.List;

public final class FilmeConstants {

    private FilmeConstants() {

    }

    public static final char DELIMITADOR_ARQUIVO = ';';
    public static final String MARCA_ORDEM_BYTE = "﻿";

    public static final String COLUNA_ANO = "year";
    public static final String COLUNA_TITULO = "title";
    public static final String COLUNA_ESTUDIOS = "studios";
    public static final String COLUNA_PRODUTORES = "producers";
    public static final String COLUNA_VENCEDOR = "winner";
    public static final List<String> LISTA_COLUNA_CABECALHO = List.of(COLUNA_ANO, COLUNA_TITULO, COLUNA_ESTUDIOS, COLUNA_PRODUTORES,
            COLUNA_VENCEDOR);

    public static final String INDICADOR_VENCEDOR = "yes";

}
