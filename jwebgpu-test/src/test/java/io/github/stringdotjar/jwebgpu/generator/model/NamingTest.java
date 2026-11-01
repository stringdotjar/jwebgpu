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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tests the name conversions that mirror the upstream header generator. */
class NamingTest {

  @Test
  void pascalCaseUppercasesTheFirstLetterOfEachWord() {
    assertEquals("RenderPassEncoder", Naming.pascal("render_pass_encoder"));
    assertEquals("SupportedWGSLLanguageFeatures", Naming.pascal("supported_WGSL_language_features"));
    assertEquals("Extent3D", Naming.pascal("extent_3D"));
    assertEquals("R8Unorm", Naming.pascal("r8_unorm"));
  }

  @Test
  void camelCaseKeepsTheFirstLetter() {
    assertEquals("colorAttachments", Naming.camel("color_attachments"));
    assertEquals("timeoutNS", Naming.camel("timeout_NS"));
    assertEquals("nextInChain", Naming.camel("nextInChain"));
  }

  @Test
  void singularDropsOneTrailingS() {
    assertEquals("color_attachment", Naming.singular("color_attachments"));
    assertEquals("entry", Naming.singular("entries"));
    assertEquals("feature", Naming.singular("features"));
    assertEquals("data", Naming.singular("data"));
  }

  @Test
  void typeNamesGetThePrefix() {
    assertEquals("WGPUTextureView", Naming.typeName("texture_view"));
    assertEquals("WGPU_STRLEN", "WGPU_" + Naming.constant("strlen"));
  }
}
