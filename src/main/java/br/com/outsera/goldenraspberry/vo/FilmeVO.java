package br.com.outsera.goldenraspberry.vo;

import java.util.List;

import lombok.Builder;

@Builder
public record FilmeVO(

        Long ano,

        String titulo,

        String estudios,

        String produtores,

        Boolean vencedor,

        List<String> listaProdutor) {

}
