package br.com.outsera.goldenraspberry.service.facade;

public interface ImportacaoFilmeFacade {

    /**
     * Lê o arquivo de filmes (fora de transação) e grava os filmes válidos numa única transação.
     */
    public abstract void importar();

}
