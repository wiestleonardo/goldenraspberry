package br.com.outsera.goldenraspberry.service;

import java.util.List;

import br.com.outsera.goldenraspberry.vo.FilmeVO;

public interface LeituraArquivoFilmeService {

    /**
     * Lê o arquivo configurado em {@code filme.arquivo-csv}. Linha inválida é descartada com log de
     * aviso (número da linha e motivo); arquivo inexistente, ilegível ou com cabeçalho incompatível
     * lança {@code BusinessServiceException}.
     */
    public abstract List<FilmeVO> ler();

}
