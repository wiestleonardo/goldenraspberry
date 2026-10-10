package br.com.outsera.goldenraspberry.service.transactional.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.outsera.goldenraspberry.repository.FilmeRepository;
import br.com.outsera.goldenraspberry.repository.domain.Filme;
import br.com.outsera.goldenraspberry.repository.domain.ProdutorFilme;
import br.com.outsera.goldenraspberry.service.transactional.FilmeServiceTransactional;
import br.com.outsera.goldenraspberry.vo.FilmeVO;

@Service
public class FilmeServiceTransactionalImpl implements FilmeServiceTransactional {

    private final FilmeRepository filmeRepository;

    public FilmeServiceTransactionalImpl(FilmeRepository filmeRepository) {

        this.filmeRepository = filmeRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void salvar(List<FilmeVO> listaFilmeVO) {

        List<Filme> listaFilme = listaFilmeVO.stream()
                .map(this::montarFilme)
                .toList();

        this.filmeRepository.saveAll(listaFilme);
    }

    private Filme montarFilme(FilmeVO filmeVO) {

        Filme filme = new Filme();
        filme.setAno(filmeVO.ano());
        filme.setTitulo(filmeVO.titulo());
        filme.setEstudios(filmeVO.estudios());
        filme.setProdutores(filmeVO.produtores());
        filme.setVencedor(filmeVO.vencedor());
        filme.setListaProdutorFilme(filmeVO.listaProdutor().stream()
                .map(produtor -> this.montarProdutorFilme(filme, produtor))
                .collect(Collectors.toCollection(ArrayList::new)));

        return filme;
    }

    private ProdutorFilme montarProdutorFilme(Filme filme, String produtor) {

        ProdutorFilme produtorFilme = new ProdutorFilme();
        produtorFilme.setFilme(filme);
        produtorFilme.setProdutor(produtor);

        return produtorFilme;
    }

}
