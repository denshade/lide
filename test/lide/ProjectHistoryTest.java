package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for persisted project directory history.
 */
public final class ProjectHistoryTest {

    @Test
    void testRememberOrdersMostRecentFirst() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 10);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            assertEquals(List.of(b, a), history.entries(), "order");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void testRememberDedupesAndMovesToFront() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 10);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            history.remember(a);
            assertEquals(List.of(a, b), history.entries(), "dedupe");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void testRespectsLimit() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 2);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            Path c = Path.of("C:/projects/c").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            history.remember(c);
            assertEquals(List.of(c, b), history.entries(), "limit");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void testPersistsAcrossInstances() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            Path a = Path.of("C:/projects/persist").toAbsolutePath().normalize();
            ProjectHistory first = new ProjectHistory(file, 10);
            first.remember(a);
            ProjectHistory second = new ProjectHistory(file, 10);
            assertEquals(List.of(a), second.entries(), "persisted");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void testRemoveAndClear() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 10);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            history.remove(a);
            assertEquals(List.of(b), history.entries(), "after remove");
            history.clear();
            assertEquals(List.of(), history.entries(), "after clear");
            ProjectHistory reloaded = new ProjectHistory(file, 10);
            assertEquals(List.of(), reloaded.entries(), "clear persisted");
        } finally {
            Files.deleteIfExists(file);
        }
    }

}
