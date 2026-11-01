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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the conversion of specification doc strings to Javadoc-safe text. */
class JavadocTextTest {

  @Test
  void blankAndTodoDocsBecomeEmpty() {
    assertEquals("", JavadocText.convert(null));
    assertEquals("", JavadocText.convert(""));
    assertEquals("", JavadocText.convert("  \n "));
    assertEquals("", JavadocText.convert("TODO\n"));
  }

  @Test
  void aSimpleSentenceIsKept() {
    assertEquals("Creates a buffer.", JavadocText.convert("Creates a buffer.\n"));
  }

  @Test
  void theFirstSentenceGetsAPeriod() {
    assertEquals("Creates a buffer.", JavadocText.convert("Creates a buffer"));
    assertEquals("The following values.", JavadocText.convert("The following values:"));
  }

  @Test
  void aPeriodInsideTheFirstParagraphIsEnough() {
    assertEquals("One. Two", JavadocText.convert("One. Two"));
  }

  @Test
  void codeSpansBecomeCodeTags() {
    assertEquals("Use {@code NULL} here.", JavadocText.convert("Use `NULL` here."));
    assertEquals("Set {@code { a: [] }} now.", JavadocText.convert("Set `{ a: [] }` now."));
  }

  @Test
  void codeSpansWithUnbalancedBracesFallBackToHtml() {
    assertEquals("Use <code>&#123;</code> here.", JavadocText.convert("Use `{` here."));
  }

  @Test
  void refsBecomeCodeAndCanBeRenamed() {
    assertEquals("See {@code WGPUBuffer}.", JavadocText.convert("See @ref WGPUBuffer."));
    assertEquals("See {@code WGPUFoo::bar}.", JavadocText.convert("See @ref WGPUFoo::bar."));
    assertEquals("See {@code GpuBuffer}.", JavadocText.convert("See @ref WGPUBuffer.", n -> n.replace("WGPU", "Gpu")));
    assertEquals("See {@code X}.", JavadocText.convert("See \\ref X."));
  }

  @Test
  void htmlCharactersAreEscaped() {
    assertEquals("Needs a &lt; b &amp;&amp; c &gt; d.", JavadocText.convert("Needs a < b && c > d."));
  }

  @Test
  void atSignsCannotStartABlockTag() {
    String out = JavadocText.convert("Intro.\n\n@param x is bad\n@return nothing");
    assertFalse(out.contains("@"), out);
    assertTrue(out.contains("&#64;param"));
  }

  @Test
  void theCommentTerminatorIsEscaped() {
    String out = JavadocText.convert("Ends with */ early. And /* nested.");
    assertFalse(out.contains("*/"), out);
  }

  @Test
  void codeCannotContainTheCommentTerminator() {
    String out = JavadocText.convert("Look at `a*/b` now.");
    assertFalse(out.contains("*/"), out);
    assertTrue(out.contains("<code>"));
  }

  @Test
  void outputIsAscii() {
    String out = JavadocText.convert("caf\u00e9 \u201Cquoted\u201D \u2014 dash \u2026 \u4e2d `\u00fc`");
    for (int i = 0; i < out.length(); i++) {
      assertTrue(out.charAt(i) < 0x80, "non-ascii at " + i + ": " + out);
    }
    assertTrue(out.startsWith("cafe \"quoted\" - dash ... "), out);
  }

  @Test
  void paragraphsAfterTheFirstStartWithP() {
    assertEquals("First.\n\n<p>Second.\n\n<p>Third.", JavadocText.convert("First.\n\nSecond.\n\n\nThird."));
  }

  @Test
  void bulletListsBecomeUl() {
    String out = JavadocText.convert("Choices:\n- one\n- two\n  continues\n* three\n\nAfter.");
    assertEquals("Choices.\n\n<ul>\n<li>one\n<li>two\ncontinues\n<li>three\n</ul>\n\n<p>After.", out);
  }

  @Test
  void numberedListsBecomeOl() {
    assertEquals("Steps.\n\n<ol>\n<li>a\n<li>b\n</ol>", JavadocText.convert("Steps.\n1. a\n2. b"));
  }

  @Test
  void emphasisIsConverted() {
    assertEquals("A <b>bold</b>, <i>italic</i> and <i>other</i> word.",
        JavadocText.convert("A **bold**, *italic* and _other_ word."));
  }

  @Test
  void identifiersWithUnderscoresAreNotItalic() {
    assertEquals("Uses map_async_status here.", JavadocText.convert("Uses map_async_status here."));
  }

  @Test
  void linksAreConverted() {
    assertEquals(
        "See <a href=\"https://example.com/a?b=1&amp;c=2\">the page</a>.",
        JavadocText.convert("See [the page](https://example.com/a?b=1&c=2)."));
    assertEquals("It defaults to one.", JavadocText.convert("It [defaults](@ref SentinelValues) to one."));
  }

  @Test
  void notesAreLabeled() {
    assertEquals("Body.\n\n<p><b>Note:</b> careful.", JavadocText.convert("Body.\n\n@note careful."));
  }

  @Test
  void headingsBecomeBoldText() {
    assertEquals("<b>Title</b>.\n\n<p>Body.", JavadocText.convert("# Title\n\nBody."));
  }

  @Test
  void fencedBlocksBecomePreformattedText() {
    assertEquals("Code.\n\n<pre>a &lt; b\nc</pre>", JavadocText.convert("Code:\n\n```\na < b\nc\n```"));
  }
}
