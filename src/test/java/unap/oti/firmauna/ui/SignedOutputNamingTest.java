package unap.oti.firmauna.ui;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SignedOutputNamingTest {

    @Test
    void chainsFuMarkersForRepeatedSignatures() throws Exception {
        Path directory = Files.createTempDirectory("firmauna-output-");
        Path original = directory.resolve("Ficha Matricula (1).pdf");
        Path first = null;
        Path second = null;
        Path third = null;
        try {
            first = MainWindow.moveToNextSignedOutput(Files.createTempFile(directory, ".firmauna-", ".pdf"),
                original.toFile());
            second = MainWindow.moveToNextSignedOutput(Files.createTempFile(directory, ".firmauna-", ".pdf"),
                first.toFile());
            third = MainWindow.moveToNextSignedOutput(Files.createTempFile(directory, ".firmauna-", ".pdf"),
                second.toFile());

            assertEquals("Ficha Matricula (1) [FU].pdf", first.getFileName().toString());
            assertEquals("Ficha Matricula (1) [FFU].pdf", second.getFileName().toString());
            assertEquals("Ficha Matricula (1) [FFFU].pdf", third.getFileName().toString());
        } finally {
            Files.deleteIfExists(third);
            Files.deleteIfExists(second);
            Files.deleteIfExists(first);
            Files.deleteIfExists(directory);
        }
    }
}
