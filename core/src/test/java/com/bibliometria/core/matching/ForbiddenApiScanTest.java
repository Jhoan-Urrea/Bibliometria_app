package com.bibliometria.core.matching;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForbiddenApiScanTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    private static final Set<String> FORBIDDEN_METHODS =
            Set.of(
                    "indexOf",
                    "lastIndexOf",
                    "contains",
                    "matches",
                    "split",
                    "replace",
                    "startsWith",
                    "endsWith",
                    "find",
                    "regionMatches");
    private static final List<String> ALLOWED_IMPORT_PREFIXES =
            List.of("java.", "com.bibliometria.core.", "co.edu.uniquindio.biblio.core.");

    @TempDir Path temporaryDirectory;

    @Test
    void allCoreProductionSourcesUseOnlyAllowedApisAndImports() throws IOException {
        assertThat(Files.isDirectory(PRODUCTION_SOURCES)).isTrue();
        assertThat(scan(PRODUCTION_SOURCES)).isEmpty();
    }

    @Test
    void detectsEveryForbiddenStringMethod() throws IOException {
        Path source = temporaryDirectory.resolve("StringOffender.java");
        Files.writeString(
                source,
                """
                class StringOffender {
                    void check(String text) {
                        text.indexOf("x");
                        text.lastIndexOf("x");
                        text.contains("x");
                        text.matches("x");
                        text.split("x");
                        text.replace("x", "y");
                        text.startsWith("x");
                        text.endsWith("x");
                        text.regionMatches(0, "x", 0, 1);
                    }
                }
                """,
                StandardCharsets.UTF_8);

        List<String> violations = scan(temporaryDirectory);
        for (String method :
                List.of(
                        "indexOf",
                        "lastIndexOf",
                        "contains",
                        "matches",
                        "split",
                        "replace",
                        "startsWith",
                        "endsWith",
                        "regionMatches")) {
            assertThat(violations)
                    .anyMatch(violation -> violation.contains("llamada prohibida a " + method));
        }
    }

    @Test
    void detectsFindCalls() throws IOException {
        Path source = temporaryDirectory.resolve("FindOffender.java");
        Files.writeString(
                source,
                "class FindOffender { void check(Index index) { index.find(); } class Index { void find() {} } }",
                StandardCharsets.UTF_8);

        assertThat(scan(temporaryDirectory))
                .anyMatch(violation -> violation.contains("llamada prohibida a find"));
    }

    @Test
    void detectsRegexPackagesAndClassNames() throws IOException {
        Path source = temporaryDirectory.resolve("RegexOffender.java");
        Files.writeString(
                source,
                """
                import java.util.regex.Pattern;
                class RegexOffender {
                    Pattern pattern;
                    Matcher matcher;
                }
                """,
                StandardCharsets.UTF_8);

        List<String> violations = scan(temporaryDirectory);
        assertThat(violations)
                .anyMatch(violation -> violation.contains("paquete prohibido java.util.regex"));
        assertThat(violations).anyMatch(violation -> violation.contains("clase prohibida Pattern"));
        assertThat(violations).anyMatch(violation -> violation.contains("clase prohibida Matcher"));
    }

    @Test
    void rejectsImportsOutsideTheJdkAndCoreDomain() throws IOException {
        Path source = temporaryDirectory.resolve("ExternalImport.java");
        Files.writeString(
                source,
                "import org.example.ExternalLibrary;\nclass ExternalImport {}",
                StandardCharsets.UTF_8);

        assertThat(scan(temporaryDirectory))
                .anyMatch(
                        violation ->
                                violation.contains(
                                        "importación no permitida org.example.ExternalLibrary"));
    }

    private static List<String> scan(Path sourceRoot) throws IOException {
        List<Path> sourceFiles;
        try (var files = Files.walk(sourceRoot)) {
            sourceFiles =
                    files.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".java"))
                            .sorted()
                            .toList();
        }
        if (sourceFiles.isEmpty()) {
            return List.of();
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("A JDK compiler is required to scan Java sources.");
        }

        List<String> violations = new ArrayList<>();
        DiagnosticCollector<javax.tools.JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends javax.tools.JavaFileObject> files =
                    fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            JavacTask task =
                    (JavacTask)
                            compiler.getTask(
                                    null,
                                    fileManager,
                                    diagnostics,
                                    List.of("--release", "21", "-proc:none"),
                                    null,
                                    files);
            List<CompilationUnitTree> units = new ArrayList<>();
            task.parse().forEach(units::add);
            task.analyze();

            Trees trees = Trees.instance(task);
            for (CompilationUnitTree unit : units) {
                new ForbiddenSourceScanner(unit, trees, violations).scan(unit, null);
            }
        }
        return List.copyOf(violations);
    }

    private static final class ForbiddenSourceScanner extends TreePathScanner<Void, Void> {

        private final CompilationUnitTree unit;
        private final Trees trees;
        private final List<String> violations;

        private ForbiddenSourceScanner(
                CompilationUnitTree unit, Trees trees, List<String> violations) {
            this.unit = unit;
            this.trees = trees;
            this.violations = violations;
        }

        @Override
        public Void visitImport(ImportTree node, Void unused) {
            String importedName = node.getQualifiedIdentifier().toString();
            if (importedName.startsWith("java.util.regex.")) {
                addViolation(node, "paquete prohibido java.util.regex");
            }

            String importRoot = importedName;
            if (node.isStatic()) {
                int memberSeparator = importedName.lastIndexOf('.');
                if (memberSeparator >= 0) {
                    importRoot = importedName.substring(0, memberSeparator);
                }
            }
            if (ALLOWED_IMPORT_PREFIXES.stream().noneMatch(importRoot::startsWith)) {
                addViolation(node, "importación no permitida " + importedName);
            }
            return super.visitImport(node, unused);
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            String identifier = node.getName().toString();
            if (identifier.equals("Pattern") || identifier.equals("Matcher")) {
                addViolation(node, "clase prohibida " + identifier);
            }
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type
                    && type.getQualifiedName().toString().startsWith("java.util.regex.")) {
                addViolation(node, "clase prohibida " + type.getQualifiedName());
            }
            return super.visitIdentifier(node, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            String selectedName = node.toString();
            if (selectedName.startsWith("java.util.regex.")) {
                addViolation(node, "paquete prohibido java.util.regex");
            }
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement type
                    && type.getQualifiedName().toString().startsWith("java.util.regex.")) {
                addViolation(node, "clase prohibida " + type.getQualifiedName());
            }
            return super.visitMemberSelect(node, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree node, Void unused) {
            String methodName = methodName(node);
            if (FORBIDDEN_METHODS.contains(methodName)) {
                Element element = trees.getElement(getCurrentPath());
                if (methodName.equals("find") || isStringMethod(element)) {
                    addViolation(node, "llamada prohibida a " + methodName);
                }
            }
            return super.visitMethodInvocation(node, unused);
        }

        private boolean isStringMethod(Element element) {
            return element instanceof ExecutableElement method
                    && method.getEnclosingElement() instanceof TypeElement owner
                    && owner.getQualifiedName().contentEquals("java.lang.String");
        }

        private String methodName(MethodInvocationTree invocation) {
            String selector = invocation.getMethodSelect().toString();
            int separator = selector.lastIndexOf('.');
            return separator < 0 ? selector : selector.substring(separator + 1);
        }

        private void addViolation(com.sun.source.tree.Tree tree, String message) {
            long position = trees.getSourcePositions().getStartPosition(unit, tree);
            long line =
                    position < 0 || unit.getLineMap() == null
                            ? 1
                            : unit.getLineMap().getLineNumber(position);
            violations.add(unit.getSourceFile().getName() + ":" + line + ": " + message);
        }
    }
}
