## jarranger-maven-plugin

Automatic code arranger. This plugin mimics the default behavior of [IntelliJ IDEA's Java arranger](https://blog.jetbrains.com/idea/2012/10/arrange-your-code-automatically-with-intellij-idea-12/), with **order by name** enabled for **methods** and **static methods**.

Note that formatting **is not preserved**. You are advised to use this plugin in conjunction with an automatic code formatter, such as [fmt-maven-plugin](https://github.com/coveo/fmt-maven-plugin); formatting changes will then be limited to addition (or removal) of blank lines.

## Usage

To run with each build, add to pom:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>gov.va.jarranger</groupId>
      <artifactId>jarranger-maven-plugin</artifactId>
      <version>${jarranger.version}</version>
      <executions>
        <execution>
          <goals>
            <goal>arrange</goal>
          </goals>
          <phase>process-sources</phase>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

To use from command line (use `-D` for options):

`mvn gov.va.jarranger:jarranger-maven-plugin:arrange -Djarranger.skip -DsourceDirectory=some/source/dir`

### Options

`jarranger.skip` is whether the plugin should skip operation.

`sourceDirectory` is the directory of the Java sources to be arranged. Defaults to `${project.build.sourceDirectory}`.

`testSourceDirectory` is the directory of the test Java sources to be arranged. Defaults to `${project.build.testSourceDirectory}`.

example:

```xml
<build>
  <plugins>
    <plugin>
      <groupId>gov.va.jarranger</groupId>
      <artifactId>jarranger-maven-plugin</artifactId>
      <version>${jarranger.version}</version>
      <configuration>
        <sourceDirectory>your/source/dir</sourceDirectory>
        <testSourceDirectory>your/test/dir</testSourceDirectory>
        <jarranger.skip>false</jarranger.skip>
      </configuration>
      <executions>
        <execution>
          <goals>
            <goal>arrange</goal>
          </goals>
          <phase>process-sources</phase>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```
