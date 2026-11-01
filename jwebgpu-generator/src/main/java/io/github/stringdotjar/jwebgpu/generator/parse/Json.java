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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small, strict JSON parser with no dependencies.
 *
 * <p>It implements RFC 8259 and rejects everything the RFC rejects: comments, trailing commas, single quotes,
 * leading zeros, raw control characters inside strings, lone surrogate escapes and duplicate object keys. Strictness
 * is deliberate. A specification file that only parses because of a lenient reader would be a silent hazard
 * when the file is updated.
 *
 * <p>Example:
 *
 * <pre>{@code
 * JsonValue.Obj root = Json.parse("{\"name\": \"webgpu\"}").asObj();
 * String name = root.str("name");
 * }</pre>
 */
public final class Json {

  private static final int MAX_DEPTH = 512;

  private final String text;
  private int pos;
  private int depth;

  private Json(String text) {
    this.text = text;
  }

  /**
   * Parses a complete JSON document.
   *
   * @param text The document text.
   * @return The root value.
   * @throws JsonException If the text is not exactly one valid JSON value.
   */
  public static JsonValue parse(String text) {
    Json parser = new Json(text);
    parser.skipWhitespace();
    JsonValue value = parser.readValue();
    parser.skipWhitespace();
    if (parser.pos != text.length()) {
      throw parser.error("unexpected content after the end of the document");
    }
    return value;
  }

  private JsonValue readValue() {
    if (pos >= text.length()) {
      throw error("unexpected end of input");
    }
    char c = text.charAt(pos);
    return switch (c) {
      case '{' -> readObject();
      case '[' -> readArray();
      case '"' -> new JsonValue.Str(readString());
      case 't' -> readLiteral("true", new JsonValue.Bool(true));
      case 'f' -> readLiteral("false", new JsonValue.Bool(false));
      case 'n' -> readLiteral("null", new JsonValue.Null());
      default -> {
        if (c == '-' || (c >= '0' && c <= '9')) {

          yield readNumber();

        }
        throw error("unexpected character '" + printable(c) + "'");
      }
    };
  }

  private JsonValue readObject() {
    enter();
    pos++;
    Map<String, JsonValue> members = new LinkedHashMap<>();
    skipWhitespace();
    if (peek() == '}') {
      pos++;
      leave();
      return new JsonValue.Obj(members);
    }
    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw error("expected a string key");
      }
      int keyPos = pos;
      String key = readString();
      skipWhitespace();
      expect(':');
      skipWhitespace();
      JsonValue value = readValue();
      if (members.put(key, value) != null) {
        pos = keyPos;
        throw error("duplicate key '" + key + "'");
      }
      skipWhitespace();
      char c = peek();
      pos++;
      if (c == '}') {
        leave();
        return new JsonValue.Obj(members);
      }
      if (c != ',') {
        pos--;
        throw error("expected ',' or '}'");
      }
    }
  }

  private JsonValue readArray() {
    enter();
    pos++;
    List<JsonValue> items = new ArrayList<>();
    skipWhitespace();
    if (peek() == ']') {
      pos++;
      leave();
      return new JsonValue.Arr(items);
    }
    while (true) {
      skipWhitespace();
      items.add(readValue());
      skipWhitespace();
      char c = peek();
      pos++;
      if (c == ']') {
        leave();
        return new JsonValue.Arr(items);
      }
      if (c != ',') {
        pos--;
        throw error("expected ',' or ']'");
      }
    }
  }

  private String readString() {
    pos++;
    StringBuilder sb = new StringBuilder();
    while (true) {
      if (pos >= text.length()) {
        throw error("unterminated string");
      }
      char c = text.charAt(pos);
      if (c == '"') {
        pos++;
        return sb.toString();
      }
      if (c < 0x20) {
        throw error("raw control character in string");
      }
      if (c != '\\') {
        sb.append(c);
        pos++;
        continue;
      }
      pos++;
      if (pos >= text.length()) {
        throw error("unterminated escape sequence");
      }
      char e = text.charAt(pos);
      switch (e) {
        case '"', '\\', '/' -> sb.append(e);
        case 'b' -> sb.append('\b');
        case 'f' -> sb.append('\f');
        case 'n' -> sb.append('\n');
        case 'r' -> sb.append('\r');
        case 't' -> sb.append('\t');
        case 'u' -> {
          char unit = readUnicodeEscape();
          if (Character.isHighSurrogate(unit)) {
            if (!text.startsWith("\\u", pos + 1)) {
              throw error("high surrogate must be followed by a low surrogate escape");
            }
            pos += 2;
            char low = readUnicodeEscape();
            if (!Character.isLowSurrogate(low)) {
              throw error("high surrogate must be followed by a low surrogate escape");
            }
            sb.append(unit).append(low);
          } else if (Character.isLowSurrogate(unit)) {
            throw error("unpaired low surrogate escape");
          } else {
            sb.append(unit);
          }
        }
        default -> throw error("invalid escape '\\" + printable(e) + "'");
      }
      pos++;
    }
  }

  // Reads the four hex digits of a unicode escape and leaves pos on the last digit.
  private char readUnicodeEscape() {
    if (pos + 4 >= text.length()) {
      throw error("truncated unicode escape");
    }
    int value = 0;
    for (int i = 1; i <= 4; i++) {
      int digit = Character.digit(text.charAt(pos + i), 16);
      if (digit < 0) {
        throw error("invalid unicode escape");
      }
      value = (value << 4) | digit;
    }
    pos += 4;
    return (char) value;
  }

  private JsonValue readNumber() {
    int start = pos;
    if (text.charAt(pos) == '-') {
      pos++;
    }
    if (pos >= text.length()) {
      throw error("incomplete number");
    }
    char first = text.charAt(pos);
    if (first == '0') {
      pos++;
    } else if (first >= '1' && first <= '9') {
      skipDigits();
    } else {
      throw error("invalid number");
    }
    if (pos < text.length() && text.charAt(pos) == '.') {
      pos++;
      if (skipDigits() == 0) {
        throw error("expected digits after the decimal point");
      }
    }
    if (pos < text.length() && (text.charAt(pos) == 'e' || text.charAt(pos) == 'E')) {
      pos++;
      if (pos < text.length() && (text.charAt(pos) == '+' || text.charAt(pos) == '-')) {
        pos++;
      }
      if (skipDigits() == 0) {
        throw error("expected digits in the exponent");
      }
    }
    return new JsonValue.Num(text.substring(start, pos));
  }

  private int skipDigits() {
    int start = pos;
    while (pos < text.length() && text.charAt(pos) >= '0' && text.charAt(pos) <= '9') {
      pos++;
    }
    return pos - start;
  }

  private JsonValue readLiteral(String word, JsonValue value) {
    if (!text.startsWith(word, pos)) {
      throw error("invalid literal");
    }
    pos += word.length();
    return value;
  }

  private void skipWhitespace() {
    while (pos < text.length()) {
      char c = text.charAt(pos);
      if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
        pos++;
      } else {
        break;
      }
    }
  }

  private char peek() {
    if (pos >= text.length()) {
      throw error("unexpected end of input");
    }
    return text.charAt(pos);
  }

  private void expect(char c) {
    if (peek() != c) {
      throw error("expected '" + c + "'");
    }
    pos++;
  }

  private void enter() {
    if (++depth > MAX_DEPTH) {
      throw error("nesting is deeper than " + MAX_DEPTH + " levels");
    }
  }

  private void leave() {
    depth--;
  }

  private JsonException error(String message) {
    int line = 1;
    int col = 1;
    int end = Math.min(pos, text.length());
    for (int i = 0; i < end; i++) {
      if (text.charAt(i) == '\n') {
        line++;
        col = 1;
      } else {
        col++;
      }
    }
    return new JsonException("JSON error at line " + line + ", column " + col + ": " + message);
  }

  private static String printable(char c) {
    return c < 0x20 || c > 0x7E ? String.format("\\u%04x", (int) c) : String.valueOf(c);
  }
}
