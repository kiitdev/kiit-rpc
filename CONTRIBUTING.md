# Contributing

Thanks for your interest in kiit-rpc.

## Where things stand right now

<!-- TODO: current maturity ("early", "just reached 1.0.x", etc.) and whether general PRs are
     accepted yet. This isn't a permanent stance, just a "not yet" while the design settles. -->

## What's welcome right now

- **Bug reports.** If something is broken, incorrect, or the docs don't match the actual
  behavior, please open an [issue](../../issues).
- **Design feedback.** Disagree with a naming choice, a builder signature, or a default? Open a
  [Discussion](../../discussions). This is genuinely useful, and it's the best way to influence
  where the project goes next.
- **Questions.** Also welcome in Discussions, not Issues.

## Language ports

If you're interested in porting this to another language, please start with a Discussion, not
a PR. Talking through the approach first, what stays faithful to the original design vs. what
should adapt to the target language's own idioms, keeps everyone's effort from being wasted, and
gives the port a real shot at becoming an official, linked module rather than staying disconnected.

kiit-rpc depends on kiit-codes, kiit-result, kiit-identity, and kiit-inputs for their status taxonomy,
result type, and shared call/input vocabulary. A port only makes sense alongside real ports of
those same modules in the same language, see their own CONTRIBUTING.md for the language-port
criteria they already apply.

**Ports hosted here need to genuinely enforce the same guarantees the design depends on**, not
just a naming convention that happens to resemble it. This is about design principles, not
gatekeeping — an independent, unofficial implementation in any language is always fine to build on
your own. This only concerns what gets hosted as an official Kiit port.

## Before opening a PR

If you've discussed something in an Issue or Discussion and a PR would be welcome, please make
sure:

- The PR references the Discussion or Issue it came out of.
- Tests are included for any behavioral change.
- The change is scoped narrowly, one concern per PR.

PRs opened without prior discussion may be closed and asked to start with a Discussion instead.
Not out of unfriendliness, just to keep effort aligned before code gets written.

## Build, test, and publish

See [BUILD.md](./BUILD.md) for local build, test, and publish instructions.

## Code of conduct

Be respectful. Disagreement about design is welcome and expected, personal attacks are not.
