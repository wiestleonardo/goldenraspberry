package br.com.outsera.goldenraspberry.api.impl;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import br.com.outsera.goldenraspberry.api.ProdutorController;
import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;
import br.com.outsera.goldenraspberry.service.IntervaloPremioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
public class ProdutorControllerImpl implements ProdutorController {

    private final IntervaloPremioService intervaloPremioService;

    @Override
    public ResponseEntity<RetornoIntervaloPremioDTO> adquirirIntervalosPremios() {

        log.info("Adquirindo os intervalos de prêmios dos produtores");

        return ResponseEntity.ok(this.intervaloPremioService.adquirir());
    }

}
