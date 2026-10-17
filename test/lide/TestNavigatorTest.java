package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Tests for pairing a class file with its test file by name.
 */
public final class TestNavigatorTest {

    @Test
    void testFileNameMapping() throws Exception {
        assertEquals("FooTest.java", TestNavigator.testFileName("Foo.java"), "java");
        assertEquals("FooTest.py", TestNavigator.testFileName("Foo.py"), "py");
        assertEquals(null, TestNavigator.testFileName("FooTest.java"), "already test");
        assertEquals(null, TestNavigator.testFileName("Foo"), "no extension");
        assertEquals(null, TestNavigator.testFileName(null), "null");
        assertTrue(TestNavigator.testFileName("Latest.java") != null, "Latest is not a test");
        assertEquals("LatestTest.java", TestNavigator.testFileName("Latest.java"), "Latest test name");
    }

    @Test
    void testImplementationFileNameMapping() throws Exception {
        assertEquals("Foo.java", TestNavigator.implementationFileName("FooTest.java"), "java");
        assertEquals("Foo.py", TestNavigator.implementationFileName("FooTest.py"), "py");
        assertEquals(null, TestNavigator.implementationFileName("Foo.java"), "not a test");
        assertEquals(null, TestNavigator.implementationFileName("FooTests.java"), "Tests plural");
        assertEquals("A.java", TestNavigator.implementationFileName("ATest.java"), "ATest");
    }

    @Test
    void testAlreadyTestHasNoTestCounterpart() throws Exception {
        assertEquals(null, TestNavigator.testFileName("FooTest.java"), "FooTest");
        assertTrue(TestNavigator.isTestStem("FooTest"), "is test stem");
        assertTrue(!TestNavigator.isTestStem("Foo"), "Foo is not test stem");
        assertTrue(!TestNavigator.isTestStem("Latest"), "Latest is not test stem");
    }

    @Test
    void testStemNamedTestIsNotAPair() throws Exception {
        assertTrue(!TestNavigator.isTestStem("Test"), "Test.java is not a test pair");
        assertEquals(null, TestNavigator.implementationFileName("Test.java"), "Test.java implementation");
        assertEquals("TestTest.java", TestNavigator.testFileName("Test.java"), "Test.java test");
    }

    @Test
    void testIsTestJavaFile() throws Exception {
        assertTrue(TestNavigator.isTestJavaFile(Path.of("test", "FooTest.java")), "FooTest.java");
        assertTrue(TestNavigator.isTestJavaFile(Path.of("Test.java")), "Test.java");
        assertTrue(!TestNavigator.isTestJavaFile(Path.of("src", "Foo.java")), "Foo is not a test file");
        assertTrue(!TestNavigator.isTestJavaFile(Path.of("Latest.java")), "Latest.java is not a test file");
        assertTrue(!TestNavigator.isTestJavaFile(Path.of("FooTests.java")), "FooTests.java is not *Test.java");
        assertTrue(!TestNavigator.isTestJavaFile(null), "null path");
    }

    @Test
    void testFindsTestInSrcTestLayout() throws Exception {
        Path root = Files.createTempDirectory("lide-test-nav");
        try {
            Path src = root.resolve("src").resolve("lide");
            Path test = root.resolve("test").resolve("lide");
            Files.createDirectories(src);
            Files.createDirectories(test);
            Path impl = src.resolve("Foo.java");
            Path testFile = test.resolve("FooTest.java");
            Files.writeString(impl, "class Foo {}");
            Files.writeString(testFile, "class FooTest {}");
            Optional<Path> found = TestNavigator.findTest(root, impl);
            assertTrue(found.isPresent(), "found test");
            assertEquals(testFile.toAbsolutePath().normalize(), found.get(), "test path");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testFindsImplementationInSrcTestLayout() throws Exception {
        Path root = Files.createTempDirectory("lide-impl-nav");
        try {
            Path src = root.resolve("src").resolve("lide");
            Path test = root.resolve("test").resolve("lide");
            Files.createDirectories(src);
            Files.createDirectories(test);
            Path impl = src.resolve("Foo.java");
            Path testFile = test.resolve("FooTest.java");
            Files.writeString(impl, "class Foo {}");
            Files.writeString(testFile, "class FooTest {}");
            Optional<Path> found = TestNavigator.findImplementation(root, testFile);
            assertTrue(found.isPresent(), "found impl");
            assertEquals(impl.toAbsolutePath().normalize(), found.get(), "impl path");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testMissingCounterpartIsEmpty() throws Exception {
        Path root = Files.createTempDirectory("lide-test-nav-missing");
        try {
            Path src = root.resolve("src");
            Files.createDirectories(src);
            Path impl = src.resolve("Lonely.java");
            Files.writeString(impl, "class Lonely {}");
            assertTrue(TestNavigator.findTest(root, impl).isEmpty(), "no test");
            assertTrue(TestNavigator.findImplementation(root, impl).isEmpty(), "no impl from source");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testRequiresProjectRoot() throws Exception {
        Path file = Files.createTempFile("Foo", ".java");
        try {
            assertTrue(TestNavigator.findTest(null, file).isEmpty(), "no root");
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            java.util.List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

}
