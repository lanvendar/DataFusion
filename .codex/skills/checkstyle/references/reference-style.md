# Reference Checkstyle Rules

This document translates the reference `codeStyle.md` and Checkstyle configuration into a portable rule summary, so the skill can use the conventions without relying on any absolute path.

## Rule priority

1. If the current repository contains its own Checkstyle XML or style guide, use that first.
2. Otherwise use the rules below as the baseline.
3. Treat Alibaba Java Coding Guidelines as supporting guidance when they match these rules.

## Baseline style goals

- Keep Java code consistent, readable, and review-friendly.
- Prefer small compliant edits over large formatting-only rewrites.
- When creating a new Java class, start with compliant naming, imports, braces, whitespace, and Javadoc shape so follow-up cleanup is unnecessary.
- Before commit or code submission, review modified Java files for style violations that are likely to be caught by Checkstyle.

## Translated baseline rules

### File and line rules

- Use UTF-8.
- Do not use tab characters.
- Java line length should stay within 150 characters.
- The line-length limit may ignore package lines, import lines, extends/implements lines, and common URL patterns.
- Applicable file extensions include `java`, `properties`, and `xml`.

### Forbidden patterns

- Do not use `System.out.println` in source code.

### Naming rules

- Package names must be lowercase and may include digits after the first character of each segment.
- Outer class names must match the Java file name.
- Enforce standard Java naming for:
  - types
  - members
  - parameters
  - lambda parameters
  - catch parameters
  - local variables
  - methods
  - constants
  - static non-final variables
- Type parameter names should be uppercase.
- Avoid long all-caps abbreviations inside camelCase names unless they are explicitly allowed.
- The allowed abbreviation set includes common terms such as `HTTP`, `URL`, `ID`, `DB`, `DAO`, `DTO`, `UUID`, `JSON`, `SQL`, and `SSL`.

### Import rules

- Prefer explicit imports.
- Avoid wildcard imports except where the config explicitly allows them.
- Remove unused imports.
- Remove redundant imports.

### Block and brace rules

- Empty blocks are generally not allowed unless they contain text where the configuration permits it.
- Empty catch blocks are only acceptable for intentionally ignored exceptions that use names like `expected` or `ignore`.
- Use braces for control blocks that require them.
- Keep left and right curly brace placement consistent with Checkstyle expectations.

### Javadoc rules

- Public methods should have Javadoc unless they are covered by allowed annotations or configuration exceptions.
- Types such as classes, interfaces, enums, and annotations should have Javadoc.
- Variable Javadoc is expected except for ignored names like `log`, `logger`, and `serialVersionUID`.
- Single-line Javadoc is discouraged.
- Javadoc must appear in a valid position.
- Summary text should be meaningful rather than boilerplate like "This method returns ...".
- Paragraphs inside Javadoc should follow proper paragraph formatting.
- `@param`, `@return`, `@throws`, and `@deprecated` tags should appear in the configured order.

### General coding rules

- If `equals()` is overridden, `hashCode()` should also be overridden.
- Missing `@Override` annotations should be added where required.
- Keep one statement per line.
- Avoid multiple variable declarations in one statement.
- `switch` statements should include a `default` branch when required.
- Avoid unintended fall-through.
- Do not use finalizers.
- Keep overloaded methods together in declaration order.
- Keep variable declaration usage distance reasonable.
- Prefer standard escape sequences over octal or unicode escapes when a simpler escape is available.

### Indentation and whitespace

- Follow consistent indentation.
- Maintain comment indentation.
- Use the configured array initialization and line-wrapping indentation.
- Keep whitespace after tokens and around operators where required.
- Avoid disallowed line wrapping.
- Avoid extra empty lines, including repeated empty lines inside class members.
- Keep separator wrapping consistent for dots, commas, ellipsis, array declarators, and method references.
- Keep generics whitespace correct.
- Avoid whitespace before tokens where forbidden.
- Keep parameter padding and parentheses padding compliant.
- Wrap operators according to the configured newline style.

### Structure and annotation rules

- Prefer one top-level class per file.
- Keep modifier order correct.
- Keep annotation placement consistent on types, methods, constructors, and variables.

## How the skill should apply these rules

- Read the target repository's checked-in rules first and use this document as fallback guidance.
- When fixing violations, modify only the requested files or the files directly implicated by the task.
- When creating a new Java class, proactively follow these rules so the new file is likely to pass style review.
- When the user mentions commit, merge request, review, or submission, treat style compliance as part of the task even if they did not explicitly say "checkstyle".
