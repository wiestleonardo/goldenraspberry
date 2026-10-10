package br.com.outsera.goldenraspberry.api.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import br.com.outsera.goldenraspberry.api.ProdutorController;
import br.com.outsera.goldenraspberry.dto.RetornoIntervaloPremioDTO;
import br.com.outsera.goldenraspberry.service.IntervaloPremioService;

@RestController
public class ProdutorControllerImpl implements ProdutorController {

	private static final Logger log = LoggerFactory.getLogger(ProdutorControllerImpl.class);

	private final IntervaloPremioService intervaloPremioService;

	public ProdutorControllerImpl(IntervaloPremioService intervaloPremioService) {

		this.intervaloPremioService = intervaloPremioService;
	}

	@Override
	public ResponseEntity<RetornoIntervaloPremioDTO> adquirirIntervalosPremios() {

		log.info("Adquirindo os intervalos de prêmios dos produtores");

		return ResponseEntity.ok(this.intervaloPremioService.adquirir());
	}

}
