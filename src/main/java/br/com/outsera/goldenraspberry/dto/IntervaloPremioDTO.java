package br.com.outsera.goldenraspberry.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record IntervaloPremioDTO(

        @JsonProperty("producer")
        String produtor,

        @JsonProperty("interval")
        Long intervalo,

        @JsonProperty("previousWin")
        Long anoPremioAnterior,

        @JsonProperty("followingWin")
        Long anoPremioSeguinte) {

}
