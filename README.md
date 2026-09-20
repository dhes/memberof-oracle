# memberof-oracle

Records what the HL7 FHIRPath engine (HAPI `org.hl7.fhir.core`) answers for the FHIRPath function `memberOf`, so that another implementation can be checked against it.

Built to support adding `memberOf` to ohs-foundation/kotlin-fhirpath (Kotlin Multiplatform FHIRPath engine). HAPI is JVM-only and heavy, so it stays here rather than in that repository's build.

## What it produces

- `golden/synthetic.json`: 59 cases over made-up code systems, four HAPI configurations per case. Deterministic: two runs are byte-identical. The `hapiR5` column is the reference.
- `tools/render_kotlin.py`: renders that golden and the fixtures as Kotlin source for a multiplatform test.
- `out/who/`: results over published WHO ValueSets read in place from a local package. **Gitignored.** They contain ICD-11, ATC, SNOMED CT and LOINC codes.

## Run

```
python3 tools/make_fixtures.py                      # rewrite fixtures/synthetic
./gradlew run                                       # synthetic grid, offline -> golden/synthetic.json
./gradlew run --args="who <packageDir> <targets.txt> [txServerUrl]"
./gradlew run --args="why <packageDir> <valueSetUrl> <system> <code> [txServerUrl]"
python3 tools/render_kotlin.py $(git rev-parse --short HEAD) > MemberOfGoldenCases.kt
```

Needs JDK 21 and the FHIR package cache (`~/.fhir/packages`) holding `hl7.fhir.r4.core#4.0.1` and `hl7.fhir.r5.core#5.0.0`.

## Pinned

HAPI core 6.9.4.1, set in `build.gradle.kts` and written into every output. Bump deliberately and regenerate.

## Findings so far (2026-09-18)

1. **Use the R5 engine as the reference.** The R4 engine (`org.hl7.fhir.r4.fhirpath.FHIRPathEngine`) throws a NullPointerException for a Coding that is in a ValueSet's expansion when the CodeSystem is not loaded, throws for string and uri focus, and ignores `|version`. The HL7 validator runs R4 content through the R5 engine anyway.
2. **HAPI without the CodeSystem is not an oracle.** With code systems withheld and no terminology server, the R5 engine answers `true` for Codings that are not in the set (an unvalidatable code is a warning, and `memberOf` only asks `isOk()`), and `false` for CodeableConcepts that are.
3. **With the CodeSystem loaded the answers are coherent**, and that is the golden.
4. **`memberOf` turns infrastructure failure into `false`.** A terminology-server error comes back as not-ok, which the function reports as `false`, indistinguishable from "not a member".

## Lessons for building an oracle for another FHIRPath function (2026-09-21)

Written down in place of a skill file. Parked, not planned: a scan of WHO's ten smart-immunizations Questionnaires found that `memberOf` was the only FHIRPath function they need that kotlin-fhirpath lacked (their whole footprint is `where`, `memberOf`, `%resource`, `or`, plus `today()` and `now()`). The other unimplemented functions matter to kotlin-fhirpath's conformance table, not to running WHO's forms. If one is ever needed, start here.

**What happened here, in order.** HAPI's R4 engine first: it threw a NullPointerException on a Coding found in an expansion when the code system was not loaded, explained by reading HAPI's source. Then HAPI's R5 engine: first answers were wrong or crashed until the worker context was given expansion parameters; after that a member Coding was `true`, the same code in a CodeableConcept `false`, and a non-member `true`, because an unvalidatable code is a warning and `memberOf` only asks `isOk()`. Loading the code systems made every answer coherent. That configuration became the reference; the other three are kept as columns. The reference engine is stock HAPI R5, not a modified one: what was engineered was its configuration and the fixtures.

| Configuration | Cases differing from the reference, of 59 |
|---|---|
| R5, code systems loaded | the reference |
| R5, code systems withheld | 19 |
| R4, code systems loaded | 13, 4 of them errors |
| R4, code systems withheld | 31, 16 of them errors |

**Lessons.**

1. The oracle is a configuration, not a library. One jar gave four sets of answers. Record engine, version, settings and what was loaded, in the golden itself.
2. Design the fixtures for the oracle. The made-up code systems exist so the oracle has what it needs to answer coherently. Measure the degraded regime separately; never mix it into the reference.
3. The oracle can be wrong. A disagreement is a finding. Every difference the implementation keeps gets a written reason in the test (six here).
4. When the oracle's answers look odd, read its source before believing or dismissing them.
5. Record four outcomes, `true`, `false`, `empty`, `error`, and catch `Throwable`, not `Exception`.
6. Pin the version, prove determinism (two runs byte-identical), keep the heavy JVM oracle out of the target repository, render the golden as Kotlin source so every platform checks it, keep licensed content out.
7. Check first whether the specification's own test suite already has cases. For `memberOf` it had none.

**Computed versus delegated functions.** `memberOf`, `subsumes`, `resolve` and `conformsTo` are delegated: the engine hands the question to a service, so comparing engines compares back ends and their configuration, and everything above applies. Functions such as `coalesce` or `pathname` are computed by the engine itself and can be compared engine to engine.

**Step zero for any function: check who implements it.** Tried 2026-09-21 with fhirpath.js 5.2.0 (2-second install, 20-line script) against HAPI R5 6.9.4.1:

| Function | HAPI R5 | fhirpath.js |
|---|---|---|
| `coalesce`, `defineVariable`, `getValue` | yes | yes |
| `pathname` | no | yes |
| `repeatAll`, `duration`, `difference` | no | no |
| `memberOf` | yes, in-process | only through a terminology server |

Two engines agreeing is a golden; one engine is a comparator, not an authority; no engine means the specification text and its test suite are the only authority, and the golden should say so. Not tried: normalising the two engines' outputs into one encoding (expected to be the real cost), the Firely .NET engine, and a terminology server's `$validate-code` as a second code path for `memberOf`, which would be worth doing before the kotlin-fhirpath pull request.

HAPI is not a "reference" for `memberOf` in any strong sense. It is the only mainstream engine with an in-process mode comparable to an on-device design, and it is what WHO's own toolchain (IG Publisher, validator) runs. It is also fragile: wrong both ways when the code system is missing, and it reports a server error as `false`.

