package br.com.outsera.goldenraspberry.repository.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

@Entity
@Table(name = "tb_filme")
@SequenceGenerator(name = "gen_filme", sequenceName = "gen_filme", allocationSize = 1)
public class Filme implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @Column(name = "id_filme", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen_filme")
    private Long idFilme;

    @Column(name = "nr_ano", nullable = false)
    private Long ano;

    @Column(name = "tx_titulo", nullable = false)
    private String titulo;

    @Column(name = "tx_estudios")
    private String estudios;

    @Column(name = "tx_produtores", nullable = false)
    private String produtores;

    @Column(name = "fl_vencedor", nullable = false)
    private Boolean vencedor;

    @OneToMany(mappedBy = "filme", cascade = CascadeType.PERSIST)
    private List<ProdutorFilme> listaProdutorFilme = new ArrayList<>();

    public Long getIdFilme() {

        return this.idFilme;
    }

    public void setIdFilme(Long idFilme) {

        this.idFilme = idFilme;
    }

    public Long getAno() {

        return this.ano;
    }

    public void setAno(Long ano) {

        this.ano = ano;
    }

    public String getTitulo() {

        return this.titulo;
    }

    public void setTitulo(String titulo) {

        this.titulo = titulo;
    }

    public String getEstudios() {

        return this.estudios;
    }

    public void setEstudios(String estudios) {

        this.estudios = estudios;
    }

    public String getProdutores() {

        return this.produtores;
    }

    public void setProdutores(String produtores) {

        this.produtores = produtores;
    }

    public Boolean getVencedor() {

        return this.vencedor;
    }

    public void setVencedor(Boolean vencedor) {

        this.vencedor = vencedor;
    }

    public List<ProdutorFilme> getListaProdutorFilme() {

        return this.listaProdutorFilme;
    }

    public void setListaProdutorFilme(List<ProdutorFilme> listaProdutorFilme) {

        this.listaProdutorFilme = listaProdutorFilme;
    }

    @Override
    public boolean equals(Object objeto) {

        if (this == objeto) {

            return true;
        }

        if (!(objeto instanceof Filme filme)) {

            return false;
        }

        return Objects.equals(this.idFilme, filme.idFilme);
    }

    @Override
    public int hashCode() {

        return Objects.hashCode(this.idFilme);
    }

}
