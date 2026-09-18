#!/usr/bin/env python3
"""Renders golden/synthetic.json and the synthetic fixtures as Kotlin source for kotlin-fhirpath's commonTest.

Kotlin source rather than a JSON file, because kotlin-fhirpath's non-JVM test targets cannot read files.
Usage: tools/render_kotlin.py <generator-commit> > MemberOfGoldenCases.kt
"""
import glob, json, sys

commit = sys.argv[1] if len(sys.argv) > 1 else "uncommitted"
golden = json.load(open("golden/synthetic.json"))
ref = golden["reference"]


def raw(obj):
    text = json.dumps(obj, separators=(",", ":"))
    assert "$" not in text and '"""' not in text
    return '"""' + text + '"""'


def fixtures(kind):
    return [json.load(open(p)) for p in sorted(glob.glob(f"fixtures/synthetic/{kind}/*.json"))]


out = []
out.append(f"""/*
 * Copyright 2026 Open Health Stack Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dev.ohs.fhir.fhirpath

// GENERATED. Do not edit by hand.
// Results recorded from the HL7 FHIRPath engine: ca.uhn.hapi.fhir:org.hl7.fhir.r5 {golden["hapiCoreVersion"]},
// no terminology server, code systems loaded. Generator: memberof-oracle @ {commit}.
// Fixtures sha256 {golden["fixturesSha256"]}.

/** A `memberOf` evaluation and the result the HL7 engine gave: "true", "false" or "empty". */
internal data class MemberOfCase(
  val id: String,
  val note: String,
  val resource: String,
  val expression: String,
  val hl7Result: String,
)
""")
for name, kind in (("memberOfCodeSystems", "codesystems"), ("memberOfValueSets", "valuesets")):
    out.append(f"internal val {name}: List<String> =\n  listOf(")
    out += [f"    {raw(r)}," for r in fixtures(kind)]
    out.append("  )\n")
out.append("internal val memberOfCases: List<MemberOfCase> =\n  listOf(")
for c in golden["cases"]:
    result = c["results"][ref]
    assert result in ("true", "false", "empty"), (c["id"], result)
    note = c["note"].replace('"', '\\"')
    expr = c["expression"].replace('"', '\\"')
    out.append(f'    MemberOfCase("{c["id"]}", "{note}", {raw(c["resource"])}, "{expr}", "{result}"),')
out.append("  )")
print("\n".join(out))
