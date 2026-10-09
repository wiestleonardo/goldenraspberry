package br.com.outsera.goldenraspberry.service.application;

import java.util.List;

import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;
import br.com.outsera.goldenraspberry.repository.projection.VitoriaProdutorProjection;

public interface CalculoIntervaloPremioServiceApplication {

    /**
     * Espera as vitórias ordenadas por produtor e ano. Só vitórias consecutivas do mesmo produtor
     * formam intervalo; devolve todos os empates de menor e de maior intervalo, ordenados por
     * produtor, ano anterior e ano seguinte. Sem intervalo, as duas listas vêm vazias.
     */
    public abstract RetornoIntervaloPremioDTO calcular(List<VitoriaProdutorProjection> listaVitoriaProdutorProjection);

}
