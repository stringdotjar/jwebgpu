package io.github.stringdotjar.jwebgpu.generator.emit;

import io.github.stringdotjar.jwebgpu.generator.model.CType;
import io.github.stringdotjar.jwebgpu.generator.model.Member;
import io.github.stringdotjar.jwebgpu.generator.model.MemberRole;
import io.github.stringdotjar.jwebgpu.generator.model.TypeKind;

/**
 * Names and type mappings shared by the emitters of the JNI backend.
 *
 * <p>The backend lives in the package {@code io.github.stringdotjar.jwebgpu.jni}. Its object classes are called
 * {@code JniGpuQueue} and so on, and the class that marshals a struct is called {@code JniGpuQueueDescriptorStruct}.
 * Native calls go through one static {@code native} method per C function, all declared in the class {@code Jni} and
 * named exactly like the C function, with the raw Java types of the call: {@code long} for every handle and pointer,
 * {@code int} for enums and 32-bit numbers. A struct that C takes by value (a string view or a callback info) travels
 * as the {@code long} address of the struct in scratch memory, and the C glue copies it.
 */
final class JniNames {

  static final String PKG = "io.github.stringdotjar.jwebgpu.jni";

  private JniNames() {}

  /**
   * Returns the path of a generated class below the output directory.
   *
   * @param name The simple class name.
   * @return The path with forward slashes, such as {@code io/github/stringdotjar/jwebgpu/jni/JniGpuQueue.java}.
   */
  static String path(String name) {
    return PKG.replace('.', '/') + "/" + name + ".java";
  }

  /**
   * Returns the name of the class that implements an object.
   *
   * @param cName The C name, such as {@code WGPUQueue}.
   * @return The class name, such as {@code JniGpuQueue}.
   */
  static String object(String cName) {
    return "Jni" + Names.type(cName);
  }

  /**
   * Returns the name of the class that marshals a struct.
   *
   * @param cName The C name, such as {@code WGPUBufferDescriptor}.
   * @return The class name, such as {@code JniGpuBufferDescriptorStruct}.
   */
  static String struct(String cName) {
    return "Jni" + Names.type(cName) + "Struct";
  }

  /**
   * Returns the name of the constant that holds the offset of a field.
   *
   * @param field The C name of the field.
   * @return For example {@code OFF_NEXT_IN_CHAIN}.
   */
  static String offset(String field) {
    return "OFF_" + Names.snake(field);
  }

  /**
   * Returns the raw Java type that carries a parameter or return value across the native call.
   *
   * @param t The C type.
   * @param role The role of the member.
   * @return {@code long}, {@code int}, {@code float}, {@code double} or {@code short}.
   * @throws IllegalArgumentException If the type cannot be passed by value.
   */
  static String raw(CType t, MemberRole role) {
    if (t.isPointer()) {
      return "long";
    }
    return switch (t.kind()) {
      case BOOL, UINT32, INT32, ENUM -> "int";
      case UINT16, INT16 -> "short";
      case UINT64, USIZE, BITFLAG, OBJECT, FUNCTION_POINTER -> "long";
      case FLOAT32 -> "float";
      case FLOAT64 -> "double";
      case STRUCT -> "long";
      case CHAR, VOID -> throw new IllegalArgumentException("unsupported by-value type " + t);
    };
  }

  /**
   * Tells whether a member is the pointer plus size pair that carries raw bytes.
   *
   * @param m The member.
   * @return {@code true} for the {@code void *} data pointer of functions such as {@code wgpuQueueWriteBuffer}.
   */
  static boolean isDataPointer(Member m) {
    return "c_void_data_ptr".equals(m.specType());
  }

  /**
   * Tells whether a type is a struct that the API turns into a Java object (not a string view).
   *
   * @param t The C type.
   * @return {@code true} for structs other than {@code WGPUStringView}.
   */
  static boolean isStruct(CType t) {
    return t.kind() == TypeKind.STRUCT && !t.name().equals(Types.STRING_VIEW);
  }

  /**
   * Converts a Java type name used in the API into the name to put in the source, importing it if needed.
   *
   * @param w The writer of the file.
   * @param javaType A type text such as {@code int}, {@code GpuBuffer}, {@code GpuBuffer[]} or {@code ByteBuffer}.
   * @return The text to write.
   */
  static String type(SourceWriter w, String javaType) {
    String base = javaType.endsWith("[]") ? javaType.substring(0, javaType.length() - 2) : javaType;
    if (base.startsWith("Gpu")) {
      w.imp(Names.pkg() + "." + base);
    } else if (base.equals("ByteBuffer")) {
      w.imp("java.nio.ByteBuffer");
    }
    return javaType;
  }
}
