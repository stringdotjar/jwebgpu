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
package io.github.stringdotjar.jwebgpu.generator.conformance;

import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Checks the resolved model against real C headers.
 *
 * <p>The vendored upstream {@code webgpu.h} is generated from the same {@code webgpu.json} by upstream's Go
 * generator. Parsing that header with an independent, minimal C scanner and requiring an exact match proves that the
 * resolver reproduces the Go generator's rules. Dawn's own header (not vendored, optional) is compared more
 * loosely: Dawn adds declarations of its own, which are reported but never fail the test.
 */
class HeaderConformanceTest {

  private static CHeader scan(Path file) throws IOException {
    return CHeader.parse(Files.readString(file, StandardCharsets.UTF_8));
  }

  @Test
  void scannerSeesTheExpectedUpstreamDeclarationCounts() throws IOException {
    CHeader h = scan(SpecFiles.upstreamHeader());
    assertEquals(202, h.functions.size());
    assertEquals(92, h.structs.size());
    assertEquals(54, h.enums.size());
    assertEquals(5, h.bitflags.size());
    assertEquals(10, h.callbacks.size());
    assertEquals(23, h.objects.size());
    assertEquals(13, h.constants.size());
  }

  @Test
  void modelHasTheExpectedCounts() {
    Spec spec = SpecFiles.load();
    assertEquals(202, spec.functions().size());
    assertEquals(92, spec.structs().size());
    assertEquals(54, spec.enums().size());
    assertEquals(5, spec.bitflags().size());
    assertEquals(10, spec.callbacks().size());
    assertEquals(23, spec.objects().size());
    assertEquals(11, spec.constants().size());
  }

  @Test
  void modelMatchesTheUpstreamHeaderExactly() throws IOException {
    ModelComparer cmp = new ModelComparer(SpecFiles.load(), scan(SpecFiles.upstreamHeader()), true);
    cmp.compareAll();
    assertTrue(cmp.mismatches.isEmpty(), () -> "model and webgpu.h disagree:\n" + String.join("\n", cmp.mismatches));
    assertTrue(cmp.extras.isEmpty());
  }

  private static void reportExtras(List<String> extras) {
    Map<String, Integer> enumValues = new TreeMap<>();
    List<String> declarations = new ArrayList<>();
    for (String e : extras) {
      int at = e.indexOf(": header only: ");
      if (at >= 0) {
        enumValues.merge(e.substring(0, at), 1, Integer::sum);
      } else {
        declarations.add(e.replace(" is in the header but not in the model", ""));
      }
    }
    System.out.println("Dawn-only declarations (" + declarations.size() + "):");
    declarations.forEach(d -> System.out.println("  " + d));
    System.out.println("Dawn-only values added to shared enums and bit flags:");
    enumValues.forEach((where, n) -> System.out.println("  " + where + ": " + n + " extra values"));
  }

  @Test
  void reportsWhatDawnAddsToTheUpstreamHeader() throws IOException {
    Path dawn = SpecFiles.dawnHeader();
    assumeTrue(dawn != null, "Dawn header not configured (set the jwebgpu.dawnHeader system property)");
    CHeader h = scan(dawn);
    ModelComparer cmp = new ModelComparer(SpecFiles.load(), h, false);
    cmp.compareAll();

    System.out.println("Dawn header: " + dawn);
    System.out.println(
        "Dawn declares " + h.functions.size() + " functions, " + h.structs.size() + " structs, " + h.enums.size()
            + " enums, " + h.bitflags.size() + " bit flag types, " + h.callbacks.size() + " callbacks, "
            + h.objects.size() + " objects and " + h.constants.size() + " constants.");
    reportExtras(cmp.extras);

    assertTrue(
        cmp.mismatches.isEmpty(),
        () -> "Dawn's header is not a superset of the model:\n" + String.join("\n", cmp.mismatches));
  }
}
