package de.dew.backend.web;

import de.dew.backend.domain.Delivery;
import de.dew.backend.domain.DeliveryPdf;
import de.dew.backend.service.DeliveryService;
import de.dew.backend.web.dto.DeliveryDetailDto;
import de.dew.parser.PackingListParseException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/deliveries/{id}")
public class DeliveryFileController {

    private final DeliveryService service;

    public DeliveryFileController(DeliveryService service) {
        this.service = service;
    }

    @PostMapping("/pdfs")
    public DeliveryDetailDto addPdfs(@PathVariable("id") long id,
            @RequestParam("files") List<MultipartFile> files) {
        return service.addPdfs(id, files);
    }

    @DeleteMapping("/pdfs/{pdfId}")
    public ResponseEntity<Void> deletePdf(@PathVariable("id") long id, @PathVariable("pdfId") long pdfId) {
        service.deletePdf(id, pdfId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/pdfs/{pdfId}/content")
    public ResponseEntity<Resource> pdfContent(@PathVariable("id") long id, @PathVariable("pdfId") long pdfId)
            throws IOException {
        DeliveryPdf pdf = service.pdf(id, pdfId);
        return fileResponse(service.storage().resolve(pdf.storedPath()), MediaType.APPLICATION_PDF,
                ContentDisposition.inline().filename(pdf.originalFilename(),
                        java.nio.charset.StandardCharsets.UTF_8).build());
    }

    @PutMapping("/excel")
    public DeliveryDetailDto replaceExcel(@PathVariable("id") long id,
            @RequestParam("file") MultipartFile file) throws PackingListParseException {
        return service.replaceExcel(id, file);
    }

    @GetMapping("/excel")
    public ResponseEntity<Resource> downloadExcel(@PathVariable("id") long id) throws IOException {
        Delivery delivery = service.deliveryWithExcel(id);
        String filename = delivery.excelOriginalFilename() == null
                ? "packing-list.xlsx"
                : delivery.excelOriginalFilename();
        return fileResponse(service.storage().resolve(delivery.excelPath()),
                MediaType.APPLICATION_OCTET_STREAM,
                ContentDisposition.attachment()
                        .filename(filename, java.nio.charset.StandardCharsets.UTF_8).build());
    }

    private static ResponseEntity<Resource> fileResponse(Path path, MediaType type,
            ContentDisposition disposition) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new de.dew.backend.error.NotFoundException(
                    "The stored file for this record is missing on disk");
        }
        return ResponseEntity.ok()
                .contentType(type)
                .contentLength(Files.size(path))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new PathResource(path));
    }
}
