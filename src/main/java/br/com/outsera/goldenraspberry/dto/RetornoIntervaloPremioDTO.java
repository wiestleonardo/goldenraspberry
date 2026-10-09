package br.com.outsera.goldenraspberry.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RetornoIntervaloPremioDTO(

        @JsonProperty("min")
        List<IntervaloPremioDTO> listaIntervaloMinimo,

        @JsonProperty("max")
        List<IntervaloPremioDTO> listaIntervaloMaximo) {

}
