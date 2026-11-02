package de.dew.backend.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import de.dew.backend.BackendTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

class DeliveryPdfApiTest extends BackendTest {

    @Test
    void uploadsSeveralPdfsInOneRequest() throws Exception {
        long id = createDelivery();

        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(pdf("delivery note.pdf", "one"))
                        .file(pdf("bill of lading.pdf", "two"))
                        .file(pdf("customs form.pdf", "three")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfs.length()").value(3))
                .andExpect(jsonPath("$.pdfs[0].original_filename").value("delivery note.pdf"))
                .andExpect(jsonPath("$.pdfs[0].sort_order").value(0))
                .andExpect(jsonPath("$.pdfs[1].original_filename").value("bill of lading.pdf"))
                .andExpect(jsonPath("$.pdfs[1].sort_order").value(1))
                .andExpect(jsonPath("$.pdfs[2].original_filename").value("customs form.pdf"))
                .andExpect(jsonPath("$.pdfs[2].sort_order").value(2))
                .andExpect(jsonPath("$.pdfs[0].size_bytes").value(3))
                .andExpect(jsonPath("$.pdfs[0].uploaded_at").exists());

        assertThat(storedFiles()).hasSize(3);
        for (Path file : storedFiles()) {
            assertThat(file.getFileName().toString())
                    .doesNotContain(" ")
                    .doesNotContain("delivery")
                    .endsWith(".pdf");
        }
    }

    @Test
    void pdfsAddedLaterAppendToTheList() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id).file(pdf("first.pdf", "1")))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id).file(pdf("second.pdf", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfs.length()").value(2))
                .andExpect(jsonPath("$.pdfs[0].sort_order").value(0))
                .andExpect(jsonPath("$.pdfs[1].original_filename").value("second.pdf"))
                .andExpect(jsonPath("$.pdfs[1].sort_order").value(1));
    }

    @Test
    void keepsTheOriginalNameForDisplayWhileTheDiskNameIsGenerated() throws Exception {
        long id = createDelivery();
        String nbsp = " ";
        String original = "QX20260115" + nbsp + "delivery" + nbsp + "note.pdf";

        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id).file(pdf(original, "x")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pdfs[0].original_filename").value(original));

        String storedPath = jdbc.queryForObject(
                "SELECT stored_path FROM delivery_pdf WHERE delivery_id = ?", String.class, id);
        assertThat(storedPath).doesNotContain(nbsp).doesNotContain("QX20260115");
    }

    @Test
    void removesOnePdfAndItsFileLeavingTheOthers() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(pdf("a.pdf", "aaa"))
                        .file(pdf("b.pdf", "bbb"))
                        .file(pdf("c.pdf", "ccc")))
                .andExpect(status().isOk());

        JsonNode pdfs = getJson("/api/deliveries/{id}", id).get("pdfs");
        long middleId = pdfs.get(1).get("id").asLong();
        String middlePath = jdbc.queryForObject(
                "SELECT stored_path FROM delivery_pdf WHERE id = ?", String.class, middleId);

        mvc.perform(delete("/api/deliveries/{id}/pdfs/{pdfId}", id, middleId))
                .andExpect(status().isNoContent());

        assertThat(storedFiles()).hasSize(2);
        assertThat(storage().resolve(middlePath)).doesNotExist();
        JsonNode remaining = getJson("/api/deliveries/{id}", id).get("pdfs");
        assertThat(remaining).hasSize(2);
        assertThat(List.of(remaining.get(0).get("original_filename").asText(),
                remaining.get(1).get("original_filename").asText()))
                .containsExactly("a.pdf", "c.pdf");
    }

    @Test
    void aPdfBelongingToAnotherDeliveryIsNotFound() throws Exception {
        long mine = createDelivery();
        long other = createDelivery(bodyWith("customer_company", "Nordwind GmbH"));
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", other).file(pdf("theirs.pdf", "x")))
                .andExpect(status().isOk());
        long theirPdf = getJson("/api/deliveries/{id}", other).get("pdfs").get(0).get("id").asLong();

        mvc.perform(delete("/api/deliveries/{id}/pdfs/{pdfId}", mine, theirPdf))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/deliveries/{id}/pdfs/{pdfId}/content", mine, theirPdf))
                .andExpect(status().isNotFound());

        assertThat(storedFiles()).hasSize(1);
        assertThat(getJson("/api/deliveries/{id}", other).get("pdfs")).hasSize(1);
    }

    @Test
    void servesPdfContentInlineAsApplicationPdf() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id).file(pdf("note.pdf", "%PDF-1.4 body")))
                .andExpect(status().isOk());
        long pdfId = getJson("/api/deliveries/{id}", id).get("pdfs").get(0).get("id").asLong();

        byte[] body = mvc.perform(get("/api/deliveries/{id}/pdfs/{pdfId}/content", id, pdfId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")))
                .andExpect(header().string("Content-Length", "13"))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(body, StandardCharsets.UTF_8)).isEqualTo("%PDF-1.4 body");
    }

    @Test
    void deletingADeliveryRemovesEveryPdfRowAndEveryFile() throws Exception {
        long id = createDelivery();
        long survivor = createDelivery(bodyWith("customer_company", "Nordwind GmbH"));
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(pdf("a.pdf", "aa"))
                        .file(pdf("b.pdf", "bb")))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", survivor).file(pdf("keep.pdf", "kk")))
                .andExpect(status().isOk());
        assertThat(storedFiles()).hasSize(3);

        mvc.perform(delete("/api/deliveries/{id}", id)).andExpect(status().isNoContent());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_pdf WHERE delivery_id = ?",
                Integer.class, id)).isZero();
        assertThat(storedFiles()).hasSize(1);
        String kept = jdbc.queryForObject("SELECT stored_path FROM delivery_pdf WHERE delivery_id = ?",
                String.class, survivor);
        assertThat(storage().resolve(kept)).isRegularFile();
    }

    @Test
    void uploadingToAnUnknownDeliveryIsFourOhFourAndStoresNothing() throws Exception {
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", 987654).file(pdf("x.pdf", "x")))
                .andExpect(status().isNotFound());
        assertThat(storedFiles()).isEmpty();
    }

    @Test
    void aMissingFilesPartIsAValidationError() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.files").exists());
    }

    @Test
    void anEmptyFileIsRejectedWithoutLeavingTheEarlierFilesBehind() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(pdf("good.pdf", "content"))
                        .file(pdf("empty.pdf", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"));

        assertThat(storedFiles()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM delivery_pdf", Integer.class)).isZero();
    }

    @Test
    void listRowReportsThePdfCount() throws Exception {
        long id = createDelivery();
        mvc.perform(multipart("/api/deliveries/{id}/pdfs", id)
                        .file(pdf("a.pdf", "a"))
                        .file(pdf("b.pdf", "b")))
                .andExpect(status().isOk());

        assertThat(getJson("/api/deliveries").get(0).get("pdf_count").asInt()).isEqualTo(2);
    }

    private static MockMultipartFile pdf(String filename, String content) {
        return new MockMultipartFile("files", filename, MediaType.APPLICATION_PDF_VALUE,
                content.getBytes(StandardCharsets.UTF_8));
    }
}
