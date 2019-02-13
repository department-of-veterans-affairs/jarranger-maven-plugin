package gov.va.jarranger;

import static com.google.common.base.Preconditions.checkState;

import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.AnnotationMemberDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.InitializerDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.nodeTypes.modifiers.NodeWithStaticModifier;
import com.github.javaparser.ast.type.Type;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/**
 * Compute the arrangement of members for a {@link TypeDeclaration}. The arrangement is shallow; the
 * internals of members that have their own members (e.g. inner classes) are not analyzed. The input
 * is not modified; the new arrangement is stored in the {@link #arrangedNodes} field.
 */
final class ShallowArranger {
  private final List<AnnotationMemberDeclaration> annotationMembers = new ArrayList<>();

  private final List<ConstructorDeclaration> constructors = new ArrayList<>();

  private final ByStatic<MethodDeclaration> methods = new ByStatic<>();

  private final List<EnumConstantDeclaration> enumConstants = new ArrayList<>();

  private final FieldsByStaticFinalVisibility fields = new FieldsByStaticFinalVisibility();

  private final ByStatic<InitializerDeclaration> initializers = new ByStatic<>();

  private final ClassesInterfacesAnnotations classesInterfacesAnnotations =
      new ClassesInterfacesAnnotations();

  private final List<EnumDeclaration> enums = new ArrayList<>();

  /** Output field that contains the new arrangement. */
  @Getter private final List<BodyDeclaration<?>> arrangedNodes = new ArrayList<>();

  ShallowArranger(final TypeDeclaration<?> typeDec) {
    super();

    for (final BodyDeclaration<?> member : typeDec.getMembers()) {
      addBody(member);
    }

    sortMethods(methods.statics);
    sortMethods(methods.nonStatics);
    sortMethods(annotationMembers);

    // Build the arranged list.
    // Enum constant declarations must be first.
    arrangedNodes.addAll(enumConstants);

    // Add fields and initializers.
    if (isInterfaceOrAnnotation(typeDec)) {
      // Do not sort fields for interfaces or annotations.
      // Everything is treated as static.
      arrangedNodes.addAll(fields.unsorted);
      // Initializers are not valid inside interfaces.
      // Include defensively.
      arrangedNodes.addAll(initializers.unsorted);
    } else {
      arrangedNodes.addAll(fields.staticFinals.arranged());
      arrangedNodes.addAll(fields.statics.arranged());
      arrangedNodes.addAll(initializers.statics);
      arrangedNodes.addAll(fields.finals.arranged());
      arrangedNodes.addAll(fields.plains.arranged());
      arrangedNodes.addAll(initializers.nonStatics);
    }

    // Add constructors and methods.
    arrangedNodes.addAll(constructors);
    arrangedNodes.addAll(methods.arranged());
    arrangedNodes.addAll(annotationMembers);

    // Add enums, interfaces, and classes.
    arrangedNodes.addAll(enums);
    arrangedNodes.addAll(classesInterfacesAnnotations.interfacesAndAnnotations);
    if (isInterfaceOrAnnotation(typeDec)) {
      arrangedNodes.addAll(classesInterfacesAnnotations.classes.unsorted);
    } else {
      arrangedNodes.addAll(classesInterfacesAnnotations.classes.arranged());
    }
  }

  private static <T extends NodeWithSimpleName<?>> void groupGettersAndSetters(
      final List<T> nodes) {
    final Map<String, T> getters = new HashMap<>();
    final Map<String, T> gettersBoolean = new HashMap<>();
    // A getter may have several setters, each with one parameter
    final ListMultimap<String, T> setters = ArrayListMultimap.create();

    for (final T node : nodes) {
      final String name = node.getNameAsString();
      if (isGetter(node)) {
        final String key = name.substring("get".length());
        checkState(!getters.containsKey(key));
        getters.put(key, node);
      } else if (isGetterBoolean(node)) {
        final String key = name.substring("is".length());
        checkState(!gettersBoolean.containsKey(key));
        gettersBoolean.put(key, node);
      } else if (isSetter(node)) {
        setters.put(name.substring("set".length()), node);
      }
    }

    for (final Map.Entry<String, Collection<T>> settersEntry : setters.asMap().entrySet()) {
      final String key = settersEntry.getKey();
      final T getter = getters.getOrDefault(key, gettersBoolean.get(key));
      if (getter == null) {
        continue;
      }
      // Original setter order will be reversed as they are inserted here.
      // Use a reversed list to preserve the original order.
      for (final T setter : Lists.reverse(ImmutableList.copyOf(settersEntry.getValue()))) {
        final boolean removed = nodes.remove(setter);
        checkState(removed);
        final int getterIndex = nodes.indexOf(getter);
        checkState(getterIndex >= 0);
        nodes.add(getterIndex + 1, setter);
      }
    }
  }

  private static <T extends NodeWithSimpleName<?>> void groupOverloadedMethods(
      final List<T> nodes) {
    final ListMultimap<String, T> byName = ArrayListMultimap.create();
    for (final T node : nodes) {
      byName.put(node.getNameAsString(), node);
    }
    for (final Map.Entry<String, Collection<T>> entry : byName.asMap().entrySet()) {
      if (entry.getValue().size() <= 1) {
        continue;
      }

      final List<T> list = ImmutableList.copyOf(entry.getValue());
      for (int i = 1; i < list.size(); i++) {
        final int prevIndex = nodes.indexOf(list.get(i - 1));
        final T node = list.get(i);
        final int index = nodes.indexOf(node);
        checkState(index > prevIndex);
        if (index - prevIndex == 1) {
          continue;
        }

        final boolean removed = nodes.remove(node);
        checkState(removed);
        nodes.add(prevIndex + 1, node);
      }
    }
  }

  private static boolean hasPrefix(final String prefix, final String name) {
    return name.startsWith(prefix)
        && name.length() > prefix.length()
        && (name.contains("$") || name.contains("_") || !name.toLowerCase(Locale.US).equals(name));
  }

  private static boolean isGetter(final NodeWithSimpleName<?> node) {
    // Getter can return anything except *primitive* void.
    return hasPrefix("get", node.getNameAsString())
        && parameters(node).isEmpty()
        && methodType(node) != null
        && !methodType(node).toString().equals("void");
  }

  private static boolean isGetterBoolean(final NodeWithSimpleName<?> node) {
    // Boolean getter can return primitive or Boolean object.
    return hasPrefix("is", node.getNameAsString())
        && parameters(node).isEmpty()
        && methodType(node) != null
        && methodType(node).toString().equalsIgnoreCase("boolean");
  }

  private static boolean isInterfaceOrAnnotation(final TypeDeclaration<?> typeDec) {
    if (typeDec instanceof ClassOrInterfaceDeclaration) {
      return ((ClassOrInterfaceDeclaration) typeDec).isInterface();
    }
    if (typeDec instanceof AnnotationDeclaration) {
      return true;
    }
    return false;
  }

  private static boolean isSetter(final NodeWithSimpleName<?> node) {
    // Setter must return *primitive* void.
    return hasPrefix("set", node.getNameAsString())
        && parameters(node).size() == 1
        && methodType(node) != null
        && methodType(node).toString().equals("void");
  }

  private static Type methodType(final Object maybeMethod) {
    if (maybeMethod instanceof MethodDeclaration) {
      return ((MethodDeclaration) maybeMethod).getType();
    }
    return null;
  }

  private static NodeList<Parameter> parameters(final Object callableMaybe) {
    if (callableMaybe instanceof CallableDeclaration<?>) {
      return ((CallableDeclaration<?>) callableMaybe).getParameters();
    }
    return new NodeList<>();
  }

  private static <T extends NodeWithSimpleName<?>> void sortMethods(final List<T> nodes) {
    final Comparator<T> nameComparator =
        (left, right) -> left.getNameAsString().compareTo(right.getNameAsString());
    nodes.sort(nameComparator);
    groupGettersAndSetters(nodes);
    groupOverloadedMethods(nodes);
  }

  private void addBody(final BodyDeclaration<?> member) {
    if (member instanceof AnnotationMemberDeclaration) {
      annotationMembers.add((AnnotationMemberDeclaration) member);
    } else if (member instanceof ConstructorDeclaration) {
      constructors.add((ConstructorDeclaration) member);
    } else if (member instanceof MethodDeclaration) {
      methods.add((MethodDeclaration) member);
    } else if (member instanceof EnumConstantDeclaration) {
      enumConstants.add((EnumConstantDeclaration) member);
    } else if (member instanceof FieldDeclaration) {
      fields.add((FieldDeclaration) member);
    } else if (member instanceof InitializerDeclaration) {
      initializers.add((InitializerDeclaration) member);
    } else if (member instanceof AnnotationDeclaration) {
      classesInterfacesAnnotations.add((AnnotationDeclaration) member);
    } else if (member instanceof ClassOrInterfaceDeclaration) {
      classesInterfacesAnnotations.add((ClassOrInterfaceDeclaration) member);
    } else if (member instanceof EnumDeclaration) {
      enums.add((EnumDeclaration) member);
    } else {
      throw new IllegalStateException("Cannot arrange unknown class " + member.getClass());
    }
  }

  private static final class ByStatic<T extends BodyDeclaration<?>> {
    private final List<T> unsorted = new ArrayList<>();

    private final List<T> statics = new ArrayList<>();

    private final List<T> nonStatics = new ArrayList<>();

    void add(final T member) {
      unsorted.add(member);
      if (isStatic(member)) {
        statics.add(member);
      } else {
        nonStatics.add(member);
      }
    }

    List<T> arranged() {
      return ImmutableList.copyOf(Iterables.concat(statics, nonStatics));
    }

    private boolean isStatic(final T member) {
      if (member instanceof NodeWithStaticModifier<?>) {
        return ((NodeWithStaticModifier<?>) member).isStatic();
      }
      if (member instanceof InitializerDeclaration) {
        return ((InitializerDeclaration) member).isStatic();
      }
      throw new IllegalArgumentException(
          "Cannot determine if " + member.getClass() + " is static.");
    }
  }

  private static final class ClassesInterfacesAnnotations {
    private final ByStatic<ClassOrInterfaceDeclaration> classes = new ByStatic<>();

    private final List<TypeDeclaration<?>> interfacesAndAnnotations = new ArrayList<>();

    void add(final ClassOrInterfaceDeclaration classOrInterface) {
      if (classOrInterface.isInterface()) {
        interfacesAndAnnotations.add(classOrInterface);
      } else {
        classes.add(classOrInterface);
      }
    }

    void add(final AnnotationDeclaration annotation) {
      interfacesAndAnnotations.add(annotation);
    }
  }

  private static final class FieldsByStaticFinalVisibility {
    private final List<FieldDeclaration> unsorted = new ArrayList<>();

    private final FieldsByVisibility staticFinals = new FieldsByVisibility();

    private final FieldsByVisibility statics = new FieldsByVisibility();

    private final FieldsByVisibility finals = new FieldsByVisibility();

    private final FieldsByVisibility plains = new FieldsByVisibility();

    void add(final FieldDeclaration field) {
      unsorted.add(field);

      if (field.isStatic()) {
        if (field.isFinal()) {
          staticFinals.add(field);
        } else {
          statics.add(field);
        }
      } else {
        if (field.isFinal()) {
          finals.add(field);
        } else {
          plains.add(field);
        }
      }
    }
  }

  private static final class FieldsByVisibility {
    private final List<FieldDeclaration> publics = new ArrayList<>();

    private final List<FieldDeclaration> protecteds = new ArrayList<>();

    private final List<FieldDeclaration> packagePrivates = new ArrayList<>();

    private final List<FieldDeclaration> privates = new ArrayList<>();

    void add(final FieldDeclaration field) {
      if (field.isPublic()) {
        publics.add(field);
      } else if (field.isProtected()) {
        protecteds.add(field);
      } else if (field.isPrivate()) {
        privates.add(field);
      } else {
        packagePrivates.add(field);
      }
    }

    List<FieldDeclaration> arranged() {
      return ImmutableList.copyOf(Iterables.concat(publics, protecteds, packagePrivates, privates));
    }
  }
}
