package br.com.outsera.goldenraspberry.service.transactional;

import java.util.List;

import br.com.outsera.goldenraspberry.vo.FilmeVO;

public interface FilmeServiceTransactional {

    public abstract void salvar(List<FilmeVO> listaFilmeVO);

}
