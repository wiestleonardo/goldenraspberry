CREATE SEQUENCE gen_filme START WITH 1 INCREMENT BY 1;

CREATE SEQUENCE gen_produtorfilme START WITH 1 INCREMENT BY 1;

CREATE TABLE tb_filme (
    id_filme BIGINT NOT NULL,
    nr_ano BIGINT NOT NULL,
    tx_titulo VARCHAR NOT NULL,
    tx_estudios VARCHAR,
    tx_produtores VARCHAR NOT NULL,
    fl_vencedor BOOLEAN NOT NULL,
    CONSTRAINT filme_pk PRIMARY KEY (id_filme)
);

CREATE TABLE tb_produtorfilme (
    id_produtorfilme BIGINT NOT NULL,
    cd_filme BIGINT NOT NULL,
    tx_produtor VARCHAR NOT NULL,
    CONSTRAINT produtorfilme_pk PRIMARY KEY (id_produtorfilme),
    CONSTRAINT produtorfilme_filme_fk FOREIGN KEY (cd_filme) REFERENCES tb_filme (id_filme)
);
