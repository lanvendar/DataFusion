# Checkstyle Skill Example Prompts

Use these examples as representative trigger cases for the skill.

## New Java class creation

- Create a new Java DTO class and make sure it follows the project's Checkstyle rules.
- Create a new Java service class in this package and keep the style consistent with nearby classes.
- Scaffold a new Java controller class and make it style-compliant from the beginning.
- Add a new Java enum in this module and follow the repository's naming and formatting conventions.

## Pre-commit or pre-review style checks

- I am about to commit these Java files. Please do a style check first.
- Before I open a PR, check whether the modified Java files follow the repository's code style.
- I just changed the service and controller layers. Please do a pre-commit style self-check.
- Review the Java files I touched and flag anything that is likely to fail Checkstyle.

## Explicit Checkstyle or style-fix requests

- Run a Checkstyle pass on this class and tell me what is still non-compliant.
- Explain how to fix this Checkstyle error.
- Align this Java file with the repository's Checkstyle configuration.
- Fix the Javadoc, imports, naming, and brace style issues in this file without changing behavior.

## Style-safe code generation

- Create a new Java class in this package and keep the style consistent with the existing project structure.
- Please align this new Java class with the package's existing conventions and Checkstyle requirements.
- Generate this Java class in a way that is ready for code review, including style compliance.
- Add the new Java class with minimal follow-up cleanup needed for style review.
