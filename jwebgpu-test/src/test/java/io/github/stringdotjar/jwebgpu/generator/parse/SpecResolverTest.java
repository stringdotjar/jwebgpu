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

import io.github.stringdotjar.jwebgpu.generator.model.BitflagValue;
import io.github.stringdotjar.jwebgpu.generator.model.EnumValue;
import io.github.stringdotjar.jwebgpu.generator.model.FunctionKind;
import io.github.stringdotjar.jwebgpu.generator.model.GpuFunction;
import io.github.stringdotjar.jwebgpu.generator.model.InitValue;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.Spec;
import io.github.stringdotjar.jwebgpu.generator.model.Struct;
import io.github.stringdotjar.jwebgpu.generator.model.StructKind;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests the expansion rules of the resolver on tiny hand-written specifications. */
class SpecResolverTest {

  private static final String ENUMS = """
      "enums": [
        {"name": "s_type", "entries": [null, {"name": "ext_one"}]},
        {"name": "callback_mode", "entries": [null, {"name": "a"}, {"name": "b"}]},
        {"name": "color", "entries": [{"name": "undefined"}, {"name": "red"}, null, {"name": "blue", "value": 7}]},
        {"name": "plain", "entries": [null, {"name": "first"}, null, {"name": "third"}]}
      ],
      """;

  private static final String MINI = """
      {
        "name": "webgpu", "copyright": "c", "doc": "d", "enum_prefix": 0,
        "constants": [{"name": "my_max", "value": "uint32_max", "doc": "Max."}, {"name": "my_num", "value": "16"}],
      """ + ENUMS + """
        "bitflags": [{"name": "flags", "entries": [
          {"name": "none"}, {"name": "a"}, {"name": "b"},
          {"name": "both", "value_combination": ["a", "b"]},
          {"name": "big", "value": "0x100000000"}]}],
        "structs": [
          {"name": "future", "type": "standalone", "members": [{"name": "id", "type": "uint64"}]},
          {"name": "holder", "type": "extensible", "members": [
            {"name": "items", "type": "array<struct.item>", "pointer": "immutable"},
            {"name": "c", "type": "enum.color"},
            {"name": "f", "type": "bitflag.flags"},
            {"name": "ok", "type": "bool", "default": true},
            {"name": "n", "type": "nullable_string"},
            {"name": "raw", "type": "c_void", "pointer": "mutable", "optional": true}]},
          {"name": "item", "type": "standalone", "members": [{"name": "v", "type": "float32", "default": 2}]},
          {"name": "ext_one", "type": "extension", "extends": ["holder"], "members": []}
        ],
        "callbacks": [{"name": "done", "style": "callback_mode", "doc": "Done.",
                       "args": [{"name": "value", "type": "uint32"}]}],
        "functions": [],
        "objects": [{"name": "thing", "methods": [
          {"name": "go", "callback": "callback.done", "args": []},
          {"name": "get_values", "args": [{"name": "values", "type": "array<uint32>", "pointer": "mutable"}]}]}]
      }
      """;

  private static Spec mini() {
    return SpecLoader.loadString(MINI);
  }

  private static String errorOf(String json) {
    return assertThrows(SpecException.class, () -> SpecLoader.loadString(json)).getMessage();
  }

  private static String mutate(String from, String to) {
    assertTrue(MINI.contains(from), from);
    return MINI.replace(from, to);
  }

  @Test
  void enumValuesAreTheListIndexUnlessExplicit() {
    Spec spec = mini();
    List<EnumValue> color = spec.enumType("WGPUColor").values();
    assertEquals(3, color.size());
    assertEquals(0, color.get(0).value());
    assertEquals(1, color.get(1).value());
    assertEquals(7, color.get(2).value());
    assertEquals("WGPUColor_Blue", color.get(2).cName());

    List<EnumValue> plain = spec.enumType("WGPUPlain").values();
    assertEquals(1, plain.get(0).value());
    assertEquals(3, plain.get(1).value());
  }

  @Test
  void enumPrefixGoesIntoTheHighBits() {
    Spec spec = SpecLoader.loadString(mutate("\"enum_prefix\": 0", "\"enum_prefix\": 5"));
    assertEquals(0x00050000, spec.enumType("WGPUColor").values().get(0).value());
    assertEquals(0x00050007, spec.enumType("WGPUColor").values().get(2).value());
  }

  @Test
  void bitflagsGetPowersOfTwoCombinationsAndExplicitValues() {
    List<BitflagValue> v = mini().bitflag("WGPUFlags").values();
    assertEquals(0L, v.get(0).value());
    assertEquals(1L, v.get(1).value());
    assertEquals(2L, v.get(2).value());
    assertEquals(3L, v.get(3).value());
    assertTrue(v.get(3).combination());
    assertEquals(0x100000000L, v.get(4).value());
    assertEquals("WGPUFlags_Big", v.get(4).cName());
  }

  @Test
  void arraysBecomeCountAndData() {
    Struct holder = mini().struct("WGPUHolder");
    List<String> names = holder.fields().stream().map(Member::name).toList();
    assertEquals(List.of("nextInChain", "itemCount", "items", "c", "f", "ok", "n", "raw"), names);
    assertEquals(MemberRole.ARRAY_COUNT, holder.field("itemCount").role());
    assertEquals("WGPUItem const *", holder.field("items").type().declaration());
    assertEquals(InitValue.Kind.NUMBER, holder.field("itemCount").init().kind());
    assertEquals(InitValue.Kind.NULL_POINTER, holder.field("items").init().kind());
  }

  @Test
  void defaultsFollowTheHeaderRules() {
    Struct holder = mini().struct("WGPUHolder");
    assertEquals("WGPUColor_Undefined", holder.field("c").init().text());
    assertEquals("WGPUFlags_None", holder.field("f").init().text());
    assertEquals("WGPU_TRUE", holder.field("ok").init().text());
    assertEquals("WGPU_STRING_VIEW_INIT", holder.field("n").init().text());
    assertEquals("NULL", holder.field("raw").init().text());
    assertTrue(holder.field("raw").nullable());
    assertEquals("2.f", mini().struct("WGPUItem").field("v").init().text());
  }

  @Test
  void enumsWithoutUndefinedDefaultToAZeroCast() {
    String json = mutate("{\"name\": \"c\", \"type\": \"enum.color\"}", "{\"name\": \"c\", \"type\": \"enum.plain\"}");
    assertEquals(
        "_wgpu_ENUM_ZERO_INIT(WGPUPlain)", SpecLoader.loadString(json).struct("WGPUHolder").field("c").init().text());
  }

  @Test
  void extensionsAndTheirTargetsAreLinked() {
    Spec spec = mini();
    Struct ext = spec.struct("WGPUExtOne");
    assertEquals(StructKind.EXTENSION, ext.kind());
    assertEquals("WGPUSType_ExtOne", ext.sType());
    assertEquals(1, ext.sTypeValue());
    assertEquals(List.of("WGPUHolder"), ext.extendsStructs());
    assertEquals(List.of("WGPUExtOne"), spec.struct("WGPUHolder").extensions());
    assertEquals(MemberRole.CHAIN_HEADER, ext.fields().get(0).role());
  }

  @Test
  void callbacksGetAnInfoStructWithAModeField() {
    Spec spec = mini();
    Struct info = spec.struct("WGPUDoneCallbackInfo");
    assertEquals(StructKind.CALLBACK_INFO, info.kind());
    assertEquals(
        List.of("nextInChain", "mode", "callback", "userdata1", "userdata2"),
        info.fields().stream().map(Member::name).toList());
    assertEquals(
        List.of("value", "userdata1", "userdata2"),
        spec.callback("WGPUDoneCallback").params().stream().map(Member::name).toList());
  }

  @Test
  void asyncMethodsEndWithTheInfoAndReturnAFuture() {
    GpuFunction go = mini().function("wgpuThingGo");
    assertTrue(go.isAsync());
    assertEquals(List.of("thing", "callbackInfo"), go.params().stream().map(Member::name).toList());
    assertEquals("WGPUDoneCallbackInfo", go.params().get(1).type().declaration());
    assertEquals("WGPUFuture", go.returns().declaration());
  }

  @Test
  void methodsAreSortedAndFollowedByAddRefAndRelease() {
    Spec spec = mini();
    List<String> names = spec.object("WGPUThing").methods().stream().map(GpuFunction::cName).toList();
    assertEquals(List.of("wgpuThingGetValues", "wgpuThingGo", "wgpuThingAddRef", "wgpuThingRelease"), names);
    assertEquals(FunctionKind.ADD_REF, spec.function("wgpuThingAddRef").kind());
    GpuFunction values = spec.function("wgpuThingGetValues");
    assertEquals(
        List.of("thing", "valueCount", "values"), values.params().stream().map(Member::name).toList());
    assertEquals("uint32_t *", values.params().get(2).type().declaration());
  }

  @Test
  void constantsAreResolved() {
    Spec spec = mini();
    assertEquals("UINT32_MAX", spec.constant("WGPU_MY_MAX").cValue());
    assertEquals("Max.", spec.constant("WGPU_MY_MAX").doc());
    assertEquals("0x10UL", spec.constant("WGPU_MY_NUM").cValue());
  }

  @Test
  void structsAreOrderedByDependency() {
    List<String> order = mini().structs().stream().map(Struct::cName).toList();
    assertTrue(order.indexOf("WGPUItem") < order.indexOf("WGPUHolder"));
    assertTrue(order.indexOf("WGPUExtOne") < order.indexOf("WGPUHolder"));
    assertEquals("WGPUStringView", order.get(0));
    assertEquals("WGPUChainedStruct", order.get(1));
  }

  @Test
  void unknownTypesAreRejected() {
    assertTrue(errorOf(mutate("\"type\": \"enum.color\"", "\"type\": \"enum.nope\"")).contains("enum.nope"));
    assertTrue(errorOf(mutate("\"type\": \"float32\"", "\"type\": \"float128\"")).contains("float128"));
  }

  @Test
  void unsupportedFeaturesAreRejectedLoudly() {
    assertTrue(
        errorOf(mutate("\"constants\":", "\"typedefs\": [{\"name\": \"t\", \"type\": \"uint32\"}], \"constants\":"))
            .contains("typedefs"));
    assertTrue(
        errorOf(mutate("{\"name\": \"item\",", "{\"name\": \"item\", \"namespace\": \"ext\",")).contains("namespace"));
    assertTrue(
        errorOf(mutate("{\"name\": \"plain\",", "{\"name\": \"plain\", \"extended\": true,")).contains("extended"));
  }

  @Test
  void inconsistentSpecificationsAreRejected() {
    assertTrue(errorOf(mutate("\"extends\": [\"holder\"]", "\"extends\": [\"ghost\"]")).contains("ghost"));
    assertTrue(errorOf(mutate("{\"name\": \"ext_one\"}", "{\"name\": \"other\"}")).contains("WGPUSType_ExtOne"));
    assertTrue(errorOf(mutate("\"pointer\": \"immutable\"}", "\"pointer\": \"\"}")).contains("pointer"));
    assertTrue(errorOf(mutate("\"value_combination\": [\"a\", \"b\"]", "\"value_combination\": [\"a\", \"zzz\"]"))
        .contains("zzz"));
    assertTrue(errorOf(mutate("\"value\": 7", "\"value\": 70000")).contains("16 bits"));
    assertTrue(errorOf(mutate("\"style\": \"callback_mode\"", "\"style\": \"mystery\"")).contains("mystery"));
    assertTrue(errorOf(mutate("\"default\": true", "\"default\": \"maybe\"")).contains("maybe"));
  }

  @Test
  void duplicateNamesAreRejected() {
    assertTrue(errorOf(mutate("{\"name\": \"item\",", "{\"name\": \"holder\",")).contains("duplicate"));
  }

  @Test
  void wrongJsonShapesBecomeSpecExceptions() {
    assertTrue(errorOf(mutate("\"name\": \"webgpu\"", "\"name\": 5")).contains("malformed"));
  }

  @Test
  void loadedSpecIsImmutable() {
    Spec spec = mini();
    assertThrows(UnsupportedOperationException.class, () -> spec.structs().clear());
    assertThrows(UnsupportedOperationException.class, () -> spec.struct("WGPUHolder").fields().clear());
  }
}
