package gov.va.jarranger;

import com.github.javaparser.utils.CodeGenerationUtils;
import org.apache.maven.plugin.logging.SystemStreamLog;

public final class YoDawg {
  /** Run the arranger on its own source. */
  public static void main(String[] args) {
    JarrangerMojo.builder()
        .log(new SystemStreamLog())
        .sourceDirectory(
            CodeGenerationUtils.mavenModuleRoot(YoDawg.class).resolve("src/main/java").toFile())
        .testSourceDirectory(
            CodeGenerationUtils.mavenModuleRoot(YoDawg.class).resolve("src/test/java").toFile())
        .build()
        .execute();
  }
}
