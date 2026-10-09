package br.com.outsera.goldenraspberry.service.facade.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import br.com.outsera.goldenraspberry.service.LeituraArquivoFilmeService;
import br.com.outsera.goldenraspberry.service.facade.ImportacaoFilmeFacade;
import br.com.outsera.goldenraspberry.service.transactional.FilmeServiceTransactional;
import br.com.outsera.goldenraspberry.vo.FilmeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImportacaoFilmeFacadeImpl implements ImportacaoFilmeFacade {

    private final LeituraArquivoFilmeService leituraArquivoFilmeService;

    private final FilmeServiceTransactional filmeServiceTransactional;

    @Override
    public void importar() {

        List<FilmeVO> listaFilmeVO = this.leituraArquivoFilmeService.ler();

        this.filmeServiceTransactional.salvar(listaFilmeVO);

        log.info("Importação do arquivo de filmes concluída: {} filmes gravados", listaFilmeVO.size());
    }

}
