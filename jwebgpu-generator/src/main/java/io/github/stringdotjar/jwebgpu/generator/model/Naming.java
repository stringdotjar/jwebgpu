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

import java.util.Locale;

/**
 * Name conversion helpers that mirror the upstream header generator.
 *
 * <p>The specification writes names in {@code snake_case}. The C header turns them into {@code PascalCase} for types
 * and {@code camelCase} for parameters. Only the first letter of each underscore separated word is changed, so an
 * acronym that is already upper case survives: {@code supported_WGSL_language_features} becomes
 * {@code SupportedWGSLLanguageFeatures}.
 */
public final class Naming {

  /** The prefix of every C type and function name. */
  public static final String TYPE_PREFIX = "WGPU";

  private Naming() {}

  /**
   * Converts a specification name to {@code PascalCase}.
   *
   * @param name The specification name, such as {@code render_pass_encoder}.
   * @return The converted name, such as {@code RenderPassEncoder}.
   */
  public static String pascal(String name) {
    StringBuilder sb = new StringBuilder(name.length());
    boolean upper = true;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (upper) {
        sb.append(Character.toUpperCase(c));
        upper = false;
      } else if (c == '_') {
        upper = true;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Converts a specification name to {@code camelCase}.
   *
   * @param name The specification name, such as {@code color_attachments}.
   * @return The converted name, such as {@code colorAttachments}.
   */
  public static String camel(String name) {
    StringBuilder sb = new StringBuilder(name.length());
    boolean upper = false;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (upper) {
        sb.append(Character.toUpperCase(c));
        upper = false;
      } else if (c == '_') {
        upper = true;
      } else {
        sb.append(c);
      }
    }
    return sb.toString();
  }

  /**
   * Converts a specification name to {@code UPPER_SNAKE_CASE}, the way the header spells constants and
   * {@code INIT} macros.
   *
   * @param name The specification name.
   * @return The upper case name with its underscores kept.
   */
  public static String constant(String name) {
    return name.toUpperCase(Locale.ROOT);
  }

  /**
   * Singularizes the name of an array member the way the header does when it builds the count member's name.
   *
   * <p>{@code entries} becomes {@code entry}; any other name loses one trailing {@code s}.
   *
   * @param name The array member name, such as {@code color_attachments}.
   * @return The singular name, such as {@code color_attachment}.
   */
  public static String singular(String name) {
    if (name.equals("entries")) {
      return "entry";
    }
    return name.endsWith("s") ? name.substring(0, name.length() - 1) : name;
  }

  /**
   * Builds the C name of a type from its specification name.
   *
   * @param name The specification name, such as {@code texture_view}.
   * @return The C name, such as {@code WGPUTextureView}.
   */
  public static String typeName(String name) {
    return TYPE_PREFIX + pascal(name);
  }
}
