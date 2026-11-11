# Contributing to Pocket Box

Thanks for wanting to help.

## Reporting bugs and ideas

Bug reports and feature ideas are very welcome. Please use the issue templates (**Issues → New issue**). They ask for exactly what's needed to reproduce a problem: your phone, Android or GrapheneOS version, Pocket Box version and the steps.

Security problems are different: please report them privately, as [SECURITY.md](SECURITY.md) explains.

Pocket Box is offline by design. Requests for streaming, online lyrics or artwork, accounts or analytics won't be accepted.

## Code contributions

Pocket Box is source-available under the [PolyForm Strict License 1.0.0](LICENSE), which doesn't allow publishing modified versions. Because of that, **pull requests are by invitation only**. If you'd like to contribute code, open an issue first and describe what you'd like to change. If it's a fit, you'll be asked to confirm that you license your contribution to the author so it can be released as part of Pocket Box.

When you do contribute:

- Follow the existing structure (see [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)): UI → ViewModel → repository, state as immutable `StateFlow`s.
- Use the theme's colours, spacing, shapes and the shared components; never hard-code values in a screen.
- Add no permission, no network access and no dependency outside Google Maven and Maven Central's official AndroidX, Media3, Kotlin and JetBrains artifacts.
- Run `./gradlew testDebugUnitTest lintRelease` before submitting.
- Start every new source file with the same copyright line as the others.
