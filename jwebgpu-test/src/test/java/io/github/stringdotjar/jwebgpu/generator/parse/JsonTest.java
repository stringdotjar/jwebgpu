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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the strict JSON parser. */
class JsonTest {

  private static String errorOf(String text) {
    return assertThrows(JsonException.class, () -> Json.parse(text)).getMessage();
  }

  @Test
  void parsesScalars() {
    assertEquals("hi", Json.parse("\"hi\"").asString());
    assertEquals(42L, Json.parse("42").asLong());
    assertEquals(-7L, Json.parse("-7").asLong());
    assertEquals(1.5e3, Json.parse("1.5e3").asDouble());
    assertTrue(Json.parse("true").asBoolean());
    assertFalse(Json.parse("false").asBoolean());
    assertInstanceOf(JsonValue.Null.class, Json.parse("null"));
  }

  @Test
  void keepsNumbersAsWrittenText() {
    assertEquals("0.10", assertInstanceOf(JsonValue.Num.class, Json.parse("0.10")).raw());
    assertEquals("18446744073709551615",
        assertInstanceOf(JsonValue.Num.class, Json.parse("18446744073709551615")).raw());
  }

  @Test
  void rejectsIntegersThatDoNotFitInLong() {
    JsonValue big = Json.parse("18446744073709551615");
    assertThrows(JsonException.class, big::asLong);
    assertThrows(JsonException.class, Json.parse("1.5")::asLong);
  }

  @Test
  void parsesNestedStructuresAndKeepsKeyOrder() {
    JsonValue.Obj o = Json.parse(" { \"b\" : [1, 2, {\"c\": null}], \"a\": {} , \"z\":[] } ").asObj();
    assertEquals(List.of("b", "a", "z"), List.copyOf(o.members().keySet()));
    List<JsonValue> items = o.arr("b");
    assertEquals(3, items.size());
    assertEquals(2L, items.get(1).asLong());
    assertInstanceOf(JsonValue.Null.class, items.get(2).asObj().require("c"));
    assertTrue(o.obj("a").members().isEmpty());
    assertTrue(o.arr("z").isEmpty());
    assertTrue(o.arr("missing").isEmpty());
  }

  @Test
  void decodesStringEscapes() {
    assertEquals("a\"b\\c/d\b\f\n\r\t", Json.parse("\"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\"").asString());
    assertEquals("A\u00e9", Json.parse("\"\\u0041\\u00E9\"").asString());
    assertEquals("\uD83D\uDE00", Json.parse("\"\\uD83D\\uDE00\"").asString());
    assertEquals("caf\u00e9", Json.parse("\"caf\u00e9\"").asString());
  }

  @Test
  void accessorsHaveDefaults() {
    JsonValue.Obj o = Json.parse("{\"s\":\"x\",\"b\":true}").asObj();
    assertEquals("x", o.str("s"));
    assertEquals("fallback", o.str("nope", "fallback"));
    assertTrue(o.bool("b", false));
    assertTrue(o.bool("nope", true));
    assertTrue(o.has("s"));
    assertFalse(o.has("nope"));
    assertThrows(JsonException.class, () -> o.str("nope"));
    assertThrows(JsonException.class, () -> o.str("b"));
  }

  @Test
  void rejectsTrailingCommasAndComments() {
    errorOf("[1,]");
    errorOf("{\"a\":1,}");
    errorOf("// c\n[1]");
    errorOf("[1] /* c */");
  }

  @Test
  void rejectsBadNumbers() {
    errorOf("01");
    errorOf("-");
    errorOf("1.");
    errorOf(".5");
    errorOf("1e");
    errorOf("+1");
    errorOf("0x10");
    errorOf("NaN");
  }

  @Test
  void rejectsBadStrings() {
    errorOf("'single'");
    errorOf("\"unterminated");
    errorOf("\"tab\there\"");
    errorOf("\"bad \\q escape\"");
    errorOf("\"\\u12\"");
    errorOf("\"\\uZZZZ\"");
    errorOf("\"\\uD83D\"");
    errorOf("\"\\uDE00\"");
    errorOf("\"\\uD83D\\u0041\"");
  }

  @Test
  void rejectsBadStructure() {
    errorOf("");
    errorOf("   ");
    errorOf("{");
    errorOf("[1 2]");
    errorOf("{\"a\" 1}");
    errorOf("{a: 1}");
    errorOf("[1] 2");
    errorOf("tru");
    errorOf("nul");
  }

  @Test
  void rejectsDuplicateKeys() {
    assertTrue(errorOf("{\"a\":1,\"a\":2}").contains("duplicate key 'a'"));
  }

  @Test
  void reportsLineAndColumn() {
    String message = errorOf("{\n  \"a\": 1,\n  \"b\": ?\n}");
    assertTrue(message.contains("line 3, column 8"), message);
  }

  @Test
  void limitsNestingDepth() {
    assertTrue(errorOf("[".repeat(600) + "]".repeat(600)).contains("deeper"));
    Json.parse("[".repeat(100) + "]".repeat(100));
  }

  @Test
  void reportsKindMismatches() {
    assertTrue(assertThrows(JsonException.class, () -> Json.parse("[]").asObj()).getMessage().contains("array"));
    assertTrue(assertThrows(JsonException.class, () -> Json.parse("{}").asString()).getMessage().contains("object"));
  }
}
