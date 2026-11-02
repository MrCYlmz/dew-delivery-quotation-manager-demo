package de.dew.backend.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.dew.backend.config.DewProperties;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileStorageServiceTest {

    @TempDir
    Path tmp;

    private FileStorageService storage() {
        return new FileStorageService(new DewProperties(tmp.resolve("uploads").toString(), false));
    }

    @Test
    void createsTheStorageDirectoryIfMissing() {
        FileStorageService storage = storage();
        assertThat(storage.root()).isDirectory();
    }

    @Test
    void storedNameIsGeneratedAndKeepsNothingOfTheOriginal() throws IOException {
        FileStorageService storage = storage();
        String nbsp = " ";
        String original = "QX20260115" + nbsp + "INV" + nbsp + "&" + nbsp + "PAC.xlsx";

        String stored = storage.store(stream("workbook"), original);

        assertThat(stored)
                .doesNotContain(nbsp)
                .doesNotContain(" ")
                .doesNotContain("QX20260115")
                .doesNotContain("&")
                .endsWith(".xlsx");
        assertThat(storage.resolve(stored)).isRegularFile()
                .hasContent("workbook");
        assertThat(storage.resolve(stored).getParent()).isEqualTo(storage.root());
    }

    @Test
    void twoUploadsSharingOneNameBecomeTwoDistinctFiles() throws IOException {
        FileStorageService storage = storage();

        String first = storage.store(stream("one"), "delivery note.pdf");
        String second = storage.store(stream("two"), "delivery note.pdf");

        assertThat(first).isNotEqualTo(second);
        assertThat(storage.resolve(first)).hasContent("one");
        assertThat(storage.resolve(second)).hasContent("two");
        assertThat(Files.list(storage.root()).count()).isEqualTo(2);
    }

    @Test
    void aTraversingNameCannotReachOutsideTheStorageRoot() throws IOException {
        FileStorageService storage = storage();

        String stored = storage.store(stream("x"), "../../../../etc/passwd");

        assertThat(stored).doesNotContain("..").doesNotContain("/").doesNotContain("passwd");
        assertThat(storage.resolve(stored).startsWith(storage.root())).isTrue();
    }

    @Test
    void onlyWhitelistedExtensionsSurvive() {
        assertThat(FileStorageService.extensionOf("a.pdf")).isEqualTo("pdf");
        assertThat(FileStorageService.extensionOf("a.PDF")).isEqualTo("pdf");
        assertThat(FileStorageService.extensionOf("a.xlsx")).isEqualTo("xlsx");
        assertThat(FileStorageService.extensionOf("a.xls")).isEqualTo("xls");
        assertThat(FileStorageService.extensionOf("a.exe")).isEqualTo("bin");
        assertThat(FileStorageService.extensionOf("no-extension")).isEqualTo("bin");
        assertThat(FileStorageService.extensionOf("trailing.")).isEqualTo("bin");
        assertThat(FileStorageService.extensionOf(null)).isEqualTo("bin");
        assertThat(FileStorageService.extensionOf("dir.pdf/evil")).isEqualTo("bin");
    }

    @Test
    void resolveRejectsAStoredPathThatEscapesTheRoot() {
        FileStorageService storage = storage();
        assertThatThrownBy(() -> storage.resolve("../outside.pdf"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteRemovesTheFileAndToleratesItAlreadyBeingGone() throws IOException {
        FileStorageService storage = storage();
        String stored = storage.store(stream("bye"), "x.pdf");

        storage.delete(stored);
        assertThat(storage.exists(stored)).isFalse();

        storage.delete(stored);
        storage.delete(null);
        storage.delete("  ");
    }

    @Test
    void reportsSizeOfAStoredFile() throws IOException {
        FileStorageService storage = storage();
        String stored = storage.store(stream("12345"), "x.pdf");
        assertThat(storage.sizeOf(stored)).isEqualTo(5);
    }

    private static ByteArrayInputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
