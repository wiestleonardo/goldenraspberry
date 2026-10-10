package br.com.outsera.goldenraspberry.service.facade.impl;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.service.LeituraArquivoFilmeService;
import br.com.outsera.goldenraspberry.service.facade.ImportacaoFilmeFacade;
import br.com.outsera.goldenraspberry.service.transactional.FilmeServiceTransactional;
import br.com.outsera.goldenraspberry.vo.FilmeVO;

@Service
public class ImportacaoFilmeFacadeImpl implements ImportacaoFilmeFacade {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoFilmeFacadeImpl.class);

    private final LeituraArquivoFilmeService leituraArquivoFilmeService;

    private final FilmeServiceTransactional filmeServiceTransactional;

    public ImportacaoFilmeFacadeImpl(LeituraArquivoFilmeService leituraArquivoFilmeService,
            FilmeServiceTransactional filmeServiceTransactional) {

        this.leituraArquivoFilmeService = leituraArquivoFilmeService;
        this.filmeServiceTransactional = filmeServiceTransactional;
    }

    @Override
    public void importar() {

        List<FilmeVO> listaFilmeVO = this.leituraArquivoFilmeService.ler();

        this.filmeServiceTransactional.salvar(listaFilmeVO);

        log.info("Importação do arquivo de filmes concluída: {} filmes gravados", listaFilmeVO.size());
    }

}
