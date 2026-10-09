package br.com.outsera.goldenraspberry.constants;

import java.util.List;

public final class FilmeConstants {

    private FilmeConstants() {

    }

    public static final char DELIMITADOR_ARQUIVO = ';';
    public static final String MARCA_ORDEM_BYTE = String.valueOf((char) 0xFEFF);
    public static final Long NUMERO_LINHA_CABECALHO = 1L;

    public static final String COLUNA_ANO = "year";
    public static final String COLUNA_TITULO = "title";
    public static final String COLUNA_ESTUDIOS = "studios";
    public static final String COLUNA_PRODUTORES = "producers";
    public static final String COLUNA_VENCEDOR = "winner";
    public static final List<String> LISTA_COLUNA_CABECALHO = List.of(COLUNA_ANO, COLUNA_TITULO, COLUNA_ESTUDIOS, COLUNA_PRODUTORES,
            COLUNA_VENCEDOR);

    public static final int INDICE_COLUNA_ANO = LISTA_COLUNA_CABECALHO.indexOf(COLUNA_ANO);
    public static final int INDICE_COLUNA_TITULO = LISTA_COLUNA_CABECALHO.indexOf(COLUNA_TITULO);
    public static final int INDICE_COLUNA_ESTUDIOS = LISTA_COLUNA_CABECALHO.indexOf(COLUNA_ESTUDIOS);
    public static final int INDICE_COLUNA_PRODUTORES = LISTA_COLUNA_CABECALHO.indexOf(COLUNA_PRODUTORES);
    public static final int INDICE_COLUNA_VENCEDOR = LISTA_COLUNA_CABECALHO.indexOf(COLUNA_VENCEDOR);

    public static final String INDICADOR_VENCEDOR = "yes";

}
