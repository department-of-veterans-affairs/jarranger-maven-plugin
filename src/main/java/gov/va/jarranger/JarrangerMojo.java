package gov.va.jarranger;

import java.io.File;
import java.util.Map;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

@NoArgsConstructor
@Mojo(name = "arrange")
final class JarrangerMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project.packaging}", required = true)
  private String packaging;

  @Parameter(
    defaultValue = "${project.build.sourceDirectory}",
    property = "sourceDirectory",
    required = true
  )
  private File sourceDirectory;

  @Parameter(
    defaultValue = "${project.build.testSourceDirectory}",
    property = "testSourceDirectory",
    required = true
  )
  private File testSourceDirectory;

  @Parameter(defaultValue = "false", property = "jarranger.skip")
  private boolean skip = false;

  @Builder
  private JarrangerMojo(
      final Log log,
      final Map<?, ?> pluginContext,
      final String packaging,
      final File sourceDirectory,
      final File testSourceDirectory,
      final boolean skip) {
    super();
    setLog(log);
    setPluginContext(pluginContext);
    this.packaging = packaging;
    this.sourceDirectory = sourceDirectory;
    this.testSourceDirectory = testSourceDirectory;
    this.skip = skip;
  }

  Jarranger.ArrangementResult arrange() {
    if (skip) {
      getLog().info("Skipping arrangement because property 'jarranger.skip' is set.");
      return Jarranger.ArrangementResult.EMPTY;
    }

    if (packaging != null && packaging.equals("pom")) {
      getLog().info("Skipping arrangement because project uses 'pom' packaging.");
      return Jarranger.ArrangementResult.EMPTY;
    }

    final Jarranger arranger = Jarranger.builder().log(getLog()).build();
    Jarranger.ArrangementResult result = Jarranger.ArrangementResult.EMPTY;

    if (sourceDirectory != null && sourceDirectory.exists()) {
      result = result.add(arranger.arrange(sourceDirectory));
    } else {
      getLog().warn("Source directory '" + sourceDirectory + "' does not exist, ignoring.");
    }

    if (testSourceDirectory != null && testSourceDirectory.exists()) {
      result = result.add(arranger.arrange(testSourceDirectory));
    } else {
      getLog()
          .warn("Test source directory '" + testSourceDirectory + "' does not exist, ignoring.");
    }

    getLog()
        .info("Processed " + result.getTotal() + " files (" + result.getArranged() + " arranged).");
    return result;
  }

  @Override
  public void execute() {
    arrange();
  }
}
