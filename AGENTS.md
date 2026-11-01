# jwebgpu (project instructions for AI assistants)

---

## Project context (jwebgpu)

jwebgpu is a standalone Java WebGPU bindings library built on Google's Dawn (`webgpu.h`) for desktop and Android,
and on the browser's `navigator.gpu` for the web. One platform-neutral API (`jwebgpu-api`) sits on top of three thin
backends: FFM for desktop, JNI for Android and TeaVM for the web.

The bindings are **generated** from vendored, pinned specifications (`spec/`) by a build-time generator
(`jwebgpu-generator`), so the three backends cannot drift apart. See [ARCHITECTURE.md](ARCHITECTURE.md).

jwebgpu is a standalone library. It **never depends on or refers to any other project** and must stay usable by any
Java program. Until jwebgpu is published, other projects use it through a Gradle composite build.

The project is in early development and is not published yet.

---

## Collaboration before implementation

Treat the interaction as teamwork, not robotic task execution. Prefer brainstorming when the user's direction
is ambiguous.

- Before implementing anything (planning or coding):

  1. Ask yourself whether the requested design or refactor is actually good for jwebgpu.
  2. If it hurts jwebgpu, breaks invariants, or there is clearly a better path, **stop before editing files or running commands**.
  3. Explain why (pros and cons), suggest better alternatives, and ask whether the user still wants to proceed.
  4. If they confirm after that discussion, proceed as requested.

- If you are unsure about something, whether that would be for a library, a specific aspect of the codebase, etc., do **not** assume anything.
  Always verify first before making an assumption. If you are still unsure about something, ignore it and bring it up to the user when you're done completing a task.

---

## Explaining things for beginners and contributors

The project welcomes new contributors learning open source.

When explaining code or introducing patterns:

- Explain **why** before **how** (motivation before mechanics).
- Use analogies for complex systems; if the user gave no analogy topic, ask for one they like.
- End **complex** explanations with a short check-in question so you can verify understanding.
- Stay encouraging and professional. Assume intelligence but not deep familiarity with Java or jwebgpu quirks.

---

## Code quality (non-negotiables)

### Performance, Memory, and allocations

- **Do not allocate objects inside loops or in methods invoked every frame.** That rule is strict. Prefer reuse,
  indexed `for` loops, and pre-allocated scratch memory. Descriptors are created once and refilled, never rebuilt per frame.
- **Always put fields in the correct order for each class**. Follow the order below:

  1. `long`s and `double`s
  2. `int`s, `float`s, and object references
  3. `short`s and `char`s
  4. `boolean`s and `byte`s

- **Standard Java collections are completely banned at runtime**. They take up too much memory and allocate too many objects
  when they're used. jwebgpu has no collections package of its own, so use plain arrays (with a separate `count` field where
  a size varies). The only exception to this rule is build-time tools, which means `jwebgpu-generator`, since it does not impact
  an application's performance at runtime and never ships. The Checkstyle suppressions for the generator live in
  `gradle/checkstyle/suppressions.xml`.
- **Reflection is banned**. It breaks many platforms that require ahead-of-time compilation and is unstable for situations 
  like version bumps. If reflection must be used, don't touch the main area requiring it, and bring it up at the end of 
  your task, explaining why it's needed.
- **Do not use deprecated APIs**. If one *is* used, replace it with the modernized, recommended version instead.

### Coding style

- **Always put fields, modifiers, types, and methods in the correct order**. This keeps the code readable and consistent.
  Follow the orders below:

#### Modifiers

1. `public`
2. `protected`
3. default
4. `private`
5. `static`
6. `final`

#### Fields, Methods/Functions and Types

1. Fields (following the alignment padding rule!)
2. Constructors, with smallest to largest parameters top to bottom
3. Methods (if there are overloads, order them smallest to largest parameters top to bottom)
4. Simple Getter/Setter methods (below every other method)
5. Inner classes
6. Inner interfaces
7. Inner enums

#### Example

```java
/*
 * Copyright notice here...
 */
package io.github.stringdotjar.jwebgpu.example;

/**
 * Rich and detailed Javadoc here.
 *
 * <p>Other details here.
 */
public class PerformanceObject {

  public static final long GLOBAL_LIMIT = 5000L;
  public static final int MAX_RETRIES = 5;
  public static String systemNodeName = "PRIMARY_NODE";
  public static char systemMode = 'V';

  protected static double internalWeight = 42.42;

  static final double VERSION_STABILITY = 1.0;
  static final float GRAVITY_CONSTANT = 9.81f;

  private static Object sharedLock = new Object();
  private static byte systemHeader = 0x1;

  public final long createdAt;
  private double sensorValue;

  public int instanceId;
  protected float velocity;
  private String displayName;
  private String[] metadata;

  public short localFlags;
  private char categoryCode;

  public boolean isActive;
  private byte checksum;

  public PerformanceObject() {
    this(-1);
  }

  public PerformanceObject(int instanceId) {
    this(instanceId, "GENERIC_INSTANCE", 0.0f);
  }

  public PerformanceObject(int instanceId, String displayName, float velocity) {
    this(instanceId, displayName, velocity, false);
  }

  public PerformanceObject(int instanceId, String displayName, float velocity, boolean isActive) {
    this.createdAt = System.currentTimeMillis(); // Final field assigned here
    this.instanceId = instanceId;
    this.displayName = displayName;
    this.velocity = velocity;
    this.isActive = isActive;
  }

  public static void sayHello() {
    System.out.println("Hello, world!");
  }

  public void logMessage() {
    logMessage("Default system heartbeat check.");
  }

  public void logMessage(String msg) {
    logMessage(msg, 1);
  }

  public void logMessage(String msg, int level) {
    System.out.println("[" + level + "] " + msg);
  }

  protected void processData() {
    this.checksum = 0x00;
  }

  private float addNum(float f1, float f2) {
    return f1 + f2;
  }

  public String getDisplayName() {
    return displayName;
  }

  private static final class InternalConfig {
    private long timeout;
    private int bufferSize;
  }

  public interface StateListener {
    void onTransition(boolean success);
  }
  
  public enum Priority {
    LOW,
    MEDIUM,
    HIGH
  }
}
```

- When working in a backend module, always add getters and setters for logical fields that would need them. Backends
  should get the same treatment; their API is public as well. Power users should be able to leverage the backends
  however they wish, not just through the API module.

### Architecture and scope

- `jwebgpu-api` is the main surface typical programs use. Keep backend or platform quirks out of it: it must contain no FFM,
  JNI or JavaScript interop types. Abstract with interfaces where behavior differs per platform. Each interop technology
  appears only in its own backend module.
- Backends are installed explicitly (for example `JWebGpuFfm.install()`). Never discover them with `ServiceLoader`,
  `Class.forName` or any other reflection.
- Keep changes minimal: avoid unrelated files unless needed for the stated task.
- When working in the web backend (`jwebgpu-teavm`), do not write JavaScript- or WebAssembly-specific code. The
  web backend should be able to support both out of the box.

### Language and style

- Target **Java 25**. Prefer modern features (records, lambdas, modern `switch`) over legacy patterns. The exception is
  `jwebgpu-api`, `jwebgpu-jni` and anything else that must run on Android: do not use Java APIs that Android's runtime lacks.
- **Import** every type you use. Do **not** use star imports (`*`).
- Do **not** use fully qualified class names inline when a normal import would read cleanly.
- Empty or void-returning methods: opening brace on the same line per `.editorconfig` (example: `public void hook() {}`).
- Prefer **short** field and method names. If shortening a method name hides its meaning, use a concise name plus Javadoc instead of a long identifier.

### Finishing work

Before finishing a coding task, run (from the repository root):

1. Compiling: `./gradlew compileJava`
2. Spotless: `./gradlew spotlessApply`
3. Checkstyle: `./gradlew checkstyleMain`
4. Javadocs: `./gradlew javadocAll`
5. Unit tests: `./gradlew :jwebgpu-test:test`

If you touched `jwebgpu-jni`, also compile the Android module with `./gradlew -PincludeAndroid=true :jwebgpu-jni:assembleDebug`
(this needs the Android SDK; see [COMPILING.md](COMPILING.md)).

For now there are no pull requests (see "Working with Git" below), so there is no PR description to update.

---

## Documentation, comments, and Javadoc

Documentation should read like a **beginner-friendly handbook**, not an expert-only manual.

### Mechanics

- Use correct grammar and punctuation everywhere (comments, `@param`, `@return`, `@throws`, and so on).
- Stick to **ASCII** in prose when practical; avoid decorative punctuation like en dash, em dash, fancy arrows, or emojis. This applies
  to both source comments and Javadoc. Use a plain hyphen (-) only for compound adjectives; never use it as a sentence separator or
  stand-in for an em dash. This rule also applies to inline comments in build scripts. This allows the docs to be read easily
  on every device and requires you to use clarity over brevity. The Markdown docs are the only exception to this rule.
- Use **consistent capitalization and grammar** in prose and code.
- Every doc comment should always start with a single sentence, with detailed paragraphs following.
- Include the right Javadoc tags (`@param`, `@return`, `@throws`, ...) wherever they apply.
- Use nullability annotations (`@Nullable`, `@NotNull`) where they help tooling.
- Keep `@link` references valid or fix broken links.
- Follow `.editorconfig` for formatting.
- Add comments where either complexity would otherwise be hard to follow, or where code requires import context.
- Skip Javadoc on trivial, self-explanatory methods (such as plain getters/setters) unless there is subtle behavior.
- All source files should carry the project's standard copyright header (exceptions: `package-info.java`, `module-info.java` and build scripts).
- Use **American English** in docs. (e.g., "behavior" instead of "behaviour")
- After code changes that affect public behavior or APIs, **update relevant Markdown docs** in the repo.
- Don't use section comments (like `// ---`). The code should be easily navigable simply by how it's organized; section comments are just noise.
- If a class needs to be referenced in a `@link`, don't write out the full package: import it as a qualifier. This allows jwebgpu's Javadoc links to be
  easy to read and not a blue mess. (Example: **not** `{@link io.github.stringdotjar.jwebgpu.JWebGpu}`, just `{@link JWebGpu}` + `import` for qualifier if needed.)

### Generated code

Generated sources go into each module's `build/generated` directory and are never committed. Generated code must still pass
Spotless, Checkstyle and Javadoc doclint, so doc strings that come from the specifications are converted into Javadoc that
follows the rules in this section.

### Comments versus Javadocs

- For single-line comments, do **not** use Markdown tricks (bold with `**`, backticks around snippets, etc.). Reserve richer formatting for Javadoc.
- When naming methods inside comments, include parentheses (`someMethod()`). If parameters exist, but you are not spelling them out, use `anotherMethod(...)`. Example class-qualified form: `SomeClass.someMethod(...)`.

### Heavily used or critical APIs

For widely used classes, fields, methods, or anything central to correctness, include a **small usage example** with an analogy in Javadoc showing correct typical use.

---

## Working with Git

jwebgpu is in early development with a single maintainer, so for now **work is committed directly to `master`**.
There are no branches and no pull requests yet; the pull request workflow will be adopted later (the `.github/`
templates are already in place for that day).

- Prefer **small, focused commits** as you finish logical slices of work so history stays readable. For example, if the task
  involves a large refactor, **don't dump everything in one commit**; split each logical change into a separate commit.
- Your commit titles should be **short and descriptive**, not exceeding **72 characters**, and should not contain
  **keywords in front (e.g. `fix`, `feat`, `refactor`, etc.)**. They should also be **present** tense. Examples:
    - "Update README with more descriptive content"
    - "Fix typos in documentation and refactor the FFM scratch arena"
    - "Fix descriptor refill in the buffer wrapper"
    - "Add missing Javadoc to JWebGpu"
- **Never add a `Co-Authored-By` trailer** to a commit.
- Do not push unless the user asks you to.
- If the user has changes present in the working tree, **do not undo, modify or touch them**. Leave them as-is.
- Once pull requests are adopted, they should target the **`master`** branch, follow the
  [PR template](.github/PULL_REQUEST_TEMPLATE.md) exactly, and have past-tense titles written like a release note
  (for example "Added the desktop FFM backend with garbage-free descriptor marshalling").
