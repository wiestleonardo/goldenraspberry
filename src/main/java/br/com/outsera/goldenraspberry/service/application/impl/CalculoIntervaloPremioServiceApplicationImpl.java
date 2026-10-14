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

    private static final Comparator<Long> CRITERIO_INTERVALO_MINIMO = Comparator.naturalOrder();
    private static final Comparator<Long> CRITERIO_INTERVALO_MAXIMO = Comparator.reverseOrder();

    // Percurso único: cada intervalo consecutivo é confrontado na hora com os extremos atuais, sem
    // lista intermediária nem passadas extras para mínimo, máximo, filtro e ordenação. As vitórias
    // chegam ordenadas por produtor e ano, então os intervalos nascem ordenados por produtor, ano
    // anterior e ano seguinte e as listas de empates já saem na ordem da resposta.
    @Override
    public RetornoIntervaloPremioDTO calcular(List<VitoriaProdutorProjection> listaVitoriaProdutorProjection) {

        List<IntervaloPremioDTO> listaIntervaloMinimo = new ArrayList<>();
        List<IntervaloPremioDTO> listaIntervaloMaximo = new ArrayList<>();
        VitoriaProdutorProjection vitoriaAnterior = null;

        for (VitoriaProdutorProjection vitoriaAtual : listaVitoriaProdutorProjection) {

            if (ObjectUtil.isNotNull(vitoriaAnterior) && ObjectUtil.isEquals(vitoriaAnterior.produtor(), vitoriaAtual.produtor())) {

                IntervaloPremioDTO intervaloPremioDTO = new IntervaloPremioDTO(vitoriaAtual.produtor(),
                        LongUtil.menos(vitoriaAtual.ano(), vitoriaAnterior.ano()),
                        vitoriaAnterior.ano(),
                        vitoriaAtual.ano());

                this.registrarExtremo(listaIntervaloMinimo, intervaloPremioDTO, CRITERIO_INTERVALO_MINIMO);
                this.registrarExtremo(listaIntervaloMaximo, intervaloPremioDTO, CRITERIO_INTERVALO_MAXIMO);
            }

            vitoriaAnterior = vitoriaAtual;
        }

        return new RetornoIntervaloPremioDTO(List.copyOf(listaIntervaloMinimo), List.copyOf(listaIntervaloMaximo));
    }

    // Intervalo que supera o extremo atual (segundo o critério) substitui os empates guardados;
    // intervalo igual entra como mais um empate; pior é ignorado
    private void registrarExtremo(List<IntervaloPremioDTO> listaExtremo, IntervaloPremioDTO intervaloPremioDTO,
            Comparator<Long> criterio) {

        int comparacao = listaExtremo.isEmpty() ? -1 : criterio.compare(intervaloPremioDTO.intervalo(), listaExtremo.getFirst().intervalo());

        if (comparacao < 0) {

            listaExtremo.clear();
        }

        if (comparacao <= 0) {

            listaExtremo.add(intervaloPremioDTO);
        }
    }

}
