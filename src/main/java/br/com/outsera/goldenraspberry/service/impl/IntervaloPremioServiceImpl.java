package br.com.outsera.goldenraspberry.service.impl;

import java.util.List;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.constants.CacheConstants;
import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;
import br.com.outsera.goldenraspberry.repository.FilmeRepository;
import br.com.outsera.goldenraspberry.repository.projection.VitoriaProdutorProjection;
import br.com.outsera.goldenraspberry.service.IntervaloPremioService;
import br.com.outsera.goldenraspberry.service.application.CalculoIntervaloPremioServiceApplication;

@Service
public class IntervaloPremioServiceImpl implements IntervaloPremioService {

    private final FilmeRepository filmeRepository;

    private final CalculoIntervaloPremioServiceApplication calculoIntervaloPremioServiceApplication;

    public IntervaloPremioServiceImpl(FilmeRepository filmeRepository,
            CalculoIntervaloPremioServiceApplication calculoIntervaloPremioServiceApplication) {

        this.filmeRepository = filmeRepository;
        this.calculoIntervaloPremioServiceApplication = calculoIntervaloPremioServiceApplication;
    }

    // Os filmes só são gravados no startup, antes de a porta HTTP abrir, e nunca mudam depois: o
    // resultado é calculado na primeira requisição (uma única carga, mesmo com chamadas simultâneas) e
    // as seguintes não vão ao banco. Cada réplica importa o mesmo arquivo, então os caches não divergem.
    @Override
    @Cacheable(cacheNames = CacheConstants.INTERVALO_PREMIO, sync = true)
    public RetornoIntervaloPremioDTO adquirir() {

        List<VitoriaProdutorProjection> listaVitoriaProdutorProjection = this.filmeRepository.listarVitoriaProdutor();

        return this.calculoIntervaloPremioServiceApplication.calcular(listaVitoriaProdutorProjection);
    }

}
