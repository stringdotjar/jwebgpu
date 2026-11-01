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
package io.github.stringdotjar.jwebgpu.generator.doc;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the documentation strings of the specification into text that is safe to put inside a Javadoc comment.
 *
 * <p>The strings in {@code webgpu.json} are a mix of Markdown (backticks, bullet lists, emphasis, links) and
 * Doxygen commands ({@code @ref}, {@code @note}). Pasting them into a Javadoc comment would break the build:
 * {@code <} and {@code &} are not valid HTML, a leading {@code @} starts a block tag, and a stray {@code *}{@code /}
 * ends the comment early. This converter produces text that passes {@code doclint} and the repository's Javadoc
 * rules:
 *
 * <ul>
 *   <li>The output is pure ASCII. Accented letters lose their accents, typographic quotes and dashes become plain
 *       ones, and any other character becomes a numeric character reference.
 *   <li>The first sentence ends with a period, as Checkstyle's {@code SummaryJavadoc} requires.
 *   <li>The characters {@code <}, {@code >}, {@code &} and {@code @} and the comment terminator are escaped.
 *   <li>Backtick code becomes {@code {@code ...}}, {@code **bold**} becomes {@code <b>}, {@code *italic*} and
 *       {@code _italic_} become {@code <i>}, bullet lists become {@code <ul>} and Markdown links become
 *       {@code <a>} elements.
 *   <li>A Doxygen {@code @ref Name} becomes {@code {@code Name}}. The caller can rename C identifiers (for example
 *       to Java names) with a mapping function.
 *   <li>Paragraphs after the first start with {@code <p>}.
 * </ul>
 *
 * <p>The result has no comment delimiters and no leading asterisks; the emitter indents and wraps it.
 *
 * <p>Example:
 *
 * <pre>{@code
 * String text = JavadocText.convert("Sets the color. See @ref WGPUColor.\n\nUses `NULL` by default.");
 * // Sets the color. See {@code WGPUColor}.
 * //
 * // <p>Uses {@code NULL} by default.
 * }</pre>
 */
public final class JavadocText {

  private static final Pattern CODE_SPAN = Pattern.compile("`([^`\\n]+)`");
  private static final Pattern REF = Pattern.compile("[@\\\\]ref\\s+([A-Za-z0-9_:.\\-]*[A-Za-z0-9_])");
  private static final Pattern LINK = Pattern.compile("\\[([^\\]\\n]+)\\]\\(([^)\\s]+)\\)");
  private static final Pattern BOLD = Pattern.compile("\\*\\*(?=\\S)([^*]+?)(?<=\\S)\\*\\*");
  private static final Pattern ITALIC_STAR =
      Pattern.compile("(?<![\\w*])\\*(?=[^\\s*])([^*\\n]+?)(?<=[^\\s*])\\*(?![\\w*])");
  private static final Pattern ITALIC_UNDERSCORE =
      Pattern.compile("(?<![\\w])_(?=[^\\s_])([^_\\n]+?)(?<=[^\\s_])_(?![\\w])");
  private static final Pattern BULLET = Pattern.compile("^\\s*[-*]\\s+(.*)$");
  private static final Pattern NUMBERED = Pattern.compile("^\\s*\\d+[.)]\\s+(.*)$");
  private static final Pattern HEADING = Pattern.compile("^\\s*#{1,6}\\s+(.*)$");
  private static final Pattern NOTE = Pattern.compile("^[@\\\\](note|remark|remarks|warning|attention)\\b\\s*");
  private static final Pattern SENTENCE_END = Pattern.compile("[.!?](\\s|$|<)");
  private static final char MARK_START = '';
  private static final char MARK_END = '';

  private JavadocText() {}

  /**
   * Converts a specification doc string with no renaming of identifiers.
   *
   * @param doc The doc string from the specification. May be {@code null} or blank.
   * @return The Javadoc-safe text, or an empty string if there is nothing to say.
   */
  public static String convert(String doc) {
    return convert(doc, UnaryOperator.identity());
  }

  /**
   * Converts a specification doc string.
   *
   * @param doc The doc string from the specification. May be {@code null} or blank. The placeholder {@code TODO}
   *     counts as blank.
   * @param renamer Maps the identifier after each {@code @ref} (for example {@code WGPUBufferUsage}) to the text
   *     to show. Return the argument unchanged to keep it.
   * @return The Javadoc-safe text, or an empty string if there is nothing to say.
   */
  public static String convert(String doc, UnaryOperator<String> renamer) {
    if (doc == null) {
      return "";
    }
    String text = asciiFold(doc.replace("\r\n", "\n").replace('\r', '\n')).strip();
    if (text.isEmpty() || text.equals("TODO")) {
      return "";
    }
    List<String> blocks = new ArrayList<>();
    String[] lines = text.split("\n", -1);
    int i = 0;
    while (i < lines.length) {
      String line = lines[i];
      if (line.isBlank()) {
        i++;
      } else if (isListItem(line)) {
        i = readList(lines, i, renamer, blocks);
      } else if (line.stripLeading().startsWith("```")) {
        i = readFence(lines, i, blocks);
      } else {
        i = readParagraph(lines, i, renamer, blocks);
      }
    }
    return assemble(blocks);
  }

  private static boolean isListItem(String line) {
    return BULLET.matcher(line).matches() || NUMBERED.matcher(line).matches();
  }

  private static int readParagraph(String[] lines, int start, UnaryOperator<String> renamer, List<String> blocks) {
    int i = start;
    StringBuilder sb = new StringBuilder();
    while (i < lines.length && !lines[i].isBlank() && !(i > start && isListItem(lines[i]))) {
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(lines[i].strip());
      i++;
    }
    String paragraph = sb.toString();
    Matcher heading = HEADING.matcher(paragraph);
    if (heading.matches()) {
      blocks.add("<b>" + inline(heading.group(1), renamer) + "</b>");
      return i;
    }
    Matcher note = NOTE.matcher(paragraph);
    if (note.find()) {
      String label = Character.toUpperCase(note.group(1).charAt(0)) + note.group(1).substring(1);
      if (label.endsWith("s")) {
        label = label.substring(0, label.length() - 1);
      }
      blocks.add("<b>" + label + ":</b> " + inline(paragraph.substring(note.end()), renamer));
      return i;
    }
    blocks.add(inline(paragraph, renamer));
    return i;
  }

  private static int readList(String[] lines, int start, UnaryOperator<String> renamer, List<String> blocks) {
    boolean ordered = NUMBERED.matcher(lines[start]).matches();
    StringBuilder sb = new StringBuilder(ordered ? "<ol>" : "<ul>");
    int i = start;
    StringBuilder item = null;
    while (i < lines.length && !lines[i].isBlank()) {
      Matcher bullet = BULLET.matcher(lines[i]);
      Matcher numbered = NUMBERED.matcher(lines[i]);
      String itemText = null;
      if (bullet.matches()) {
        itemText = bullet.group(1);
      } else if (numbered.matches()) {
        itemText = numbered.group(1);
      }
      if (itemText != null) {
        flushItem(sb, item, renamer);
        item = new StringBuilder(itemText.strip());
      } else {
        item.append('\n').append(lines[i].strip());
      }
      i++;
    }
    flushItem(sb, item, renamer);
    sb.append(ordered ? "\n</ol>" : "\n</ul>");
    blocks.add(sb.toString());
    return i;
  }

  private static void flushItem(StringBuilder sb, StringBuilder item, UnaryOperator<String> renamer) {
    if (item != null) {
      sb.append("\n<li>").append(inline(item.toString(), renamer));
    }
  }

  private static int readFence(String[] lines, int start, List<String> blocks) {
    StringBuilder sb = new StringBuilder("<pre>");
    int i = start + 1;
    boolean first = true;
    while (i < lines.length && !lines[i].stripLeading().startsWith("```")) {
      if (!first) {
        sb.append('\n');
      }
      sb.append(escape(lines[i]));
      first = false;
      i++;
    }
    sb.append("</pre>");
    blocks.add(sb.toString());
    return Math.min(i + 1, lines.length);
  }

  private static String assemble(List<String> blocks) {
    if (blocks.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < blocks.size(); i++) {
      String block = blocks.get(i);
      if (i == 0) {
        sb.append(ensureSentence(block));
      } else {
        sb.append("\n\n");
        boolean structural = block.startsWith("<ul>") || block.startsWith("<ol>") || block.startsWith("<pre>");
        sb.append(structural ? block : "<p>" + block);
      }
    }
    return sb.toString();
  }

  private static String ensureSentence(String first) {
    if (first.startsWith("<ul>") || first.startsWith("<ol>") || first.startsWith("<pre>")) {
      return first;
    }
    if (SENTENCE_END.matcher(first).find()) {
      return first;
    }
    String trimmed = first.stripTrailing();
    while (trimmed.endsWith(":") || trimmed.endsWith(",") || trimmed.endsWith(";")) {
      trimmed = trimmed.substring(0, trimmed.length() - 1).stripTrailing();
    }
    return trimmed + ".";
  }

  private static String inline(String raw, UnaryOperator<String> renamer) {
    List<String> held = new ArrayList<>();
    String s = raw;
    s = replaceAll(CODE_SPAN, s, m -> hold(held, codeHtml(m.group(1))));
    s = replaceAll(REF, s, m -> hold(held, codeHtml(renamer.apply(m.group(1)))));
    s = replaceAll(LINK, s, m -> {
      String label = m.group(1);
      String target = m.group(2);
      if (target.startsWith("@ref") || target.startsWith("\\ref") || target.startsWith("#")) {
        return label;
      }
      if (!(target.startsWith("http://") || target.startsWith("https://"))) {
        return label;
      }
      return hold(held, "<a href=\"" + escape(target).replace("\"", "&quot;") + "\">" + escape(label) + "</a>");
    });
    s = escape(s);
    s = BOLD.matcher(s).replaceAll("<b>$1</b>");
    s = ITALIC_STAR.matcher(s).replaceAll("<i>$1</i>");
    s = ITALIC_UNDERSCORE.matcher(s).replaceAll("<i>$1</i>");
    return restore(s, held);
  }

  private static String restore(String s, List<String> held) {
    if (s.indexOf(MARK_START) < 0) {
      return s;
    }
    StringBuilder out = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == MARK_START) {
        int end = s.indexOf(MARK_END, i);
        out.append(restore(held.get(Integer.parseInt(s.substring(i + 1, end))), held));
        i = end;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }

  private static String hold(List<String> held, String html) {
    held.add(html);
    return MARK_START + Integer.toString(held.size() - 1) + MARK_END;
  }

  private static String replaceAll(Pattern pattern, String input, Function<Matcher, String> fn) {
    Matcher m = pattern.matcher(input);
    StringBuilder sb = new StringBuilder();
    while (m.find()) {
      m.appendReplacement(sb, Matcher.quoteReplacement(fn.apply(m)));
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static String codeHtml(String code) {
    boolean safe = !code.contains("*/") && !code.contains("/*") && balanced(code);
    if (safe) {
      return "{@code " + code + "}";
    }
    return "<code>" + escape(code).replace("{", "&#123;").replace("}", "&#125;") + "</code>";
  }

  private static boolean balanced(String code) {
    int depth = 0;
    for (int i = 0; i < code.length(); i++) {
      char c = code.charAt(i);
      if (c == '{') {
        depth++;
      } else if (c == '}' && --depth < 0) {
        return false;
      }
    }
    return depth == 0 && !code.startsWith("@") && !code.endsWith("\\");
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder(s.length() + 8);
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        case '@' -> sb.append("&#64;");
        case '/' -> sb.append(i > 0 && s.charAt(i - 1) == '*' ? "&#47;" : "/");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  /** Replaces non-ASCII characters so that the result is pure ASCII. */
  private static String asciiFold(String s) {
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c < 0x80) {
        sb.append(c);
        continue;
      }
      switch (c) {
        case '‘', '’', '‛' -> sb.append('\'');
        case '“', '”', '‟' -> sb.append('"');
        case '–', '—', '−' -> sb.append('-');
        case '…' -> sb.append("...");
        case ' ', ' ', ' ' -> sb.append(' ');
        case '→' -> sb.append("->");
        case '←' -> sb.append("<-");
        case '×' -> sb.append('x');
        default -> {
          String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
          StringBuilder base = new StringBuilder();
          for (int j = 0; j < decomposed.length(); j++) {
            if (decomposed.charAt(j) < 0x80) {
              base.append(decomposed.charAt(j));
            }
          }
          if (base.length() > 0) {
            sb.append(base);
          } else {
            sb.append("?");
          }
        }
      }
    }
    return sb.toString();
  }
}
