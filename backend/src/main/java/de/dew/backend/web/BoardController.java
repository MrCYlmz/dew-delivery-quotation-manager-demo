package de.dew.backend.web;

import de.dew.backend.service.DeliveryService;
import de.dew.backend.web.dto.BoardCardDto;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final DeliveryService service;

    public BoardController(DeliveryService service) {
        this.service = service;
    }

    @GetMapping
    public List<BoardCardDto> board() {
        return service.board();
    }
}
