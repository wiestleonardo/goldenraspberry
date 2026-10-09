package br.com.outsera.goldenraspberry.job;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import br.com.outsera.goldenraspberry.service.facade.ImportacaoFilmeFacade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Importa o arquivo de filmes uma única vez, na inicialização. Roda depois de todos os singletons
 * criados e antes de o servidor HTTP aceitar conexões (ao contrário de um {@code ApplicationRunner},
 * que roda com o servidor já de pé), então nenhuma requisição enxerga o banco vazio. Uma exceção
 * aqui interrompe a subida.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportacaoFilmeJob implements SmartInitializingSingleton {

    private final ImportacaoFilmeFacade importacaoFilmeFacade;

    @Override
    public void afterSingletonsInstantiated() {

        log.info("Iniciando a importação do arquivo de filmes");

        this.importacaoFilmeFacade.importar();
    }

}
