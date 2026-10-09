package br.com.outsera.goldenraspberry.repository.domain;

import java.io.Serializable;

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
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "tb_produtorfilme")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@SequenceGenerator(name = "gen_produtorfilme", sequenceName = "gen_produtorfilme", allocationSize = 1)
public class ProdutorFilme implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @EqualsAndHashCode.Include
    @Column(name = "id_produtorfilme", nullable = false)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "gen_produtorfilme")
    private Long idProdutorFilme;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cd_filme", nullable = false)
    private Filme filme;

    @Column(name = "tx_produtor", nullable = false)
    private String produtor;

}
