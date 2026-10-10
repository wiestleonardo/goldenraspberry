package br.com.outsera.goldenraspberry.vo;

import java.util.List;

public record FilmeVO(

        Long ano,

        String titulo,

        String estudios,

        String produtores,

        Boolean vencedor,

        List<String> listaProdutor) {

}
