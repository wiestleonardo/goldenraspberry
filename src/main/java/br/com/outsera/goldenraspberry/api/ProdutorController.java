package br.com.outsera.goldenraspberry.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import br.com.outsera.goldenraspberry.constants.RotaConstants;
import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;

@RequestMapping(path = RotaConstants.Produtor.BASE, produces = MediaType.APPLICATION_JSON_VALUE)
public interface ProdutorController {

    @GetMapping(RotaConstants.Produtor.INTERVALOS_PREMIOS)
    public abstract ResponseEntity<RetornoIntervaloPremioDTO> adquirirIntervalosPremios();

}
