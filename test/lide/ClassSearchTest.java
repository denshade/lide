package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for class-name search used by Go to Class.
 */
public final class ClassSearchTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testRankExactPrefixContainsCamel();
        testCamelMatch();
        testFilterOrdersBestMatchesFirst();
        testEmptyQueryReturnsNothing();
        testListsJavaAndSkipsBuildDir();
        testIsClassFile();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testRankExactPrefixContainsCamel() {
        assertEqual("exact", 0, ClassSearch.rank("ClassNavigator", "ClassNavigator"));
        assertEqual("exact ignore case", 0, ClassSearch.rank("ClassNavigator", "classnavigator"));
        assertEqual("prefix", 1, ClassSearch.rank("ClassNavigator", "Class"));
        assertEqual("contains", 2, ClassSearch.rank("ClassNavigator", "Nav"));
        assertEqual("camel", 3, ClassSearch.rank("ClassNavigator", "CN"));
        assertEqual("no match", -1, ClassSearch.rank("ClassNavigator", "XYZ"));
        assertEqual("blank query", -1, ClassSearch.rank("Foo", ""));
    }

    private static void testCamelMatch() {
        assertTrue("CN", ClassSearch.camelMatch("ClassNavigator", "CN"));
        assertTrue("cn", ClassSearch.camelMatch("ClassNavigator", "cn"));
        assertTrue("CNav", ClassSearch.camelMatch("ClassNavigator", "CNav"));
        assertTrue("no match", !ClassSearch.camelMatch("ClassNavigator", "XYZ"));
        assertTrue("CodeEditor CE", ClassSearch.camelMatch("CodeEditor", "CE"));
    }

    private static void testFilterOrdersBestMatchesFirst() {
        Path a = Path.of("src", "ClassNavigator.java");
        Path b = Path.of("src", "ClassSearch.java");
        Path c = Path.of("test", "ClassNavigatorTest.java");
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("ClassNavigator", a, "src/ClassNavigator.java"),
                new ClassSearch.Hit("ClassSearch", b, "src/ClassSearch.java"),
                new ClassSearch.Hit("ClassNavigatorTest", c, "test/ClassNavigatorTest.java"));
        List<ClassSearch.Hit> hits = ClassSearch.filter(all, "ClassNavigator");
        assertEqual("exact first", "ClassNavigator", hits.get(0).className());
        assertTrue("includes test", hits.stream().anyMatch(h -> h.className().equals("ClassNavigatorTest")));
        List<ClassSearch.Hit> camel = ClassSearch.filter(all, "CN");
        assertTrue("camel finds navigator",
                camel.stream().anyMatch(h -> h.className().equals("ClassNavigator")));
    }

    private static void testEmptyQueryReturnsNothing() {
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("Foo", Path.of("Foo.java"), "Foo.java"));
        assertEqual("empty", List.of(), ClassSearch.filter(all, "  "));
        assertEqual("null query", List.of(), ClassSearch.filter(all, null));
        assertEqual("null list", List.of(), ClassSearch.filter(null, "Foo"));
    }

    private static void testListsJavaAndSkipsBuildDir() throws Exception {
        Path root = Files.createTempDirectory("lide-class-search");
        try {
            Path src = root.resolve("src").resolve("lide");
            Path test = root.resolve("test").resolve("lide");
            Path out = root.resolve("out").resolve("lide");
            Files.createDirectories(src);
            Files.createDirectories(test);
            Files.createDirectories(out);
            Files.writeString(src.resolve("Foo.java"), "class Foo {}");
            Files.writeString(test.resolve("FooTest.java"), "class FooTest {}");
            Files.writeString(src.resolve("notes.md"), "# not a class");
            Files.writeString(out.resolve("Foo.java"), "class Foo {}");
            List<ClassSearch.Hit> listed = ClassSearch.listClasses(root);
            List<String> names = listed.stream().map(ClassSearch.Hit::className).sorted().toList();
            assertEqual("listed names", List.of("Foo", "FooTest"), names);
            assertTrue("relative src", listed.stream().anyMatch(h -> h.relativePath().equals("src/lide/Foo.java")));
        } finally {
            deleteRecursive(root);
        }
    }

    private static void testIsClassFile() {
        assertTrue("java", ClassSearch.isClassFile("Foo.java"));
        assertTrue("js", ClassSearch.isClassFile("App.js"));
        assertTrue("py", ClassSearch.isClassFile("app.py"));
        assertTrue("md skipped", !ClassSearch.isClassFile("README.md"));
        assertTrue("null", !ClassSearch.isClassFile(null));
        assertEqual("stem", "Foo", ClassSearch.stem("Foo.java"));
    }

    private static void deleteRecursive(Path root) throws Exception {
        if (!Files.exists(root)) {
            return;
        }
        try (var walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.compareTo(a)).toList();
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

    private ClassSearchTest() {
    }
}
