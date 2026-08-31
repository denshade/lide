package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Tests for pairing a class file with its test file by name.
 */
public final class TestNavigatorTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testFileNameMapping();
        testImplementationFileNameMapping();
        testAlreadyTestHasNoTestCounterpart();
        testStemNamedTestIsNotAPair();
        testFindsTestInSrcTestLayout();
        testFindsImplementationInSrcTestLayout();
        testMissingCounterpartIsEmpty();
        testRequiresProjectRoot();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFileNameMapping() {
        assertEqual("java", "FooTest.java", TestNavigator.testFileName("Foo.java"));
        assertEqual("py", "FooTest.py", TestNavigator.testFileName("Foo.py"));
        assertEqual("already test", null, TestNavigator.testFileName("FooTest.java"));
        assertEqual("no extension", null, TestNavigator.testFileName("Foo"));
        assertEqual("null", null, TestNavigator.testFileName(null));
        assertTrue("Latest is not a test", TestNavigator.testFileName("Latest.java") != null);
        assertEqual("Latest test name", "LatestTest.java", TestNavigator.testFileName("Latest.java"));
    }

    private static void testImplementationFileNameMapping() {
        assertEqual("java", "Foo.java", TestNavigator.implementationFileName("FooTest.java"));
        assertEqual("py", "Foo.py", TestNavigator.implementationFileName("FooTest.py"));
        assertEqual("not a test", null, TestNavigator.implementationFileName("Foo.java"));
        assertEqual("Tests plural", null, TestNavigator.implementationFileName("FooTests.java"));
        assertEqual("ATest", "A.java", TestNavigator.implementationFileName("ATest.java"));
    }

    private static void testAlreadyTestHasNoTestCounterpart() {
        assertEqual("FooTest", null, TestNavigator.testFileName("FooTest.java"));
        assertTrue("is test stem", TestNavigator.isTestStem("FooTest"));
        assertTrue("Foo is not test stem", !TestNavigator.isTestStem("Foo"));
        assertTrue("Latest is not test stem", !TestNavigator.isTestStem("Latest"));
    }

    private static void testStemNamedTestIsNotAPair() {
        assertTrue("Test.java is not a test pair", !TestNavigator.isTestStem("Test"));
        assertEqual("Test.java implementation", null, TestNavigator.implementationFileName("Test.java"));
        assertEqual("Test.java test", "TestTest.java", TestNavigator.testFileName("Test.java"));
    }

    private static void testFindsTestInSrcTestLayout() throws Exception {
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
            assertTrue("found test", found.isPresent());
            assertEqual("test path", testFile.toAbsolutePath().normalize(), found.get());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testFindsImplementationInSrcTestLayout() throws Exception {
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
            assertTrue("found impl", found.isPresent());
            assertEqual("impl path", impl.toAbsolutePath().normalize(), found.get());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testMissingCounterpartIsEmpty() throws Exception {
        Path root = Files.createTempDirectory("lide-test-nav-missing");
        try {
            Path src = root.resolve("src");
            Files.createDirectories(src);
            Path impl = src.resolve("Lonely.java");
            Files.writeString(impl, "class Lonely {}");
            assertTrue("no test", TestNavigator.findTest(root, impl).isEmpty());
            assertTrue("no impl from source", TestNavigator.findImplementation(root, impl).isEmpty());
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testRequiresProjectRoot() throws Exception {
        Path file = Files.createTempFile("Foo", ".java");
        try {
            assertTrue("no root", TestNavigator.findTest(null, file).isEmpty());
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

    private static void assertEqual(String label, Object expected, Object actual) {
        if (expected == null ? actual == null : expected.equals(actual)) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label + ": expected " + expected + " but was " + actual);
    }

    private static void assertTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            return;
        }
        failed++;
        System.err.println("FAIL " + label);
    }

    private TestNavigatorTest() {
    }
}
