package br.com.outsera.goldenraspberry.service.application;

import java.util.List;

public interface SeparacaoProdutorServiceApplication {

    /**
     * Separa o texto de produtores de um filme em nomes individuais (vírgula, "and" e ", and", com o
     * "and" como palavra inteira em qualquer caixa). Cada nome perde só os espaços das extremidades;
     * caixa e grafia dos nomes são preservadas e nomes repetidos no mesmo texto aparecem uma vez. Texto
     * vazio devolve lista vazia.
     */
    public abstract List<String> separar(String produtores);

}
