package lide;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for class-name search used by Go to Class.
 */
public final class ClassSearchTest {

    @Test
    void testRankExactPrefixContainsCamel() throws Exception {
        assertEquals(0, ClassSearch.rank("ClassNavigator", "ClassNavigator"), "exact");
        assertEquals(0, ClassSearch.rank("ClassNavigator", "classnavigator"), "exact ignore case");
        assertEquals(1, ClassSearch.rank("ClassNavigator", "Class"), "prefix");
        assertEquals(2, ClassSearch.rank("ClassNavigator", "Nav"), "contains");
        assertEquals(3, ClassSearch.rank("ClassNavigator", "CN"), "camel");
        assertEquals(-1, ClassSearch.rank("ClassNavigator", "XYZ"), "no match");
        assertEquals(-1, ClassSearch.rank("Foo", ""), "blank query");
    }

    @Test
    void testCamelMatch() throws Exception {
        assertTrue(ClassSearch.camelMatch("ClassNavigator", "CN"), "CN");
        assertTrue(ClassSearch.camelMatch("ClassNavigator", "cn"), "cn");
        assertTrue(ClassSearch.camelMatch("ClassNavigator", "CNav"), "CNav");
        assertTrue(!ClassSearch.camelMatch("ClassNavigator", "XYZ"), "no match");
        assertTrue(ClassSearch.camelMatch("CodeEditor", "CE"), "CodeEditor CE");
    }

    @Test
    void testFilterOrdersBestMatchesFirst() throws Exception {
        Path a = Path.of("src", "ClassNavigator.java");
        Path b = Path.of("src", "ClassSearch.java");
        Path c = Path.of("test", "ClassNavigatorTest.java");
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("ClassNavigator", a, "src/ClassNavigator.java"),
                new ClassSearch.Hit("ClassSearch", b, "src/ClassSearch.java"),
                new ClassSearch.Hit("ClassNavigatorTest", c, "test/ClassNavigatorTest.java"));
        List<ClassSearch.Hit> hits = ClassSearch.filter(all, "ClassNavigator");
        assertEquals("ClassNavigator", hits.get(0).className(), "exact first");
        assertTrue(hits.stream().anyMatch(h -> h.className().equals("ClassNavigatorTest")), "includes test");
        List<ClassSearch.Hit> camel = ClassSearch.filter(all, "CN");
        assertTrue(camel.stream().anyMatch(h -> h.className().equals("ClassNavigator")), "camel finds navigator");
    }

    @Test
    void testEmptyQueryReturnsNothing() throws Exception {
        List<ClassSearch.Hit> all = List.of(
                new ClassSearch.Hit("Foo", Path.of("Foo.java"), "Foo.java"));
        assertEquals(List.of(), ClassSearch.filter(all, "  "), "empty");
        assertEquals(List.of(), ClassSearch.filter(all, null), "null query");
        assertEquals(List.of(), ClassSearch.filter(null, "Foo"), "null list");
    }

    @Test
    void testListsJavaAndSkipsBuildDir() throws Exception {
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
            assertEquals(List.of("Foo", "FooTest"), names, "listed names");
            assertTrue(listed.stream().anyMatch(h -> h.relativePath().equals("src/lide/Foo.java")), "relative src");
        } finally {
            deleteRecursive(root);
        }
    }

    @Test
    void testIsClassFile() throws Exception {
        assertTrue(ClassSearch.isClassFile("Foo.java"), "java");
        assertTrue(ClassSearch.isClassFile("App.js"), "js");
        assertTrue(ClassSearch.isClassFile("app.py"), "py");
        assertTrue(!ClassSearch.isClassFile("README.md"), "md skipped");
        assertTrue(!ClassSearch.isClassFile(null), "null");
        assertEquals("Foo", ClassSearch.stem("Foo.java"), "stem");
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

}
