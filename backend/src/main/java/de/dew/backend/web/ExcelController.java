package de.dew.backend.web;

import de.dew.backend.service.DeliveryService;
import de.dew.backend.web.dto.ParseResultDto;
import de.dew.parser.PackingListParseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class ExcelController {

    private final DeliveryService service;

    public ExcelController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping("/api/excel/parse")
    public ParseResultDto parse(@RequestParam("file") MultipartFile file)
            throws PackingListParseException {
        return service.parseOnly(file);
    }
}
