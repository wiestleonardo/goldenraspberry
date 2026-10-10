package br.com.outsera.goldenraspberry.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

import br.com.outsera.goldenraspberry.service.facade.ImportacaoFilmeFacade;

/**
 * Importa o arquivo de filmes uma única vez, na inicialização. Roda depois de todos os singletons
 * criados e antes de o servidor HTTP aceitar conexões (ao contrário de um {@code ApplicationRunner},
 * que roda com o servidor já de pé), então nenhuma requisição enxerga o banco vazio. Uma exceção
 * aqui interrompe a subida.
 */
@Component
public class ImportacaoFilmeJob implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoFilmeJob.class);

    private final ImportacaoFilmeFacade importacaoFilmeFacade;

    public ImportacaoFilmeJob(ImportacaoFilmeFacade importacaoFilmeFacade) {

        this.importacaoFilmeFacade = importacaoFilmeFacade;
    }

    @Override
    public void afterSingletonsInstantiated() {

        log.info("Iniciando a importação do arquivo de filmes");

        this.importacaoFilmeFacade.importar();
    }

}
