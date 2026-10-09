package br.com.outsera.goldenraspberry.repository.domain;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tb_filme")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SequenceGenerator(name = "gen_filme", sequenceName = "gen_filme", allocationSize = 1)
public class Filme implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @EqualsAndHashCode.Include
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

}
