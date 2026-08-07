package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for persisted project directory history.
 */
public final class ProjectHistoryTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testRememberOrdersMostRecentFirst();
        testRememberDedupesAndMovesToFront();
        testRespectsLimit();
        testPersistsAcrossInstances();
        testRemoveAndClear();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRememberOrdersMostRecentFirst() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 10);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            assertEqual("order", List.of(b, a), history.entries());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void testRememberDedupesAndMovesToFront() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 10);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            history.remember(a);
            assertEqual("dedupe", List.of(a, b), history.entries());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void testRespectsLimit() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 2);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            Path c = Path.of("C:/projects/c").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            history.remember(c);
            assertEqual("limit", List.of(c, b), history.entries());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void testPersistsAcrossInstances() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            Path a = Path.of("C:/projects/persist").toAbsolutePath().normalize();
            ProjectHistory first = new ProjectHistory(file, 10);
            first.remember(a);
            ProjectHistory second = new ProjectHistory(file, 10);
            assertEqual("persisted", List.of(a), second.entries());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void testRemoveAndClear() throws Exception {
        Path file = Files.createTempFile("lide-history-", ".txt");
        try {
            ProjectHistory history = new ProjectHistory(file, 10);
            Path a = Path.of("C:/projects/a").toAbsolutePath().normalize();
            Path b = Path.of("C:/projects/b").toAbsolutePath().normalize();
            history.remember(a);
            history.remember(b);
            history.remove(a);
            assertEqual("after remove", List.of(b), history.entries());
            history.clear();
            assertEqual("after clear", List.of(), history.entries());
            ProjectHistory reloaded = new ProjectHistory(file, 10);
            assertEqual("clear persisted", List.of(), reloaded.entries());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected " + expected + " but was " + actual);
    }

    private ProjectHistoryTest() {
    }
}
