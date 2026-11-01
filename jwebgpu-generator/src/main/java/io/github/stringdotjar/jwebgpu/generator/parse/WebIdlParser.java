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
package io.github.stringdotjar.jwebgpu.generator.parse;

import io.github.stringdotjar.jwebgpu.generator.model.WebIdl;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Argument;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Attribute;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Constant;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Dictionary;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.DictionaryMember;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Enumeration;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Interface;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Namespace;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Operation;
import io.github.stringdotjar.jwebgpu.generator.model.WebIdl.Type;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses the vendored WebGPU WebIDL into a {@link WebIdl}.
 *
 * <p>This is a small hand-written parser with no dependencies. It understands the part of WebIDL that the WebGPU
 * specification uses: interfaces, mixins, {@code includes} statements, partial definitions, dictionaries, enums,
 * namespaces, typedefs, extended attributes (which are skipped), unions, generics, nullable types and default values.
 * Anything else, such as a feature this specification does not use yet, is reported with the line number, because a
 * silently wrong binding is worse than a build error.
 *
 * <p>Example:
 *
 * <pre>{@code
 * WebIdl idl = WebIdlParser.parse("enum GPUFilterMode { \"nearest\", \"linear\" };");
 * List<String> modes = idl.enumType("GPUFilterMode").values();
 * }</pre>
 */
public final class WebIdlParser {

  private final List<Token> tokens;
  private int pos;

  private final Map<String, Builder> interfaces = new LinkedHashMap<>();
  private final Map<String, Builder> mixins = new LinkedHashMap<>();
  private final Map<String, List<String>> includes = new LinkedHashMap<>();
  private final Map<String, DictionaryBuilder> dictionaries = new LinkedHashMap<>();
  private final Map<String, Enumeration> enums = new LinkedHashMap<>();
  private final Map<String, Namespace> namespaces = new LinkedHashMap<>();
  private final Map<String, Type> typedefs = new LinkedHashMap<>();

  private WebIdlParser(List<Token> tokens) {
    this.tokens = tokens;
  }

  /**
   * Reads and parses a WebIDL file.
   *
   * @param file The path of {@code webgpu.idl}.
   * @return The parsed model.
   * @throws UncheckedIOException If the file cannot be read.
   * @throws SpecException If the IDL contains something this parser does not understand.
   */
  public static WebIdl load(Path file) {
    try {
      return parse(Files.readString(file, StandardCharsets.UTF_8));
    } catch (IOException e) {
      throw new UncheckedIOException("Cannot read " + file, e);
    }
  }

  /**
   * Parses WebIDL text.
   *
   * @param text The IDL source.
   * @return The parsed model, with partial definitions and mixins merged into their interfaces.
   * @throws SpecException If the IDL contains something this parser does not understand.
   */
  public static WebIdl parse(String text) {
    WebIdlParser p = new WebIdlParser(tokenize(text));
    p.definitions();
    return p.build();
  }

  private void definitions() {
    while (!peek().is(Token.END)) {
      skipExtendedAttributes();
      Token t = next();
      switch (t.text) {
        case "interface" -> iface();
        case "partial" -> {
          Token k = next();
          switch (k.text) {
            case "interface" -> iface();
            case "dictionary" -> dictionary();
            case "namespace" -> namespace();
            default -> throw error(k, "unexpected '" + k.text + "' after partial");
          }
        }
        case "dictionary" -> dictionary();
        case "enum" -> enumeration();
        case "namespace" -> namespace();
        case "typedef" -> typedef();
        default -> {
          if (t.is(Token.IDENT) && peek().text.equals("includes")) {
            next();
            Token mixin = next();
            expect(";");
            includes.computeIfAbsent(t.text, k -> new ArrayList<>()).add(mixin.text);
          } else {
            throw error(t, "unexpected '" + t.text + "'");
          }
        }
      }
    }
  }

  private void iface() {
    boolean mixin = false;
    if (peek().text.equals("mixin")) {
      next();
      mixin = true;
    }
    Token name = next();
    String parent = null;
    if (peek().text.equals(":")) {
      next();
      parent = next().text;
    }
    expect("{");
    Map<String, Builder> target = mixin ? mixins : interfaces;
    Builder b = target.computeIfAbsent(name.text, Builder::new);
    if (parent != null) {
      b.parent = parent;
    }
    while (!peek().text.equals("}")) {
      member(b);
    }
    expect("}");
    expect(";");
  }

  private void member(Builder b) {
    skipExtendedAttributes();
    Token t = peek();
    if (t.text.equals("constructor")) {
      next();
      arguments();
      expect(";");
      return;
    }
    if (t.text.equals("const")) {
      next();
      Type type = type();
      String name = next().text;
      expect("=");
      String value = next().text;
      expect(";");
      b.constants.add(new Constant(name, type, value));
      return;
    }
    boolean isStatic = false;
    if (t.text.equals("static")) {
      next();
      isStatic = true;
    }
    boolean readonly = false;
    if (peek().text.equals("readonly")) {
      next();
      readonly = true;
    }
    if (peek().text.equals("setlike")) {
      next();
      expect("<");
      type();
      expect(">");
      expect(";");
      b.setLike = true;
      return;
    }
    if (peek().text.equals("attribute")) {
      next();
      Type type = type();
      String name = next().text;
      expect(";");
      b.attributes.add(new Attribute(name, type, readonly));
      return;
    }
    if (readonly) {
      throw error(peek(), "unexpected '" + peek().text + "' after readonly");
    }
    Type returns = type();
    String name = next().text;
    List<Argument> args = arguments();
    expect(";");
    b.operations.add(new Operation(name, returns, args, isStatic));
  }

  private List<Argument> arguments() {
    expect("(");
    List<Argument> out = new ArrayList<>();
    while (!peek().text.equals(")")) {
      skipExtendedAttributes();
      boolean optional = false;
      if (peek().text.equals("optional")) {
        next();
        optional = true;
      }
      Type type = type();
      boolean variadic = false;
      if (peek().text.equals("...")) {
        next();
        variadic = true;
      }
      String name = next().text;
      String def = null;
      if (peek().text.equals("=")) {
        next();
        def = defaultValue();
      }
      out.add(new Argument(name, type, optional, variadic, def));
      if (peek().text.equals(",")) {
        next();
      }
    }
    expect(")");
    return out;
  }

  private void dictionary() {
    Token name = next();
    String parent = null;
    if (peek().text.equals(":")) {
      next();
      parent = next().text;
    }
    expect("{");
    DictionaryBuilder d = dictionaries.computeIfAbsent(name.text, DictionaryBuilder::new);
    if (parent != null) {
      d.parent = parent;
    }
    while (!peek().text.equals("}")) {
      skipExtendedAttributes();
      boolean required = false;
      if (peek().text.equals("required")) {
        next();
        required = true;
      }
      Type type = type();
      String memberName = next().text;
      String def = null;
      if (peek().text.equals("=")) {
        next();
        def = defaultValue();
      }
      expect(";");
      d.members.add(new DictionaryMember(memberName, type, required, def));
    }
    expect("}");
    expect(";");
  }

  private void enumeration() {
    Token name = next();
    expect("{");
    List<String> values = new ArrayList<>();
    while (!peek().text.equals("}")) {
      Token v = next();
      if (!v.is(Token.STRING)) {
        throw error(v, "expected a string in enum " + name.text);
      }
      values.add(v.text);
      if (peek().text.equals(",")) {
        next();
      }
    }
    expect("}");
    expect(";");
    enums.put(name.text, new Enumeration(name.text, values));
  }

  private void namespace() {
    Token name = next();
    expect("{");
    Builder b = new Builder(name.text);
    while (!peek().text.equals("}")) {
      member(b);
    }
    expect("}");
    expect(";");
    namespaces.put(name.text, new Namespace(name.text, b.constants));
  }

  private void typedef() {
    skipExtendedAttributes();
    Type type = type();
    Token name = next();
    expect(";");
    typedefs.put(name.text, type);
  }

  private Type type() {
    skipExtendedAttributes();
    Type base;
    if (peek().text.equals("(")) {
      next();
      List<Type> members = new ArrayList<>();
      members.add(type());
      while (peek().text.equals("or")) {
        next();
        members.add(type());
      }
      expect(")");
      base = new Type(Type.Kind.UNION, "", members, false);
    } else {
      Token t = next();
      if (!t.is(Token.IDENT)) {
        throw error(t, "expected a type but found '" + t.text + "'");
      }
      switch (t.text) {
        case "sequence", "FrozenArray", "Promise", "ObservableArray" -> {
          expect("<");
          Type inner = type();
          expect(">");
          Type.Kind kind = switch (t.text) {
            case "sequence" -> Type.Kind.SEQUENCE;
            case "Promise" -> Type.Kind.PROMISE;
            default -> Type.Kind.FROZEN_ARRAY;
          };
          base = new Type(kind, t.text, List.of(inner), false);
        }
        case "record" -> {
          expect("<");
          Type key = type();
          expect(",");
          Type value = type();
          expect(">");
          base = new Type(Type.Kind.RECORD, t.text, List.of(key, value), false);
        }
        default -> base = Type.named(multiWord(t.text));
      }
    }
    if (peek().text.equals("?")) {
      next();
      return base.asNullable();
    }
    return base;
  }

  private String multiWord(String first) {
    switch (first) {
      case "unsigned" -> {
        String n = next().text;
        if (n.equals("long") && peek().text.equals("long")) {
          next();
          return "unsigned long long";
        }
        return "unsigned " + n;
      }
      case "unrestricted" -> {
        return "unrestricted " + next().text;
      }
      case "long" -> {
        if (peek().text.equals("long")) {
          next();
          return "long long";
        }
        return "long";
      }
      default -> {
        return first;
      }
    }
  }

  private String defaultValue() {
    Token t = next();
    switch (t.text) {
      case "[" -> {
        expect("]");
        return "[]";
      }
      case "{" -> {
        expect("}");
        return "{}";
      }
      default -> {
        return t.is(Token.STRING) ? "\"" + t.text + "\"" : t.text;
      }
    }
  }

  private void skipExtendedAttributes() {
    while (peek().text.equals("[") && peek().is(Token.SYMBOL)) {
      int depth = 0;
      do {
        Token t = next();
        if (t.is(Token.END)) {
          throw error(t, "unterminated extended attribute");
        }
        if (t.is(Token.SYMBOL) && (t.text.equals("[") || t.text.equals("(") || t.text.equals("{"))) {
          depth++;
        } else if (t.is(Token.SYMBOL) && (t.text.equals("]") || t.text.equals(")") || t.text.equals("}"))) {
          depth--;
        }
      } while (depth > 0);
    }
  }

  private Token peek() {
    return tokens.get(pos);
  }

  private Token next() {
    Token t = tokens.get(pos);
    if (pos < tokens.size() - 1) {
      pos++;
    }
    return t;
  }

  private void expect(String text) {
    Token t = next();
    if (!t.text.equals(text) || t.is(Token.STRING)) {
      throw error(t, "expected '" + text + "' but found '" + t.text + "'");
    }
  }

  private static SpecException error(Token t, String message) {
    return new SpecException("WebIDL line " + t.line + ": " + message);
  }

  private WebIdl build() {
    Map<String, Interface> out = new LinkedHashMap<>();
    for (Builder b : interfaces.values()) {
      List<Operation> ops = new ArrayList<>(b.operations);
      List<Attribute> attrs = new ArrayList<>(b.attributes);
      for (String m : includes.getOrDefault(b.name, List.of())) {
        Builder mixin = mixins.get(m);
        if (mixin == null) {
          throw new SpecException("WebIDL: " + b.name + " includes unknown mixin " + m);
        }
        ops.addAll(mixin.operations);
        attrs.addAll(mixin.attributes);
      }
      out.put(b.name, new Interface(b.name, b.parent, ops, attrs, b.constants, b.setLike));
    }
    Map<String, Dictionary> dicts = new LinkedHashMap<>();
    for (DictionaryBuilder d : dictionaries.values()) {
      dicts.put(d.name, new Dictionary(d.name, d.parent, d.members));
    }
    return new WebIdl(out, dicts, enums, namespaces, typedefs);
  }

  private static List<Token> tokenize(String text) {
    List<Token> out = new ArrayList<>();
    int line = 1;
    int i = 0;
    int n = text.length();
    while (i < n) {
      char c = text.charAt(i);
      if (c == '\n') {
        line++;
        i++;
      } else if (Character.isWhitespace(c)) {
        i++;
      } else if (text.startsWith("//", i)) {
        while (i < n && text.charAt(i) != '\n') {
          i++;
        }
      } else if (text.startsWith("/*", i)) {
        int end = text.indexOf("*/", i + 2);
        if (end < 0) {
          throw new SpecException("WebIDL line " + line + ": unterminated comment");
        }
        for (int k = i; k < end; k++) {
          if (text.charAt(k) == '\n') {
            line++;
          }
        }
        i = end + 2;
      } else if (c == '"') {
        int end = text.indexOf('"', i + 1);
        if (end < 0) {
          throw new SpecException("WebIDL line " + line + ": unterminated string");
        }
        out.add(new Token(Token.STRING, text.substring(i + 1, end), line));
        i = end + 1;
      } else if (Character.isLetter(c) || c == '_') {
        int j = i + 1;
        while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '_')) {
          j++;
        }
        out.add(new Token(Token.IDENT, text.substring(i, j), line));
        i = j;
      } else if (Character.isDigit(c) || c == '-' && i + 1 < n && (Character.isDigit(text.charAt(i + 1))
          || text.startsWith("Infinity", i + 1))) {
        int j = i + 1;
        while (j < n && (Character.isLetterOrDigit(text.charAt(j)) || text.charAt(j) == '.')) {
          j++;
        }
        out.add(new Token(Token.NUMBER, text.substring(i, j), line));
        i = j;
      } else if (text.startsWith("...", i)) {
        out.add(new Token(Token.SYMBOL, "...", line));
        i += 3;
      } else {
        out.add(new Token(Token.SYMBOL, String.valueOf(c), line));
        i++;
      }
    }
    out.add(new Token(Token.END, "<end>", line));
    return out;
  }

  private record Token(int kind, String text, int line) {

    static final int IDENT = 0;
    static final int NUMBER = 1;
    static final int STRING = 2;
    static final int SYMBOL = 3;
    static final int END = 4;

    boolean is(int k) {
      return kind == k;
    }
  }

  private static final class Builder {

    final String name;
    String parent;
    boolean setLike;
    final List<Operation> operations = new ArrayList<>();
    final List<Attribute> attributes = new ArrayList<>();
    final List<Constant> constants = new ArrayList<>();

    Builder(String name) {
      this.name = name;
    }
  }

  private static final class DictionaryBuilder {

    final String name;
    String parent;
    final List<DictionaryMember> members = new ArrayList<>();

    DictionaryBuilder(String name) {
      this.name = name;
    }
  }
}
