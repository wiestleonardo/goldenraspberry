package br.com.outsera.goldenraspberry.repository;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import br.com.outsera.goldenraspberry.repository.domain.Filme;
import br.com.outsera.goldenraspberry.repository.projection.VitoriaProdutorProjection;

public interface FilmeRepository extends CrudRepository<Filme, Long> {

    /**
     * Uma linha por produtor de filme vencedor, ordenada por produtor e ano: as vitórias do mesmo
     * produtor ficam contíguas e em ordem cronológica, o que permite calcular os intervalos
     * consecutivos num único percurso.
     */
    @Query(nativeQuery = true, value = """
            SELECT
                produtorfilme.tx_produtor AS produtor,
                filme.nr_ano AS ano
            FROM
                tb_produtorfilme produtorfilme
            INNER JOIN tb_filme filme ON
                filme.id_filme = produtorfilme.cd_filme
            WHERE
                filme.fl_vencedor = TRUE
            ORDER BY
                produtorfilme.tx_produtor,
                filme.nr_ano
            """)
    public abstract List<VitoriaProdutorProjection> listarVitoriaProdutor();

}
