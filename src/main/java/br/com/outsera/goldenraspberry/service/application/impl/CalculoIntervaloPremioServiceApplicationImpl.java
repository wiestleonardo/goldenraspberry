package br.com.outsera.goldenraspberry.service.application.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.dto.IntervaloPremioDTO;
import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;
import br.com.outsera.goldenraspberry.repository.projection.VitoriaProdutorProjection;
import br.com.outsera.goldenraspberry.service.application.CalculoIntervaloPremioServiceApplication;
import br.com.outsera.goldenraspberry.util.LongUtil;
import br.com.outsera.goldenraspberry.util.ObjectUtil;

@Service
public class CalculoIntervaloPremioServiceApplicationImpl implements CalculoIntervaloPremioServiceApplication {

    private static final Comparator<IntervaloPremioDTO> ORDENACAO_INTERVALO_PREMIO = Comparator.comparing(IntervaloPremioDTO::produtor)
            .thenComparing(IntervaloPremioDTO::anoPremioAnterior)
            .thenComparing(IntervaloPremioDTO::anoPremioSeguinte);

    @Override
    public RetornoIntervaloPremioDTO calcular(List<VitoriaProdutorProjection> listaVitoriaProdutorProjection) {

        List<IntervaloPremioDTO> listaIntervaloPremioDTO = this.montarIntervalosConsecutivos(listaVitoriaProdutorProjection);

        if (listaIntervaloPremioDTO.isEmpty()) {

            return new RetornoIntervaloPremioDTO(List.of(), List.of());
        }

        Long intervaloMinimo = listaIntervaloPremioDTO.stream()
                .map(IntervaloPremioDTO::intervalo)
                .min(Comparator.naturalOrder())
                .orElseThrow();

        Long intervaloMaximo = listaIntervaloPremioDTO.stream()
                .map(IntervaloPremioDTO::intervalo)
                .max(Comparator.naturalOrder())
                .orElseThrow();

        return new RetornoIntervaloPremioDTO(this.filtrarPorIntervalo(listaIntervaloPremioDTO, intervaloMinimo),
                this.filtrarPorIntervalo(listaIntervaloPremioDTO, intervaloMaximo));
    }

    private List<IntervaloPremioDTO> montarIntervalosConsecutivos(List<VitoriaProdutorProjection> listaVitoriaProdutorProjection) {

        List<IntervaloPremioDTO> listaIntervaloPremioDTO = new ArrayList<>();
        VitoriaProdutorProjection vitoriaAnterior = null;

        for (VitoriaProdutorProjection vitoriaAtual : listaVitoriaProdutorProjection) {

            if (ObjectUtil.isNotNull(vitoriaAnterior) && ObjectUtil.isEquals(vitoriaAnterior.produtor(), vitoriaAtual.produtor())) {

                listaIntervaloPremioDTO.add(IntervaloPremioDTO.builder()
                        .produtor(vitoriaAtual.produtor())
                        .intervalo(LongUtil.menos(vitoriaAtual.ano(), vitoriaAnterior.ano()))
                        .anoPremioAnterior(vitoriaAnterior.ano())
                        .anoPremioSeguinte(vitoriaAtual.ano())
                        .build());
            }

            vitoriaAnterior = vitoriaAtual;
        }

        return listaIntervaloPremioDTO;
    }

    private List<IntervaloPremioDTO> filtrarPorIntervalo(List<IntervaloPremioDTO> listaIntervaloPremioDTO, Long intervalo) {

        return listaIntervaloPremioDTO.stream()
                .filter(intervaloPremioDTO -> LongUtil.igual(intervaloPremioDTO.intervalo(), intervalo))
                .sorted(ORDENACAO_INTERVALO_PREMIO)
                .toList();
    }

}
