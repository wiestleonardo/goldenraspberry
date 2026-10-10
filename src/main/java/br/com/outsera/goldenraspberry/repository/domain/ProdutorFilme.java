package br.com.outsera.goldenraspberry.repository.domain;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_produtorfilme")
@SequenceGenerator(name = "gen_produtorfilme", sequenceName = "gen_produtorfilme", allocationSize = 1)
public class ProdutorFilme implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id_produtorfilme", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen_produtorfilme")
    private Long idProdutorFilme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cd_filme", nullable = false)
    private Filme filme;

    @Column(name = "tx_produtor", nullable = false)
    private String produtor;

    public Long getIdProdutorFilme() {

        return this.idProdutorFilme;
    }

    public void setIdProdutorFilme(Long idProdutorFilme) {

        this.idProdutorFilme = idProdutorFilme;
    }

    public Filme getFilme() {

        return this.filme;
    }

    public void setFilme(Filme filme) {

        this.filme = filme;
    }

    public String getProdutor() {

        return this.produtor;
    }

    public void setProdutor(String produtor) {

        this.produtor = produtor;
    }

    @Override
    public boolean equals(Object objeto) {

        if (this == objeto) {

            return true;
        }

        if (!(objeto instanceof ProdutorFilme produtorFilme)) {

            return false;
        }

        return Objects.equals(this.idProdutorFilme, produtorFilme.idProdutorFilme);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(this.idProdutorFilme);
    }

}
