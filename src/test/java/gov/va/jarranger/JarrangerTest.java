package gov.va.jarranger;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.utils.CodeGenerationUtils;
import com.github.javaparser.utils.SourceRoot;
import gov.va.jarranger.Jarranger.ArrangementResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import lombok.SneakyThrows;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Many of these tests use pairs of files in src/test/resources: a *.java file and a *.expected.java
 * file. The *.java file is arranged and compared to the *.expected.java file, which has been
 * arranged using IntelliJ IDEA.
 */
public final class JarrangerTest {
  private Path testResourcesPath;

  private String targetFileName;

  @SneakyThrows
  private void _backup() {
    assertThat(testResourcesPath).isNotNull();
    assertThat(targetFileName).isNotNull();
    Files.copy(
        testResourcesPath.resolve(targetFileName + ".java"),
        testResourcesPath.resolve(targetFileName + ".bak"),
        StandardCopyOption.REPLACE_EXISTING);
    assertThat(testResourcesPath.resolve(targetFileName + ".bak").toFile().exists()).isTrue();
  }

  @AfterEach
  public void _cleanUp() {
    assertThat(testResourcesPath.resolve(targetFileName + ".bak").toFile().exists()).isTrue();
    final boolean deleted = testResourcesPath.resolve(targetFileName + ".java").toFile().delete();
    assertThat(deleted).isTrue();
    final boolean renamed =
        testResourcesPath
            .resolve(targetFileName + ".bak")
            .toFile()
            .renameTo(testResourcesPath.resolve(targetFileName + ".java").toFile());
    assertThat(renamed).isTrue();
    testResourcesPath = null;
    targetFileName = null;
  }

  private void _parseAndCompare() {
    _backup();
    final CompilationUnit expectedCompUnit =
        new SourceRoot(testResourcesPath).parse("", targetFileName + ".java.expected");
    JarrangerMojo.builder()
        .log(new SystemStreamLog())
        .sourceDirectory(testResourcesPath.toFile())
        .build()
        .execute();
    final CompilationUnit arrangedCompUnit =
        new SourceRoot(testResourcesPath).parse("", targetFileName + ".java");
    assertThat(arrangedCompUnit).isEqualTo(expectedCompUnit);
  }

  @Test
  public void arrange() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/general");
    targetFileName = "ArrangePlz";
    _parseAndCompare();
  }

  @Test
  public void gettersAndSetters() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass())
            .resolve("src/test/resources/gettersandsetters");
    targetFileName = "GettersAndSetters";
    _parseAndCompare();
  }

  @Test
  public void innerAnnotation() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass())
            .resolve("src/test/resources/innerannotation");
    targetFileName = "InnerAnnotation";
    _parseAndCompare();
  }

  @Test
  public void innerClass() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/innerclass");
    targetFileName = "InnerClass";
    _parseAndCompare();
  }

  @Test
  public void innerEnum() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/innerenum");
    targetFileName = "InnerEnum";
    _parseAndCompare();
  }

  @Test
  public void innerInterface() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass())
            .resolve("src/test/resources/innerinterface");
    targetFileName = "InnerInterface";
    _parseAndCompare();
  }

  @Test
  public void malformed() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/malformed");
    targetFileName = "Malformed";
    _backup();
    final ArrangementResult result =
        JarrangerMojo.builder()
            .log(new SystemStreamLog())
            .testSourceDirectory(testResourcesPath.toFile())
            .build()
            .arrange();
    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getArranged()).isEqualTo(0);
  }

  @Test
  public void noChange() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/nochange");
    targetFileName = "NoChange";
    _backup();
    final ArrangementResult result =
        JarrangerMojo.builder()
            .log(new SystemStreamLog())
            .testSourceDirectory(testResourcesPath.toFile())
            .build()
            .arrange();
    assertThat(result.getTotal()).isEqualTo(1);
    assertThat(result.getArranged()).isEqualTo(0);
  }

  @Test
  public void overloaded() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/overloaded");
    targetFileName = "Overloaded";
    _parseAndCompare();
  }

  @Test
  public void overloadedSimple() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass())
            .resolve("src/test/resources/overloadedsimple");
    targetFileName = "OverloadedSimple";
    _parseAndCompare();
  }

  @Test
  public void pom() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/general");
    targetFileName = "ArrangePlz";
    _backup();
    final ArrangementResult result =
        JarrangerMojo.builder()
            .log(new SystemStreamLog())
            .packaging("pom")
            .sourceDirectory(testResourcesPath.toFile())
            .build()
            .arrange();
    assertThat(result.getTotal()).isEqualTo(0);
    assertThat(result.getArranged()).isEqualTo(0);
  }

  @Test
  public void skip() {
    testResourcesPath =
        CodeGenerationUtils.mavenModuleRoot(getClass()).resolve("src/test/resources/general");
    targetFileName = "ArrangePlz";
    _backup();
    final ArrangementResult result =
        JarrangerMojo.builder()
            .log(new SystemStreamLog())
            .sourceDirectory(testResourcesPath.toFile())
            .skip(true)
            .build()
            .arrange();
    assertThat(result.getTotal()).isEqualTo(0);
    assertThat(result.getArranged()).isEqualTo(0);
  }
}
