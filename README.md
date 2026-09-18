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
