package lide;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Tests for copying a Ladle distribution into a project.
 */
public final class LadleInstallerTest {
    private static int passed;
    private static int failed;

    public static void main(String[] args) throws Exception {
        testIsDistribution();
        testFindDistributionSkipsTarget();
        testFindDistributionFindsSibling();
        testInstallCopiesJarScriptsAndIni();
        testInstallDoesNotOverwriteIni();
        testDefaultBuildIniUsesProjectName();
        testInstallRejectsMissingJar();
        System.out.println("Passed: " + passed + ", Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testIsDistribution() throws Exception {
        Path dist = fakeDistribution();
        Path other = Files.createTempDirectory("lide-not-ladle");
        try {
            assertTrue("dist", LadleInstaller.isDistribution(dist));
            assertTrue("other", !LadleInstaller.isDistribution(other));
            assertTrue("null", !LadleInstaller.isDistribution(null));
        } finally {
            deleteRecursive(dist);
            deleteRecursive(other);
        }
    }

    private static void testFindDistributionSkipsTarget() throws Exception {
        Path dist = fakeDistribution();
        try {
            Path found = LadleInstaller.findDistribution(dist, List.of(dist));
            assertTrue("skips installing from itself", found == null);
        } finally {
            deleteRecursive(dist);
        }
    }

    private static void testFindDistributionFindsSibling() throws Exception {
        Path parent = Files.createTempDirectory("lide-git");
        Path project = Files.createDirectories(parent.resolve("app"));
        Path ladle = fakeDistribution(parent.resolve("ladle"));
        try {
            Path found = LadleInstaller.findDistribution(project, List.of(project));
            assertTrue("found sibling", found != null);
            assertEqual("sibling path", ladle.toAbsolutePath().normalize(), found);
        } finally {
            deleteRecursive(parent);
        }
    }

    private static void testInstallCopiesJarScriptsAndIni() throws Exception {
        Path dist = fakeDistribution();
        Path project = Files.createTempDirectory("lide-install-target");
        try {
            LadleInstaller.Result result = LadleInstaller.install(dist, project);
            assertTrue("jar copied", Files.isRegularFile(project.resolve("lib").resolve("ladle.jar")));
            assertEqual("jar bytes", "jar-bytes", Files.readString(project.resolve("lib").resolve("ladle.jar")));
            assertTrue("cmd copied", Files.isRegularFile(project.resolve("bin").resolve("ladle.cmd")));
            assertTrue("unix copied", Files.isRegularFile(project.resolve("bin").resolve("ladle")));
            assertTrue("wrote ini", result.wroteIni());
            assertTrue("ini exists", Files.isRegularFile(project.resolve("build.ini")));
            assertTrue("available", LadleCommand.isAvailable(project));
            assertEqual("script count", 2, result.scripts().size());
        } finally {
            deleteRecursive(dist);
            deleteRecursive(project);
        }
    }

    private static void testInstallDoesNotOverwriteIni() throws Exception {
        Path dist = fakeDistribution();
        Path project = Files.createTempDirectory("lide-install-keep-ini");
        try {
            Files.writeString(project.resolve("build.ini"), "# keep me\n");
            LadleInstaller.Result result = LadleInstaller.install(dist, project);
            assertTrue("did not write ini", !result.wroteIni());
            assertEqual("ini preserved", "# keep me\n", Files.readString(project.resolve("build.ini")));
        } finally {
            deleteRecursive(dist);
            deleteRecursive(project);
        }
    }

    private static void testDefaultBuildIniUsesProjectName() {
        String ini = LadleInstaller.defaultBuildIni(Path.of("C:/work/myapp"));
        assertTrue("jar name", ini.contains("name = myapp"));
        assertTrue("sources", ini.contains("paths = src"));
        assertTrue("test", ini.contains("sources = test"));
        assertTrue("no JAVA_HOME placeholder", !ini.contains("path = $JAVA_HOME"));
        assertTrue("has javac path", ini.contains("path = "));
        assertTrue("mentions main-class", ini.contains("main-class = your.package.Main"));
        assertTrue("main-class is commented", ini.contains("# main-class ="));
    }

    private static void testInstallRejectsMissingJar() throws Exception {
        Path empty = Files.createTempDirectory("lide-empty-dist");
        Path project = Files.createTempDirectory("lide-empty-target");
        try {
            try {
                LadleInstaller.install(empty, project);
                failed++;
                System.err.println("FAIL expected IllegalArgumentException");
            } catch (IllegalArgumentException ex) {
                passed++;
                assertTrue("mentions jar", ex.getMessage().contains("ladle.jar"));
            }
        } finally {
            deleteRecursive(empty);
            deleteRecursive(project);
        }
    }

    private static Path fakeDistribution() throws Exception {
        return fakeDistribution(Files.createTempDirectory("lide-ladle-dist"));
    }

    private static Path fakeDistribution(Path root) throws Exception {
        Files.createDirectories(root.resolve("lib"));
        Files.createDirectories(root.resolve("bin"));
        Files.writeString(root.resolve("lib").resolve("ladle.jar"), "jar-bytes");
        Files.writeString(root.resolve("bin").resolve("ladle.cmd"), "@echo off\n");
        Files.writeString(root.resolve("bin").resolve("ladle"), "#!/bin/sh\n");
        return root.toAbsolutePath().normalize();
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

    private LadleInstallerTest() {
    }
}
