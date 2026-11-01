# Contributing to jwebgpu

We welcome contributions! Whether you're fixing bugs, adding new features, or improving documentation, your help is appreciated. To ensure a smooth process for everyone, please follow these guidelines.

> [!TIP]
> You can learn more about the project structure and how to test it locally in the [Project Structure](ARCHITECTURE.md) and [Compiling](COMPILING.md) documents.

> [!IMPORTANT]
> ## Our Stance on AI
> We welcome AI assistance with open arms. We believe that AI -- if used correctly -- is an incredibly powerful tool, which can be an enhancing
> aid for development; however, we put emphasis on "assistance". While AI can be incredibly useful, it's not a replacement.
> You can't outsource to AI with a brain if you don't have one in the first place.
>
> To ensure we have a stable codebase, we ask everyone to follow these simple rules when using AI in their workflow:
>
> 1. ***You must know what the code does before you request to merge it***. There are no excuses for this. If your code is broken,
> not documented, unoptimized, or overall just unreadable, we will request you to make changes; and if you can't, then we will
> close your pull request.
>
> 2. We don't require you to mention whether you used AI to help you by default. We believe that the core of software engineering
> isn't just about writing code, but rather it's about solving problems for the benefit of others; however, if a maintainer at any
> point asks you about whether you used AI to help you, we request you to be honest and transparent about it.
>
> 3. If your code is broken or rejected, ***it is not the AI's fault, it is yours***. AI is useful, but it can't do the thinking for you.
> 
> 4. We do not mind if you create pull request *descriptions* with AI. As long as the information provided is accurate and there's clear evidence 
> that the pull request fulfills the task it's for, it's okay by us; *however*, you're **not allowed to use AI to comment in the PR conversation or 
> any area where it requires human communication.** That must be done by you. It's extremely rude and unethical to use a chatbot to talk to other 
> human collaborators for you. (**NOTE**: The only exception to this rule is if you're a non-native English speaker, and you use AI to smooth out
> your messages, which is 100% okay by us. Just please let us know beforehand so we don't mistake your communication to be disrespectful!)
> 
> 5. AI-generated bug reports are **not allowed**. If  there's an issue with your program or the library itself, *you* are the one who should address 
> it. AI is allowed to find the bug itself, but you need to the one presenting it. This also includes feature requests for the same obvious reasons.

## Java runtime (JDK 25, Eclipse Temurin)

jwebgpu targets **Java 25** (LTS). For **local development and Gradle**, use **[Eclipse Temurin 25](https://adoptium.net/temurin/releases/?version=25)**
(the Adoptium distribution of OpenJDK). Temurin is a well-tested, freely available HotSpot build with strong community support and broad platform coverage.

**Do not** use Oracle's proprietary JDK. If `java -version` output mentions **Temurin** or **OpenJDK**, you are on a compatible build.

CI uses **Eclipse Temurin 25** via GitHub Actions so builds match that recommendation.

Full installation steps for Windows, macOS, and Linux are in [COMPILING.md](COMPILING.md) (Java section).

## Automated tests

Unit tests live in the **`jwebgpu-test`** Gradle module (`:jwebgpu-test:test`). Add new tests next to the area they cover (packages under
`io.github.stringdotjar.jwebgpu`). They use JUnit 5 and depend on the generator and the library modules.

## Workflow

> [!NOTE]
> While jwebgpu is in early development, the maintainer commits directly to `master` and does not use pull requests yet. The pull
> request workflow below is what outside contributions will follow once it is adopted.

To maintain a stable code base, we follow a specific branching model:

1. **Target Branch**: All pull requests must be made against the `master` branch.
2. **Build Checks**: Your PR **MUST** pass all automated build checks. If the build fails, **the PR will not beconsidered for merging until the issues are resolved.**
3. **Commit Messages**: Keep commit messages concise and descriptive. Use the imperative mood (e.g. "Add buffer label support" not "Added buffer label support"). If the change fixes an issue, reference it in the message (e.g. "Fix NPE when a texture view has no label (#123)").
4. **Scope**: Prefer one logical change per PR. Large features can be split into smaller, reviewable PRs (e.g. API first, then implementation).
5. **Discussion**: For large or breaking changes, consider opening an issue first to discuss the approach. For bugs, feel free to open a PR directly if the fix is clear.

## Coding Standards

jwebgpu aims to be a high-quality library. Please follow these standards so the codebase stays consistent and maintainable.

### Formatting

We use [EditorConfig](https://editorconfig.org) to keep indentation, line endings, and basic whitespace consistent across editors. The project root contains a `.editorconfig` file that defines:

- **Charset**: UTF-8
- **Line endings**: LF
- **Final newline**: Insert at end of file
- **Trailing whitespace**: Trim (except in Markdown, where it is left for line breaks)
- **Indentation**: 2 spaces (no tabs) for Java, Gradle, Kotlin, etc.
- **Line length**: 120 characters for Java and similar (see `.editorconfig` for scope)

EditorConfig only controls how your editor behaves when you type or when it applies basic rules (e.g. indent on Tab, trim on save). It does **not** run a full code formatter. For “Format” or format-on-save, your editor uses its own formatter (e.g. IntelliJ’s code style or a VS Code formatter). To avoid clashes, enable EditorConfig in your editor so its behavior matches the project, and use the Project code style where applicable.

#### IntelliJ setup 

- Go to **Settings -> Editor -> Code Style** and enable **“Enable EditorConfig support”** (on some versions it is under **Settings -> Editor -> General**).
- Use the **Project** code style scheme so EditorConfig takes precedence over personal/IDE defaults. The project’s `.editorconfig` also includes IntelliJ-specific options (e.g. `ij_java_*`) for brace style and imports.

Once EditorConfig is enabled, new and edited files will respect the project’s indentation and whitespace rules automatically.

#### Style rules (match the codebase)

- **Indentation**: 2 spaces (no tabs). Apply to Java, Gradle, and Markdown (where applicable).
- **Line length**: Prefer staying under 120 characters. Break long lines at natural points (e.g. after a comma, before an operator); avoid breaking in the middle of a word or string when possible.
- **Braces**: Opening brace on the same line as the declaration (K&R style), with a space before it. Single-statement blocks may stay on one line when readable.
- **Whitespace**: Trim trailing whitespace from every line. End each file with a single newline. Use a single blank line between methods and between logical sections; do not add multiple blank lines in a row unless the style already exists in that file.
- **Imports**: Use single-class imports (no `import foo.bar.*`). Spotless orders them for you: `com`, `org`, `io`, `java`, `javax`, `jdk`, everything else, then static imports, with a blank line between groups.

Match the existing style in the file you are editing. When in doubt, run `./gradlew spotlessApply` and rely on the build; we expect code to look like it was written by a single person. Spotless covers every Java module, including `jwebgpu-jni`, and you do not need the Android SDK or `-PincludeAndroid=true` for it to do so.

### Naming and Style

- **Types**: PascalCase for classes, interfaces, and enums (e.g. `GpuDevice`, `RenderPassDescriptor`).
- **Methods and variables**: camelCase (e.g. `createBuffer`, `bufferSize`). Use descriptive names; avoid single-letter names except for trivial loop indices or well-known math (e.g. `x`, `y`).
- **Constants**: UPPER_SNAKE_CASE for static final constants (e.g. `MAX_COLOR_ATTACHMENTS`).
- **Final**: Use `final` for parameters and local variables when the reference or value is not reassigned. This clarifies intent and helps avoid mistakes.

### When to Add Comments and Javadoc

**Javadoc (block comments starting with `/**`):**

- **Public API**: Every public class, interface, and public or protected method that is part of the library’s API should have Javadoc. This includes constructors, getters/setters that are part of the API, and overrides of public methods when the behavior differs or is non-obvious.
- **What to include**: A short summary of what the type or method does; for methods, `@param` for every parameter, `@return` when the return value is not void, `@throws` when the method can throw. Use `{@link ClassName}` or `{@link #methodName}` to reference other types or methods, and `{@code expression}` for code or constant names. Use `<p>` to separate paragraphs when the description is long.
- **Private and package-private**: Javadoc is optional. Add it when the method or class does something non-obvious, has non-trivial invariants, or is easy to misuse. Do not add Javadoc that only repeats the method name (e.g. "Gets the x" for `getX()`).

**Inline comments:**

- **Complex logic**: Use short comments to explain *why* something is done when the reason is not obvious from the code (e.g. a workaround, a non-obvious invariant, or a performance-sensitive choice). Prefer clear naming and small methods so simple to semi-complex code does not need comments.
- **Avoid noise**: Do not comment the obvious (e.g. "increment i" next to `i++`). Do not leave commented-out code in the final patch; remove it or explain in the PR why it must stay.
- **TODOs**: Use `// TODO: description` for temporary workarounds or follow-ups. Prefer opening an issue and referencing it in the TODO if the follow-up is non-trivial.

**Example - public API with Javadoc:**

```java
/**
 * Writes bytes into a buffer through the queue.
 *
 * <p>This is the per-frame way to upload data. Prefer it over mapping the buffer.
 *
 * @param buffer The buffer to write into.
 * @param offset The byte offset in the buffer where writing starts.
 * @param data The bytes to copy.
 * @param size How many bytes to copy.
 */
public void writeBuffer(GpuBuffer buffer, long offset, byte[] data, int size) { ... }
```

**Example - inline comment for non-obvious behavior:**

```java
// Fill the scratch segment in place so no descriptor memory is allocated per call.
for (int i = 0; i < desc.colorCount; i++) { ... }
```

### Code Quality

- **Single responsibility**: Keep methods and classes focused. If a method does more than one thing, consider splitting it or extracting helpers.
- **Meaningful names**: Names should reveal intent. Avoid magic numbers; use named constants or variables (e.g. `float duration = 0.5f` or `private static final float FADE_DURATION = 0.5f`).
- **Consistency**: Match the existing project style exactly (indentation, brace placement, spacing, ordering of modifiers). The codebase should look like it was written by a single person.

### Performance and Memory

- **Avoid allocations in hot paths**: Do not create new objects in code that runs every frame unless necessary. Allocations in hot paths cause GC pressure and can cause hitches.
- **Reuse descriptors and wrappers**: Descriptors are plain mutable objects that you create once and refill. Objects that WebGPU creates every frame (command encoders, pass encoders, surface textures) have overloads that refill an existing wrapper instead of allocating a new one.
- **No standard Java collections**: Standard Java collection types like `HashMap` and `List` are ***completely banned*** in the runtime modules (`jwebgpu-api`, `jwebgpu-ffm`, `jwebgpu-jni`, `jwebgpu-teavm`). Use plain arrays. The only exception is the build-time generator (`jwebgpu-generator`), since it never ships with an application.
- **Absolutely no reflection**: Reflection (including `ServiceLoader` and `Class.forName`) is ***not allowed to be used***. It completely breaks AOT compilers and isn't reliable. Backends are installed explicitly instead.

### Examples

**Good code (reuse, no per-frame allocation):**

```java
public class Renderer {

  private final RenderPassDescriptor pass = new RenderPassDescriptor(); // Created once, refilled every frame.

  public void frame(GpuTextureView target) {
    pass.colorAttachments[0].view = target;
    pass.colorAttachments[0].clearR = 0.1f;
    // ...
  }
}
```

**Bad code (allocation every frame):**

```java
public class Renderer {

  public void frame(GpuTextureView target) {
    RenderPassDescriptor pass = new RenderPassDescriptor(); // Bad: new allocation every frame.
    // ...
  }
}
```

**Good Javadoc (with good comments and formatting) on a public method:**

```java
/**
 * Creates a buffer on this device.
 *
 * @param desc Describes the size and usage of the buffer.
 * @return The new buffer. Release it when you no longer need it.
 */
public abstract GpuBuffer createBuffer(BufferDescriptor desc);
```

**Bad Javadoc (with bad comments and formatting) on a public method:**

```java
/**
 * makes a buffer lol
 *
 * @param desc its da desc
 * @return eh you can figure it out...
 */
public 
abstract GpuBuffer createBuffer(BufferDescriptor desc);
```

## Creating a Pull Request

A good PR is easy to review and merge.

- **Title**: Descriptive and brief (e.g. "Descriptors no longer throw a NullPointerException when a label is null"). Your PR title will be used in the changelogs when a new release is made!
- **Description**: Explain *what* was changed and *why*. If the PR relates to an issue, reference it (e.g. "Fixes #123"). For features, describe the intended use and any breaking changes.
- **Self-review**: Before submitting, run `./gradlew compileJava`, `./gradlew spotlessApply`, `./gradlew checkstyleMain`, `./gradlew javadocAll` and `./gradlew :jwebgpu-test:test` (and any other relevant tasks) and fix build failures. Ensure formatting and style match the rest of the project; see [Editor setup (use EditorConfig in your editor)](#editor-setup-use-editorconfig-in-your-editor) above to enable EditorConfig in your editor.
- **Tests**: If you add a new feature, include tests or clear steps to verify the behavior. For bug fixes, describe how to reproduce the bug and confirm the fix.
- **Review feedback**: Address review comments in new commits or by amending. Keep the discussion focused and update the PR description if the scope changes.
- **Size**: Please keep pull requests small and concise. If a pull request has thousands of changes, ***we will close it***.
