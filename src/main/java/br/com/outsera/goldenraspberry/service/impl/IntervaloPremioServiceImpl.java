package br.com.outsera.goldenraspberry.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;
import br.com.outsera.goldenraspberry.repository.FilmeRepository;
import br.com.outsera.goldenraspberry.repository.projection.VitoriaProdutorProjection;
import br.com.outsera.goldenraspberry.service.IntervaloPremioService;
import br.com.outsera.goldenraspberry.service.application.CalculoIntervaloPremioServiceApplication;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class IntervaloPremioServiceImpl implements IntervaloPremioService {

    private final FilmeRepository filmeRepository;

    private final CalculoIntervaloPremioServiceApplication calculoIntervaloPremioServiceApplication;

    @Override
    public RetornoIntervaloPremioDTO adquirir() {

        List<VitoriaProdutorProjection> listaVitoriaProdutorProjection = this.filmeRepository.listarVitoriaProdutor();

        return this.calculoIntervaloPremioServiceApplication.calcular(listaVitoriaProdutorProjection);
    }

}
