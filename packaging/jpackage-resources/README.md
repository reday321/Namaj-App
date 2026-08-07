# jpackage resource overrides

Files here replace the templates jpackage bundles internally. jpackage looks for each
override by an exact filename, so these names must not change.

| File | Replaces | Why we override it |
|---|---|---|
| `SalahGuardian.desktop` | `template.desktop` | The stock template derives `Name=` from `--name`, giving `SalahGuardian` with no space. It also omits `Keywords`, `GenericName` and `StartupWMClass`, so the app is hard to find in a menu search and its window may not be matched to its launcher. |

The uppercase words are substitution tokens filled in by jpackage; leave them as they
are. Only the lines that hard-code human-facing text are ours to set.
