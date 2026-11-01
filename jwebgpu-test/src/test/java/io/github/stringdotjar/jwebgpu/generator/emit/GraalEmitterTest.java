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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.github.stringdotjar.jwebgpu.generator.SpecFiles;
import io.github.stringdotjar.jwebgpu.generator.model.Callback;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.GpuObject;
import io.github.stringdotjar.jwebgpu.generator.model.Naming;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.parse.Json;
import io.github.stringdotjar.jwebgpu.generator.parse.JsonValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the native image metadata of the FFM backend against the model and against the generated Java sources.
 *
 * <p>The metadata must list every downcall and every callback, and it must match the descriptors in the generated
 * code exactly, because a native image that lacks one fails at run time with a missing registration. Two kinds of
 * check guard that: the first walks the specification, and the second reads the Java text that {@link FfmEmitter}
 * wrote, translates each {@code FunctionDescriptor} expression by plain text rules and looks it up in the metadata.
 */
class GraalEmitterTest {

  private static final Pattern DESCRIPTOR = Pattern.compile("(Native\\.handle|Native\\.upcall)\\((.*)\\);");
  private static final Pattern FUNCTION = Pattern.compile("FunctionDescriptor\\.(of|ofVoid)\\((.*)\\)\\)?;?$");
  private static final Pattern LAYOUT_LINE = Pattern.compile("static final StructLayout LAYOUT = (.*);");
  private static final Pattern TOKENS =
      Pattern.compile("jlong|jint|jshort|jbyte|jfloat|jdouble|padding\\([0-9]+\\)|struct\\(|\\)|,| ");

  private static Spec spec;
  private static List<GeneratedFile> files;
  private static String json;
  private static JsonValue.Obj root;

  @BeforeAll
  static void emit() {
    spec = SpecFiles.load();
    files = GraalEmitter.emit(spec);
    json = file("reachability-metadata.json");
    root = Json.parse(json).asObj();
  }

  private static String file(String name) {
    String path = "META-INF/native-image/io.github.stringdotjar/jwebgpu-ffm/" + name;
    for (GeneratedFile f : files) {
      if (f.path().equals(path)) {
        return f.source();
      }
    }
    throw new AssertionError("not generated: " + path);
  }

  private static Set<String> signatures(String section) {
    Set<String> set = new HashSet<>();
    for (JsonValue v : root.obj("foreign").arr(section)) {
      set.add(signature(v.asObj()));
    }
    return set;
  }

  private static String signature(JsonValue.Obj entry) {
    List<String> params = new ArrayList<>();
    for (JsonValue p : entry.arr("parameterTypes")) {
      params.add(p.asString());
    }
    return entry.str("returnType") + " (" + String.join(", ", params) + ")";
  }

  private static String signature(FfmDescriptor d) {
    List<String> params = new ArrayList<>();
    for (FfmLayout l : d.params()) {
      params.add(l.graal());
    }
    return (d.ret() == null ? "void" : d.ret().graal()) + " (" + String.join(", ", params) + ")";
  }

  @Test
  void everyDowncallInTheModelIsRegistered() {
    Set<String> registered = signatures("downcalls");
    int functions = 0;
    for (GpuObject o : spec.objects()) {
      for (GpuFunction f : o.methods()) {
        assertTrue(registered.contains(signature(FfmDescriptors.of(f))), f.cName());
        functions++;
      }
    }
    for (GpuFunction f : FfmDescriptors.globals(spec)) {
      assertTrue(registered.contains(signature(FfmDescriptors.of(f))), f.cName());
      functions++;
    }
    for (Struct s : spec.structs()) {
      if (s.freeMembers()) {
        assertTrue(registered.contains(signature(FfmDescriptors.free(spec, s))), s.cName());
        functions++;
      }
    }
    assertTrue(functions > 100, "the walk saw only " + functions + " functions");
    assertEquals(registered.size(), root.obj("foreign").arr("downcalls").size(), "a descriptor is listed twice");
  }

  @Test
  void everyCallbackHasAnUpcallAndAReflectionEntry() {
    Set<String> upcalls = signatures("upcalls");
    List<JsonValue> direct = root.obj("foreign").arr("directUpcalls");
    List<JsonValue> methods = root.arr("reflection").get(0).asObj().arr("methods");
    assertEquals("io.github.stringdotjar.jwebgpu.ffm.FfmCallbacks", root.arr("reflection").get(0).asObj().str("type"));
    assertEquals(spec.callbacks().size(), direct.size());
    assertEquals(spec.callbacks().size(), methods.size());
    for (int i = 0; i < spec.callbacks().size(); i++) {
      Callback cb = spec.callbacks().get(i);
      JsonValue.Obj d = direct.get(i).asObj();
      String name = "on" + Naming.pascal(cb.specName());
      assertEquals(name, d.str("method"), cb.cName());
      assertEquals("io.github.stringdotjar.jwebgpu.ffm.FfmCallbacks", d.str("class"));
      assertEquals(name, methods.get(i).asObj().str("name"));
      assertTrue(upcalls.contains(signature(d)), cb.cName());
      assertEquals(cb.params().size(), d.arr("parameterTypes").size(), cb.cName());
      assertEquals(cb.params().size(), methods.get(i).asObj().arr("parameterTypes").size(), cb.cName());
      assertEquals("void", d.str("returnType"));
    }
  }

  @Test
  void everyDescriptorInTheGeneratedJavaIsRegistered() {
    Set<String> down = signatures("downcalls");
    Set<String> up = signatures("upcalls");
    int seen = 0;
    for (GeneratedFile f : FfmEmitter.emit(spec)) {
      String layout = null;
      for (String line : f.source().split("\\n")) {
        Matcher lm = LAYOUT_LINE.matcher(line.strip());
        if (lm.matches()) {
          layout = lm.group(1);
        }
        Matcher m = DESCRIPTOR.matcher(line.strip());
        if (!m.find()) {
          continue;
        }
        Matcher fm = FUNCTION.matcher(m.group(2));
        assertTrue(fm.find(), line);
        String args = fm.group(2);
        if (args.equals("LAYOUT")) {
          args = layout;
        }
        String text = translate(fm.group(1).equals("ofVoid"), args);
        assertTrue((m.group(1).equals("Native.handle") ? down : up).contains(text), f.path() + ": " + text);
        seen++;
      }
    }
    assertTrue(seen > 100, "the scan saw only " + seen + " descriptors");
  }

  @Test
  void everyLayoutUsesTheMetadataGrammar() {
    for (String section : List.of("downcalls", "upcalls", "directUpcalls")) {
      for (JsonValue v : root.obj("foreign").arr(section)) {
        JsonValue.Obj entry = v.asObj();
        assertTrue(valid(entry.str("returnType")), entry.str("returnType"));
        for (JsonValue p : entry.arr("parameterTypes")) {
          assertTrue(valid(p.asString()), p.asString());
        }
      }
    }
  }

  @Test
  void theFileListIsStable() {
    assertEquals(files, GraalEmitter.emit(spec));
    assertEquals(2, files.size());
    assertFalse(json.contains("\t"));
    assertTrue(json.chars().allMatch(c -> c < 0x80));
  }

  @Test
  void metadataHead() {
    Golden.check("GraalMetadata.head.json.txt", Golden.head(json, 24));
  }

  @Test
  void propertiesEnableNativeAccess() {
    Golden.check("GraalNativeImage.properties.txt", file("native-image.properties"));
  }

  private static boolean valid(String layout) {
    return layout.equals("void") || TOKENS.matcher(layout).replaceAll("").isEmpty() && !layout.isEmpty();
  }

  private static String translate(boolean isVoid, String args) {
    String t = args
        .replace("MemoryLayout.structLayout(", "struct(")
        .replaceAll("MemoryLayout\\.paddingLayout\\(([0-9]+)\\)", "padding($1)")
        .replace("ValueLayout.JAVA_LONG", "jlong")
        .replace("ValueLayout.JAVA_INT", "jint")
        .replace("ValueLayout.JAVA_SHORT", "jshort")
        .replace("ValueLayout.JAVA_FLOAT", "jfloat")
        .replace("ValueLayout.JAVA_DOUBLE", "jdouble")
        .replace("ValueLayout.JAVA_BYTE", "jbyte")
        .replace("Native.STRING_VIEW", "struct(jlong, jlong)")
        .replace("Native.CALLBACK_INFO", "struct(jlong, jint, padding(4), jlong, jlong, jlong)");
    List<String> parts = split(t);
    if (isVoid) {
      return "void (" + String.join(", ", parts) + ")";
    }
    return parts.get(0) + " (" + String.join(", ", parts.subList(1, parts.size())) + ")";
  }

  private static List<String> split(String text) {
    List<String> parts = new ArrayList<>();
    int depth = 0;
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '(') {
        depth++;
      } else if (c == ')') {
        depth--;
      } else if (c == ',' && depth == 0) {
        parts.add(text.substring(start, i).strip());
        start = i + 1;
      }
    }
    if (!text.isBlank()) {
      parts.add(text.substring(start).strip());
    }
    return parts;
  }
}
