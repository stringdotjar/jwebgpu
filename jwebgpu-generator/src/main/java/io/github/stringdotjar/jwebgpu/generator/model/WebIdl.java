/*
 * MIT License
 *
 * Copyright (c) 2026 stringdotjar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.stringdotjar.jwebgpu.generator.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The generator's model of the browser's WebGPU API, read from the vendored WebIDL.
 *
 * <p>Where {@link Spec} describes the C ABI of {@code webgpu.h}, this class describes what the browser exposes:
 * interfaces with attributes and operations, dictionaries (the browser's descriptors), enums (strings), and the
 * namespaces that hold the bit flag constants. Partial interfaces and mixins are already merged, so
 * {@link #iface(String)} returns everything an object has.
 *
 * <p>Example:
 *
 * <pre>{@code
 * WebIdl idl = WebIdlParser.load(Path.of("spec/webgpu.idl"));
 * Interface device = idl.iface("GPUDevice");
 * Operation create = device.operation("createBuffer");
 * }</pre>
 */
public final class WebIdl {

  private final Map<String, Interface> interfaces;
  private final Map<String, Dictionary> dictionaries;
  private final Map<String, Enumeration> enums;
  private final Map<String, Namespace> namespaces;
  private final Map<String, Type> typedefs;

  /**
   * Creates the model from merged definitions. Use the parser instead of calling this yourself.
   *
   * @param interfaces The interfaces by name, with partials and mixins merged in.
   * @param dictionaries The dictionaries by name.
   * @param enums The enums by name.
   * @param namespaces The namespaces by name.
   * @param typedefs The typedef targets by name.
   */
  public WebIdl(Map<String, Interface> interfaces, Map<String, Dictionary> dictionaries, Map<String, Enumeration> enums,
      Map<String, Namespace> namespaces, Map<String, Type> typedefs) {
    this.interfaces = new LinkedHashMap<>(interfaces);
    this.dictionaries = new LinkedHashMap<>(dictionaries);
    this.enums = new LinkedHashMap<>(enums);
    this.namespaces = new LinkedHashMap<>(namespaces);
    this.typedefs = new LinkedHashMap<>(typedefs);
  }

  /**
   * Returns an interface with its partials and mixins merged in.
   *
   * @param name The IDL name, such as {@code GPUDevice}.
   * @return The interface.
   * @throws IllegalArgumentException If there is none with that name.
   */
  public Interface iface(String name) {
    return require(interfaces, "interface", name);
  }

  /**
   * Returns a dictionary.
   *
   * @param name The IDL name, such as {@code GPUBufferDescriptor}.
   * @return The dictionary.
   * @throws IllegalArgumentException If there is none with that name.
   */
  public Dictionary dictionary(String name) {
    return require(dictionaries, "dictionary", name);
  }

  /**
   * Returns an enum.
   *
   * @param name The IDL name, such as {@code GPUTextureFormat}.
   * @return The enum.
   * @throws IllegalArgumentException If there is none with that name.
   */
  public Enumeration enumType(String name) {
    return require(enums, "enum", name);
  }

  /**
   * Returns a namespace.
   *
   * @param name The IDL name, such as {@code GPUBufferUsage}.
   * @return The namespace.
   * @throws IllegalArgumentException If there is none with that name.
   */
  public Namespace namespace(String name) {
    return require(namespaces, "namespace", name);
  }

  /**
   * Finds an interface.
   *
   * @param name The IDL name.
   * @return The interface, or {@code null} if there is none.
   */
  public @Nullable Interface findInterface(String name) {
    return interfaces.get(name);
  }

  /**
   * Finds a dictionary.
   *
   * @param name The IDL name.
   * @return The dictionary, or {@code null} if there is none.
   */
  public @Nullable Dictionary findDictionary(String name) {
    return dictionaries.get(name);
  }

  /**
   * Finds an enum.
   *
   * @param name The IDL name.
   * @return The enum, or {@code null} if there is none.
   */
  public @Nullable Enumeration findEnum(String name) {
    return enums.get(name);
  }

  /**
   * Finds a namespace.
   *
   * @param name The IDL name.
   * @return The namespace, or {@code null} if there is none.
   */
  public @Nullable Namespace findNamespace(String name) {
    return namespaces.get(name);
  }

  /**
   * Follows typedefs until the type is not an alias any more.
   *
   * @param type A type.
   * @return The type itself, or what the typedef chain leads to. The nullable flag of the alias is kept.
   */
  public Type resolve(Type type) {
    Type t = type;
    int guard = 0;
    while (t.kind() == Type.Kind.NAMED && typedefs.containsKey(t.name()) && guard++ < 32) {
      Type target = typedefs.get(t.name());
      t = t.nullable() && !target.nullable() ? target.asNullable() : target;
    }
    return t;
  }

  /**
   * Returns the members of a dictionary including the ones it inherits, parents first.
   *
   * @param name The dictionary name.
   * @return The members in IDL order.
   */
  public List<DictionaryMember> allMembers(String name) {
    List<DictionaryMember> out = new ArrayList<>();
    collect(dictionary(name), out);
    return out;
  }

  /**
   * Returns every interface.
   *
   * @return The interfaces in declaration order.
   */
  public List<Interface> interfaces() {
    return List.copyOf(interfaces.values());
  }

  /**
   * Returns every dictionary.
   *
   * @return The dictionaries in declaration order.
   */
  public List<Dictionary> dictionaries() {
    return List.copyOf(dictionaries.values());
  }

  /**
   * Returns every enum.
   *
   * @return The enums in declaration order.
   */
  public List<Enumeration> enums() {
    return List.copyOf(enums.values());
  }

  /**
   * Returns every namespace.
   *
   * @return The namespaces in declaration order.
   */
  public List<Namespace> namespaces() {
    return List.copyOf(namespaces.values());
  }

  private void collect(Dictionary d, List<DictionaryMember> out) {
    if (d.parent() != null && dictionaries.containsKey(d.parent())) {
      collect(dictionaries.get(d.parent()), out);
    }
    out.addAll(d.members());
  }

  private static <T> T require(Map<String, T> map, String kind, String name) {
    T value = map.get(name);
    if (value == null) {
      throw new IllegalArgumentException("no " + kind + " named " + name + " in the WebIDL");
    }
    return value;
  }

  /**
   * A type of the IDL.
   *
   * @param kind What kind of type this is.
   * @param name The name for a {@link Kind#NAMED} type, such as {@code unsigned long} or {@code GPUBuffer}.
   * @param args The type arguments of a generic type, or the members of a union.
   * @param nullable Whether the type ends in {@code ?}.
   */
  public record Type(@NotNull Kind kind, @NotNull String name, @NotNull List<Type> args, boolean nullable) {

    /** Creates a record. */
    public Type {
      args = List.copyOf(args);
    }

    /**
     * Creates a plain named type.
     *
     * @param name The name.
     * @return The type.
     */
    public static Type named(String name) {
      return new Type(Kind.NAMED, name, List.of(), false);
    }

    /**
     * Returns the same type, marked as nullable.
     *
     * @return The nullable type.
     */
    public Type asNullable() {
      return new Type(kind, name, args, true);
    }

    /**
     * Tells whether this is the named type.
     *
     * @param n A name such as {@code undefined}.
     * @return {@code true} for a plain named type with that name.
     */
    public boolean is(String n) {
      return kind == Kind.NAMED && name.equals(n);
    }

    @Override
    public @NotNull String toString() {
      String base = switch (kind) {
        case NAMED -> name;
        case SEQUENCE -> "sequence<" + args.get(0) + ">";
        case FROZEN_ARRAY -> "FrozenArray<" + args.get(0) + ">";
        case PROMISE -> "Promise<" + args.get(0) + ">";
        case RECORD -> "record<" + args.get(0) + ", " + args.get(1) + ">";
        case UNION -> {
          StringBuilder sb = new StringBuilder("(");
          for (int i = 0; i < args.size(); i++) {
            sb.append(i == 0 ? "" : " or ").append(args.get(i));
          }
          yield sb.append(')').toString();
        }
      };
      return nullable ? base + "?" : base;
    }

    /** The kinds of types. */
    public enum Kind {
      /** A name: a primitive, an interface, a dictionary, an enum or a typedef. */
      NAMED,
      /** A {@code sequence<T>}. */
      SEQUENCE,
      /** A {@code FrozenArray<T>}. */
      FROZEN_ARRAY,
      /** A {@code Promise<T>}. */
      PROMISE,
      /** A {@code record<K, V>}. */
      RECORD,
      /** A union such as {@code (A or B)}. */
      UNION
    }
  }

  /**
   * An argument of an operation.
   *
   * @param name The argument name.
   * @param type The argument type.
   * @param optional Whether the argument may be left out.
   * @param variadic Whether the argument is {@code ...}.
   * @param defaultValue The default value as written, or {@code null} if there is none.
   */
  public record Argument(@NotNull String name, @NotNull Type type, boolean optional, boolean variadic,
      @Nullable String defaultValue) {
  }

  /**
   * An operation (method) of an interface.
   *
   * @param name The operation name.
   * @param returns The return type.
   * @param args The arguments in order.
   * @param isStatic Whether the operation is static.
   */
  public record Operation(@NotNull String name, @NotNull Type returns, @NotNull List<Argument> args,
      boolean isStatic) {

    /** Creates a record. */
    public Operation {
      args = List.copyOf(args);
    }

    /**
     * Counts the arguments that must be passed.
     *
     * @return The number of arguments before the first optional one.
     */
    public int requiredCount() {
      int n = 0;
      for (Argument a : args) {
        if (a.optional()) {
          break;
        }
        n++;
      }
      return n;
    }
  }

  /**
   * An attribute of an interface.
   *
   * @param name The attribute name.
   * @param type The attribute type.
   * @param readonly Whether the attribute is read-only.
   */
  public record Attribute(@NotNull String name, @NotNull Type type, boolean readonly) {
  }

  /**
   * A constant of an interface or a namespace.
   *
   * @param name The constant name.
   * @param type The constant type.
   * @param value The value as written, such as {@code 0x0010}.
   */
  public record Constant(@NotNull String name, @NotNull Type type, @NotNull String value) {

    /**
     * Parses the value as a number.
     *
     * @return The value.
     */
    public long number() {
      String v = value.trim();
      return v.startsWith("0x") || v.startsWith("0X") ? Long.parseLong(v.substring(2), 16) : Long.parseLong(v);
    }
  }

  /**
   * An interface with partials and mixins merged in.
   *
   * @param name The interface name.
   * @param parent The parent interface name, or {@code null}.
   * @param operations The operations, including those of mixins.
   * @param attributes The attributes, including those of mixins.
   * @param constants The constants.
   * @param setLike Whether the interface is a read-only set of strings ({@code setlike<DOMString>}).
   */
  public record Interface(@NotNull String name, @Nullable String parent, @NotNull List<Operation> operations,
      @NotNull List<Attribute> attributes, @NotNull List<Constant> constants, boolean setLike) {

    /** Creates a record. */
    public Interface {
      operations = List.copyOf(operations);
      attributes = List.copyOf(attributes);
      constants = List.copyOf(constants);
    }

    /**
     * Finds the operations with a name. Overloads share a name.
     *
     * @param n The operation name.
     * @return The matching operations in declaration order.
     */
    public List<Operation> overloads(String n) {
      List<Operation> out = new ArrayList<>();
      for (Operation o : operations) {
        if (o.name().equals(n)) {
          out.add(o);
        }
      }
      return out;
    }

    /**
     * Finds the first operation with a name.
     *
     * @param n The operation name.
     * @return The operation, or {@code null}.
     */
    public @Nullable Operation operation(String n) {
      List<Operation> all = overloads(n);
      return all.isEmpty() ? null : all.get(0);
    }

    /**
     * Finds an attribute.
     *
     * @param n The attribute name.
     * @return The attribute, or {@code null}.
     */
    public @Nullable Attribute attribute(String n) {
      for (Attribute a : attributes) {
        if (a.name().equals(n)) {
          return a;
        }
      }
      return null;
    }
  }

  /**
   * A dictionary member.
   *
   * @param name The member name.
   * @param type The member type.
   * @param required Whether the member is {@code required}.
   * @param defaultValue The default as written, or {@code null} if there is none.
   */
  public record DictionaryMember(@NotNull String name, @NotNull Type type, boolean required,
      @Nullable String defaultValue) {
  }

  /**
   * A dictionary, which is how the browser takes descriptors.
   *
   * @param name The dictionary name.
   * @param parent The parent dictionary name, or {@code null}.
   * @param members The members declared by this dictionary (not the inherited ones).
   */
  public record Dictionary(@NotNull String name, @Nullable String parent, @NotNull List<DictionaryMember> members) {

    /** Creates a record. */
    public Dictionary {
      members = List.copyOf(members);
    }
  }

  /**
   * An enum, which is a set of strings in the browser.
   *
   * @param name The enum name.
   * @param values The allowed strings in declaration order.
   */
  public record Enumeration(@NotNull String name, @NotNull List<String> values) {

    /** Creates a record. */
    public Enumeration {
      values = List.copyOf(values);
    }
  }

  /**
   * A namespace, which holds the bit flag constants of one flag type.
   *
   * @param name The namespace name.
   * @param constants The constants.
   */
  public record Namespace(@NotNull String name, @NotNull List<Constant> constants) {

    /** Creates a record. */
    public Namespace {
      constants = List.copyOf(constants);
    }
  }
}
