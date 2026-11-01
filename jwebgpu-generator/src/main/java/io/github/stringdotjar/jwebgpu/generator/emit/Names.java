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
package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.Naming;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns names from the C API into names in the Java API.
 *
 * <p>The rules, in one place:
 *
 * <ul>
 *   <li>Every type gets the {@code Gpu} prefix and loses the {@code WGPU} prefix ({@code WGPUTextureFormat} becomes
 *       {@code GpuTextureFormat}).
 *   <li>Methods and fields are camelCase. A method loses its object prefix ({@code wgpuDeviceCreateBuffer} becomes
 *       {@code createBuffer} on {@code GpuDevice}).
 *   <li>Enum, bit flag and constant names are UPPER_SNAKE_CASE, taken from the specification name. A name that starts
 *       with a digit gets a {@code D} in front when it names a dimension (so {@code 2D_array} becomes
 *       {@code D2_ARRAY}), because a Java identifier cannot start with a digit.
 * </ul>
 */
final class Names {

  private static final String C_PREFIX = "WGPU";
  private static final String PACKAGE = "io.github.stringdotjar.jwebgpu";
  private static final Pattern DIMENSION = Pattern.compile("^([0-9]+)D(.*)$");
  private static final Pattern WORD_BREAK = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|_");
  private static final Set<String> KEYWORDS = Set.of(
      "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
      "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
      "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
      "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
      "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null", "_");

  private Names() {}

  /**
   * Returns the package that holds the generated API.
   *
   * @return The package name.
   */
  static String pkg() {
    return PACKAGE;
  }

  /**
   * Converts a C type name into the Java class name.
   *
   * @param cName The C name, such as {@code WGPUBufferDescriptor}.
   * @return The Java name, such as {@code GpuBufferDescriptor}.
   */
  static String type(String cName) {
    if (!cName.startsWith(C_PREFIX)) {
      throw new IllegalArgumentException("not a WGPU type: " + cName);
    }
    return "Gpu" + cName.substring(C_PREFIX.length());
  }

  /**
   * Converts a specification name into an UPPER_SNAKE_CASE constant name.
   *
   * @param specName The name from the specification, such as {@code R8_unorm}, {@code 2D_array} or {@code strlen}.
   * @return The constant name, such as {@code R8_UNORM}, {@code D2_ARRAY} or {@code STRLEN}.
   */
  static String constant(String specName) {
    Matcher m = DIMENSION.matcher(specName);
    String name = m.matches() ? "D" + m.group(1) + m.group(2) : specName;
    name = name.toUpperCase(Locale.ROOT);
    if (Character.isDigit(name.charAt(0))) {
      name = "_" + name;
    }
    return name;
  }

  /**
   * Converts a specification name into a camelCase method name.
   *
   * @param specName The name from the specification, such as {@code create_buffer}.
   * @return The method name, such as {@code createBuffer}.
   */
  static String method(String specName) {
    return ident(Naming.camel(specName));
  }

  /**
   * Makes a name safe to use as a Java identifier.
   *
   * @param name The C identifier.
   * @return The same name, or the name with a trailing underscore if it is a Java keyword.
   */
  static String ident(String name) {
    return KEYWORDS.contains(name) ? name + "_" : name;
  }

  /**
   * Splits a camelCase or snake_case name into lower-case words for use in prose.
   *
   * <p>Words that are acronyms or contain digits and capitals, such as {@code ID} or {@code 1D}, keep their case.
   *
   * @param name A name such as {@code colorAttachmentCount}.
   * @return The words, such as {@code color attachment count}.
   */
  static String words(String name) {
    String stripped = name.startsWith(C_PREFIX) ? name.substring(C_PREFIX.length()) : name;
    StringBuilder sb = new StringBuilder();
    for (String token : WORD_BREAK.split(stripped)) {
      if (token.isEmpty()) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append(' ');
      }
      boolean acronym = token.length() > 1 && token.equals(token.toUpperCase(Locale.ROOT));
      sb.append(acronym ? token : token.toLowerCase(Locale.ROOT));
    }
    return sb.toString();
  }

  /**
   * Joins the paragraphs of a doc text into one flowing paragraph, for use inside a block tag.
   *
   * @param doc Javadoc text that may contain paragraph breaks.
   * @return The text with every {@code <p>} paragraph break replaced by a space.
   */
  static String flat(String doc) {
    return doc.replace("\n\n<p>", " ");
  }

  /**
   * Puts the right indefinite article in front of a noun phrase.
   *
   * @param words A noun phrase in lower case, such as {@code instance} or {@code render pass}.
   * @return The phrase with {@code a} or {@code an} in front.
   */
  static String article(String words) {
    char c = words.isEmpty() ? 'x' : Character.toLowerCase(words.charAt(0));
    return ("aeiou".indexOf(c) >= 0 ? "an " : "a ") + words;
  }

  /**
   * Upper-cases the first letter of a text.
   *
   * @param text Any text.
   * @return The text starting with a capital letter.
   */
  static String capitalize(String text) {
    return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
  }
}
