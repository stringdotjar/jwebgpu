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

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Builds the text of one Java source file with the repository's formatting rules.
 *
 * <p>The writer takes care of the things every emitter would otherwise repeat: the copyright header, the package
 * line, sorted and grouped imports, two-space indentation, and word-wrapped Javadoc. An emitter writes the body of the
 * file line by line, asks {@link #imp(String)} for every type it needs, and finally calls {@link #toSource()}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * SourceWriter w = new SourceWriter("org.example");
 * String list = w.imp("java.util.List");
 * w.doc("Does a thing.");
 * w.open("public final class Thing");
 * w.line("private " + list + "<String> names;");
 * w.close();
 * String text = w.toSource();
 * }</pre>
 *
 * <p>The output follows the Spotless, Checkstyle and doclint rules of the repository, because generated sources are
 * never reformatted by the build.
 */
public final class SourceWriter {

  private static final int WIDTH = 120;
  private static final String INDENT = "  ";
  private static final String[] LICENSE = {
      "/*",
      " * MIT License",
      " *",
      " * Copyright (c) 2026 stringdotjar",
      " *",
      " * Permission is hereby granted, free of charge, to any person obtaining a copy",
      " * of this software and associated documentation files (the \"Software\"), to deal",
      " * in the Software without restriction, including without limitation the rights",
      " * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell",
      " * copies of the Software, and to permit persons to whom the Software is",
      " * furnished to do so, subject to the following conditions:",
      " *",
      " * The above copyright notice and this permission notice shall be included in all",
      " * copies or substantial portions of the Software.",
      " *",
      " * THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR",
      " * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,",
      " * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE",
      " * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER",
      " * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,",
      " * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE",
      " * SOFTWARE.",
      " */"
  };

  private final String packageName;
  private final TreeSet<String> imports = new TreeSet<>();
  private final StringBuilder body = new StringBuilder();
  private int level;

  /**
   * Creates a writer for a file in the given package.
   *
   * @param packageName The package of the file, such as {@code io.github.stringdotjar.jwebgpu}.
   */
  public SourceWriter(String packageName) {
    this.packageName = packageName;
  }

  /**
   * Registers an import and returns the name to use in the code.
   *
   * @param qualifiedName The fully qualified class name. Classes in {@code java.lang} and in the file's own package
   *     need no import and are returned as their simple name.
   * @return The simple name of the class.
   */
  public String imp(String qualifiedName) {
    int dot = qualifiedName.lastIndexOf('.');
    String pkg = qualifiedName.substring(0, dot);
    if (!pkg.equals("java.lang") && !pkg.equals(packageName)) {
      imports.add(qualifiedName);
    }
    return qualifiedName.substring(dot + 1);
  }

  /**
   * Writes one line at the current indentation.
   *
   * @param text The line, without a line break. An empty text writes an empty line.
   * @return This writer.
   */
  public SourceWriter line(String text) {
    if (!text.isEmpty()) {
      for (int i = 0; i < level; i++) {
        body.append(INDENT);
      }
      body.append(text);
    }
    body.append('\n');
    return this;
  }

  /**
   * Writes an empty line, unless the previous line is already empty or opens a block.
   *
   * @return This writer.
   */
  public SourceWriter blank() {
    int n = body.length();
    if (n == 0 || body.charAt(n - 1) != '\n') {
      return this;
    }
    if (n >= 2 && body.charAt(n - 2) == '\n') {
      return this;
    }
    if (n >= 2 && body.charAt(n - 2) == '{') {
      return this;
    }
    body.append('\n');
    return this;
  }

  /**
   * Writes a line that opens a block and indents what follows.
   *
   * @param header The text before the brace, such as {@code public final class Thing}.
   * @return This writer.
   */
  public SourceWriter open(String header) {
    line(header + " {");
    level++;
    return this;
  }

  /**
   * Writes a line that opens a class, interface or enum body, followed by the empty line that the repository's
   * formatting rules require after a type header.
   *
   * @param header The text before the brace, such as {@code public final class Thing}.
   * @return This writer.
   */
  public SourceWriter openType(String header) {
    open(header);
    body.append('\n');
    return this;
  }

  /**
   * Closes the innermost block.
   *
   * @return This writer.
   */
  public SourceWriter close() {
    return close("");
  }

  /**
   * Closes the innermost block and appends text after the brace.
   *
   * @param suffix The text after the closing brace, for example {@code ;} to end a switch expression.
   * @return This writer.
   */
  public SourceWriter close(String suffix) {
    int n = body.length();
    if (n >= 2 && body.charAt(n - 1) == '\n' && body.charAt(n - 2) == '\n') {
      body.setLength(n - 1);
    }
    level--;
    return line("}" + suffix);
  }

  /**
   * Writes a call or declaration whose arguments are wrapped when the line would be too long.
   *
   * <p>If everything fits on one line (within 120 columns), it is written as {@code head + args + tail}. Otherwise
   * the head is written, then each argument on its own line with a four-space continuation indent, and the tail
   * follows the last argument.
   *
   * @param head The text before the arguments, ending with the opening parenthesis.
   * @param args The arguments or parameters, without separators.
   * @param tail The text after the arguments, starting with the closing parenthesis.
   * @return This writer.
   */
  public SourceWriter call(String head, List<String> args, String tail) {
    String joined = String.join(", ", args);
    if (level * INDENT.length() + head.length() + joined.length() + tail.length() <= WIDTH) {
      return line(head + joined + tail);
    }
    line(head);
    level += 2;
    for (int i = 0; i < args.size(); i++) {
      line(args.get(i) + (i == args.size() - 1 ? tail : ","));
    }
    level -= 2;
    return this;
  }

  /**
   * Writes a Javadoc comment.
   *
   * <p>A comment of one short line (and no block tags) is written on a single line; anything longer is written as a
   * block with wrapped lines.
   *
   * @param text The Javadoc body. Lines are kept, and lines that are too long are wrapped. Blank lines separate
   *     paragraphs. A {@code <pre>} block is never wrapped.
   * @param tags Block tags, each one complete (for example {@code @param name The name.}). A tag may span lines;
   *     continuation lines are indented by four spaces.
   * @return This writer.
   */
  public SourceWriter doc(String text, List<String> tags) {
    if (tags.isEmpty() && text.indexOf('\n') < 0 && level * INDENT.length() + text.length() + 8 <= WIDTH) {
      return line("/** " + text + " */");
    }
    int width = WIDTH - level * INDENT.length() - 3;
    List<String> lines = new ArrayList<>();
    wrap(text, width, "", lines);
    if (!tags.isEmpty()) {
      lines.add("");
      for (String tag : tags) {
        wrap(tag, width, "    ", lines);
      }
    }
    line("/**");
    for (String l : lines) {
      line(l.isEmpty() ? " *" : " * " + l);
    }
    return line(" */");
  }

  /**
   * Writes a Javadoc comment without block tags.
   *
   * @param text The Javadoc body.
   * @return This writer.
   */
  public SourceWriter doc(String text) {
    return doc(text, List.of());
  }

  /**
   * Returns the complete file: copyright header, package line, imports and body.
   *
   * @return The source text, ending with a newline.
   */
  public String toSource() {
    StringBuilder sb = new StringBuilder(body.length() + 2048);
    for (String l : LICENSE) {
      sb.append(l).append('\n');
    }
    sb.append("package ").append(packageName).append(";\n\n");
    String[] groups = { "com.", "org.", "io.", "java.", "javax.", "jdk." };
    boolean any = false;
    List<String> rest = new ArrayList<>(imports);
    for (String group : groups) {
      List<String> inGroup = new ArrayList<>();
      for (String imp : rest) {
        if (imp.startsWith(group)) {
          inGroup.add(imp);
        }
      }
      rest.removeAll(inGroup);
      any |= appendGroup(sb, inGroup, any);
    }
    any |= appendGroup(sb, rest, any);
    if (any) {
      sb.append('\n');
    }
    sb.append(body);
    return sb.toString();
  }

  private static boolean appendGroup(StringBuilder sb, List<String> group, boolean needsBlank) {
    if (group.isEmpty()) {
      return false;
    }
    if (needsBlank) {
      sb.append('\n');
    }
    for (String imp : group) {
      sb.append("import ").append(imp).append(";\n");
    }
    return true;
  }

  private static void wrap(String text, int width, String continuation, List<String> out) {
    boolean inPre = false;
    boolean firstLine = true;
    for (String raw : text.split("\n", -1)) {
      boolean wasInPre = inPre;
      if (raw.contains("<pre>")) {
        inPre = true;
      }
      if (raw.contains("</pre>")) {
        inPre = false;
      }
      String prefix = firstLine ? "" : continuation;
      if (wasInPre || inPre || raw.isEmpty() || raw.length() + prefix.length() <= width) {
        out.add(raw.isEmpty() ? "" : prefix + raw);
      } else {
        wrapLine(raw, width, prefix, continuation, out);
      }
      firstLine = false;
    }
  }

  private static boolean glued(String word) {
    return word.equals("<a") || (word.startsWith("{@") && word.indexOf('}') < 0 && word.indexOf(' ') < 0);
  }

  private static void wrapLine(String raw, int width, String firstPrefix, String continuation, List<String> out) {
    StringBuilder cur = new StringBuilder(firstPrefix);
    int words = 0;
    String[] parts = raw.split(" ");
    for (int i = 0; i < parts.length; i++) {
      String word = parts[i];
      while (i + 1 < parts.length && glued(word)) {
        word = word + " " + parts[++i];
      }
      if (words > 0 && cur.length() + 1 + word.length() > width) {
        out.add(cur.toString());
        cur = new StringBuilder(continuation);
        words = 0;
      }
      if (words > 0) {
        cur.append(' ');
      }
      cur.append(word);
      words++;
    }
    out.add(cur.toString());
  }
}
