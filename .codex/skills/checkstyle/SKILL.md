---
name: checkstyle
description: Use this skill whenever the user asks to fix, align, explain, verify, or proactively satisfy Java code style and Checkstyle requirements. Trigger this skill not only for explicit Checkstyle violations, Alibaba Java coding guideline issues, Javadoc/style compliance, style XML files, style markdown documents, lint output, and code style inspection requests, but also when creating a new Java class, preparing code for commit or submission, or checking whether modified Java files meet repository style rules before review.
---

# Checkstyle Skill

Use this skill to handle code-style work driven by a project's Checkstyle configuration and related style documentation.

## What this skill does

- Read the project's style documentation and Checkstyle XML before changing code.
- Infer the important conventions that matter for the current task.
- Apply focused style fixes to the requested Java code.
- Help create new Java classes in a style-compliant way from the beginning.
- Help with pre-commit and pre-review style self-checks on modified Java files.
- Explain violations in concise, practical language when the user asks for analysis.
- Prefer minimal edits that satisfy the configured rules instead of broad refactors.

## Inputs this skill expects

You may be given one or more of the following:

- A Checkstyle XML file
- A style guide markdown file
- Checkstyle or IDE warning output
- One or more Java source files to inspect or edit
- A request like "fix checkstyle", "align with code style", or "explain this violation"
- A request to create a new Java class or scaffold new Java code
- A request made right before commit, merge, review, or code submission
- A request to perform a code style check, self-check, or pre-commit inspection

## Workflow

1. Identify the relevant style sources.
   - Look for files such as `CheckStyle*.xml`, `*checkstyle*.xml`, `codeStyle.md`, or similarly named repository style docs.
   - If the user already provided exact paths, use those first.
   - In the DataFusion repository, use `style/codeStyle.md` and `style/CheckStyle-13.0.0.xml` first.

2. Read before editing.
   - Read the Checkstyle configuration and any companion style documentation.
   - Extract only the rules that matter to the current file or violation.
   - Do not guess rules that are not supported by the checked-in config or docs.

3. Inspect the target Java code or planned code.
   - Read the specific files named by the user.
   - If the task is to create a new Java class, inspect nearby package structure and existing peer classes first so the new file starts compliant.
   - If the user only gave an error message, find the referenced file and nearby code.
   - If the request is for commit-time or review-time validation, focus on the modified Java files.
   - Keep changes narrow and local.

4. Fix or prevent style issues with the repository's conventions.
   - Prefer renaming, spacing, braces, Javadoc, import cleanup, and small structural edits when needed for compliance.
   - When creating new Java code, generate it in a style-safe way from the start instead of creating code first and cleaning it up later.
   - When the user is about to commit or submit code, treat the task as a pre-commit style gate and check for the most likely violations before finishing.
   - Do not use `@SuppressWarnings`, suppression XML, or `SuppressWarningsFilter` to bypass Checkstyle unless the user explicitly asks for a policy change.
   - Preserve behavior. Do not change logic unless required to satisfy an explicit style rule and the behavior remains equivalent.
   - Avoid unrelated cleanup.

5. Report clearly.
   - If you edited code, summarize the exact categories of fixes you applied.
   - If the task involved new Java class creation or pre-commit checking, explicitly say that the code was aligned for style compliance.
   - If you only analyzed, explain which style rules are relevant and why the code violates them.
   - When useful, reference the source rule file and the affected code locations.

## Priorities

1. Repository-local Checkstyle XML and style docs
2. Explicit user instructions
3. Established Java conventions already present in the touched file and nearby peer classes
4. Focus first on modified files and directly affected Java files when the task is tied to commit, review, or submission
5. General Alibaba Java guideline patterns only when they are clearly referenced by the repository docs

## Good defaults

- Prefer explicit imports over wildcard imports.
- Preserve package/type naming consistency.
- When creating a new class, inspect sibling classes in the same package first and follow their local structure where it does not conflict with Checkstyle.
- Keep line length within the configured limit when practical.
- Add or adjust Javadoc only where the rules require it.
- Maintain brace and whitespace conventions from the Checkstyle config.
- For commit-time or review-time work, prioritize changed files over broad repository-wide cleanup.
- Do not reformat entire files if a small targeted edit is enough.

## Trigger examples

This skill should strongly trigger for prompts like these:

- "Create a new Java DTO class and make sure it follows the project's Checkstyle rules."
- "I am about to commit these Java files. Please do a style check first."
- "Run a Checkstyle pass on this class and tell me what is still non-compliant."
- "I just changed the service and controller layers. Please do a pre-commit style self-check."
- "Create a new Java class in this package and keep the style consistent with the existing project structure."
- "Explain how to fix this Checkstyle error."
- "Before I open a PR, check whether the modified Java files follow the repository's code style."
- "Please align this new Java class with the package's existing conventions and Checkstyle requirements."

## Output expectations

When making changes:

- Briefly state which style sources were used.
- Summarize the violations fixed.
- If the task was new-class creation, mention that the file was created in a style-compliant shape.
- If the task was pre-commit or pre-review inspection, mention that the changed files were checked with style compliance in mind.
- Reference changed locations as `file_path:line_number` when possible.

When only explaining:

- Name the relevant rule or rule family.
- Quote or paraphrase the applicable requirement.
- Explain the minimal compliant fix.

## Project reference

If the repository contains a style reference under `references/`, read it before acting. In this skill bundle:

- Read `references/reference-style.md` for the baseline rule summary.
- Read `references/examples.md` for representative trigger examples and usage patterns.
