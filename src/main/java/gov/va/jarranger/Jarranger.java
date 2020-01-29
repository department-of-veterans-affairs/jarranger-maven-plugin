package gov.va.jarranger;

import static com.google.common.base.Preconditions.checkArgument;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.printer.PrettyPrinter;
import com.github.javaparser.printer.PrettyPrinterConfiguration;
import com.github.javaparser.utils.SourceRoot;
import java.io.File;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Builder;
import lombok.NonNull;
import lombok.SneakyThrows;
import lombok.Value;
import org.apache.maven.plugin.logging.Log;

@Builder
final class Jarranger {
  /** Maven plugin logger. */
  @NonNull private final Log log;

  /**
   * Arrange the type declaration and any of its descendants that are also type declarations.
   * Returns {@code true} if the order of members (or any descendant members) changed.
   */
  private static boolean deepArrange(final TypeDeclaration<?> typeDec) {
    boolean didArrangement = false;

    // First, recursively visit any children that also need to be arranged.
    for (final BodyDeclaration<?> child : typeDec.getMembers()) {
      if (child instanceof TypeDeclaration<?>) {
        didArrangement |= deepArrange((TypeDeclaration<?>) child);
      }
    }

    final List<BodyDeclaration<?>> arrangedNodes = new ShallowArranger(typeDec).getArrangedNodes();
    if (!typeDec.getMembers().equals(arrangedNodes)) {
      typeDec.setMembers(new NodeList<>(arrangedNodes));
      didArrangement = true;
    }

    return didArrangement;
  }

  private static PrettyPrinterConfiguration printerConfig() {
    final PrettyPrinterConfiguration printerConfig = new PrettyPrinterConfiguration();
    printerConfig.setEndOfLineCharacter("\n");
    printerConfig.setIndentSize(2);
    printerConfig.setIndentType(PrettyPrinterConfiguration.IndentType.SPACES);
    printerConfig.setOrderImports(true);
    printerConfig.setTabWidth(2);
    return printerConfig;
  }

  /**
   * Arrange each Java file in the given source directory. This directory corresponds to the root of
   * the package structure, e.g. proj/src/main/java or proj/src/test/java.
   */
  @SneakyThrows
  ArrangementResult arrange(final File sourceRootDir) {
    checkArgument(sourceRootDir.exists(), "File %s does not exist.", sourceRootDir);
    checkArgument(sourceRootDir.isDirectory(), "File %s is not a directory.", sourceRootDir);

    final SourceRoot sourceRoot = new SourceRoot(sourceRootDir.toPath());
    sourceRoot.setPrinter(new PrettyPrinter(printerConfig())::print);

    final AtomicInteger totalCount = new AtomicInteger();
    final AtomicInteger arrangedCount = new AtomicInteger();
    sourceRoot.parseParallelized(
        (localPath, absolutePath, parseResult) -> {
          log.debug("Processing " + absolutePath);
          totalCount.incrementAndGet();
          final Optional<CompilationUnit> optCompUnit = parseResult.getResult();
          if (!optCompUnit.isPresent()
              || optCompUnit.get().getTypes() == null
              || optCompUnit.get().getTypes().isEmpty()) {
            log.warn("Failed to parse " + absolutePath);
            return SourceRoot.Callback.Result.DONT_SAVE;
          }

          boolean didArrangement = false;
          for (final TypeDeclaration<?> typeDec : optCompUnit.get().getTypes()) {
            didArrangement |= deepArrange(typeDec);
          }

          // Only modify the files where the order changed.
          if (!didArrangement) {
            return SourceRoot.Callback.Result.DONT_SAVE;
          }

          log.debug("Arranged " + absolutePath);
          arrangedCount.incrementAndGet();
          return SourceRoot.Callback.Result.SAVE;
        });

    return ArrangementResult.builder()
        .total(totalCount.get())
        .arranged(arrangedCount.get())
        .build();
  }

  @Value
  @Builder
  static final class ArrangementResult {
    public static final ArrangementResult EMPTY = new ArrangementResult(0, 0);

    private final int total;

    private final int arranged;

    public ArrangementResult add(final ArrangementResult other) {
      return builder().total(total + other.total).arranged(arranged + other.arranged).build();
    }
  }
}
