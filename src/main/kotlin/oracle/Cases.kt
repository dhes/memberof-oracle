package oracle

const val BASE = "http://example.org/fhirpath-memberof"

fun cs(name: String) = "$BASE/CodeSystem/$name"

fun vs(name: String) = "$BASE/ValueSet/$name"

/** One oracle case: a resource, an expression evaluated against it, and a note on what it probes. */
data class Case(val id: String, val note: String, val resource: String, val expression: String)

private fun coding(system: String?, code: String?): String =
  listOfNotNull(system?.let { "\"system\":\"$it\"" }, code?.let { "\"code\":\"$it\"" }).joinToString(",", "{", "}")

private fun obs(vararg codings: String, valueString: String? = null): String {
  // Observation.code is required (1..1). HAPI tolerates its absence; stricter parsers do not.
  val code =
    if (codings.isEmpty()) ",\"code\":{\"text\":\"unused\"}" else ",\"code\":{\"coding\":[${codings.joinToString(",")}]}"
  val value = valueString?.let { ",\"valueString\":\"$it\"" } ?: ""
  return "{\"resourceType\":\"Observation\",\"status\":\"final\"$code$value}"
}

private const val CODING = "Observation.code.coding"
private const val CONCEPT = "Observation.code"
private const val CODE = "Observation.code.coding.code"

fun syntheticCases(): List<Case> = buildList {
  var n = 0
  fun add(note: String, resource: String, focus: String, arg: String) {
    n++
    add(Case("S%03d".format(n), note, resource, "$focus.memberOf($arg)"))
  }
  fun url(name: String) = "'${vs(name)}'"

  // --- the WHO shape: enumerated multi-system compose plus a matching expansion
  val who = url("who-shape")
  add("Coding in set (first system)", obs(coding(cs("alpha"), "A1")), CODING, who)
  add("Coding in set (second system)", obs(coding(cs("beta"), "B2")), CODING, who)
  add("Coding: right system, code not in set", obs(coding(cs("alpha"), "A3")), CODING, who)
  add("Coding: code unknown to its system", obs(coding(cs("alpha"), "ZZ")), CODING, who)
  add("Coding: right code, wrong system", obs(coding(cs("gamma"), "A1")), CODING, who)
  add("Coding: right code, unknown system", obs(coding("$BASE/CodeSystem/nowhere", "A1")), CODING, who)
  add("Coding without system", obs(coding(null, "A1")), CODING, who)
  add("Coding without code", obs(coding(cs("alpha"), null)), CODING, who)
  add("CodeableConcept: single member", obs(coding(cs("alpha"), "A1")), CONCEPT, who)
  add("CodeableConcept: one member among non-members", obs(coding(cs("gamma"), "G2"), coding(cs("alpha"), "A2")), CONCEPT, who)
  add("CodeableConcept: no members", obs(coding(cs("gamma"), "G2"), coding(cs("alpha"), "A3")), CONCEPT, who)
  add("CodeableConcept without codings", "{\"resourceType\":\"Observation\",\"status\":\"final\",\"code\":{\"text\":\"free text\"}}", CONCEPT, who)
  add("bare code in set, unambiguous", obs(coding(cs("beta"), "B2")), CODE, who)
  add("bare code in set, code also exists in a system outside the set", obs(coding(cs("alpha"), "A1")), CODE, who)
  add("bare code not in set", obs(coding(cs("alpha"), "A3")), CODE, who)
  add("bare string in set", obs(valueString = "A1"), "Observation.value", who)
  add("bare string not in set", obs(valueString = "A3"), "Observation.value", who)
  add("bare uri focus", obs(coding(cs("alpha"), "A1")), "Observation.code.coding.system", who)
  add("empty focus", obs(), "Observation.category", who)
  add("focus with two Codings", obs(coding(cs("alpha"), "A1"), coding(cs("alpha"), "A2")), CODING, who)
  add("focus of an unsupported type (boolean)", obs(coding(cs("alpha"), "A1")), "Observation.code.exists()", who)
  add("unknown ValueSet url", obs(coding(cs("alpha"), "A1")), CODING, url("does-not-exist"))
  add("empty collection as argument", obs(coding(cs("alpha"), "A1")), CODING, "{}")
  add("integer as argument", obs(coding(cs("alpha"), "A1")), CODING, "1")

  // --- expansion only / compose only
  add("expansion only: member", obs(coding(cs("alpha"), "A1")), CODING, url("expansion-only"))
  add("expansion only: non-member", obs(coding(cs("alpha"), "A2")), CODING, url("expansion-only"))
  add("expansion only: CodeableConcept member", obs(coding(cs("alpha"), "A1")), CONCEPT, url("expansion-only"))
  add("expansion only: bare code member", obs(coding(cs("alpha"), "A1")), CODE, url("expansion-only"))
  add("compose only: member", obs(coding(cs("alpha"), "A2")), CODING, url("compose-only"))
  add("compose only: non-member", obs(coding(cs("alpha"), "A3")), CODING, url("compose-only"))
  add("compose only: wrong system", obs(coding(cs("gamma"), "A1")), CODING, url("compose-only"))
  add("compose only: bare code member", obs(coding(cs("alpha"), "A2")), CODE, url("compose-only"))

  // --- nested, abstract, inactive expansion entries
  add("nested expansion: parent", obs(coding(cs("beta"), "B1")), CODING, url("nested"))
  add("nested expansion: child", obs(coding(cs("beta"), "B1a")), CODING, url("nested"))
  add("nested expansion: sibling outside set", obs(coding(cs("beta"), "B2")), CODING, url("nested"))
  add("abstract entry itself", obs(coding(cs("beta"), "B1")), CODING, url("abstract"))
  add("child of abstract entry", obs(coding(cs("beta"), "B1a")), CODING, url("abstract"))
  add("inactive entry", obs(coding(cs("alpha"), "A3")), CODING, url("inactive"))
  add("active entry beside an inactive one", obs(coding(cs("alpha"), "A1")), CODING, url("inactive"))

  // --- exclude
  add("exclude: included and not excluded", obs(coding(cs("alpha"), "A1")), CODING, url("exclude"))
  add("exclude: excluded", obs(coding(cs("alpha"), "A2")), CODING, url("exclude"))

  // --- versions
  add("versioned: url|1.0.0 member", obs(coding(cs("alpha"), "A1")), CODING, "'${vs("versioned")}|1.0.0'")
  add("versioned: url|1.0.0 non-member", obs(coding(cs("alpha"), "A2")), CODING, "'${vs("versioned")}|1.0.0'")
  add("versioned: url|2.0.0 member", obs(coding(cs("alpha"), "A2")), CODING, "'${vs("versioned")}|2.0.0'")
  add("versioned: unversioned url, code only in 1.0.0", obs(coding(cs("alpha"), "A1")), CODING, url("versioned"))
  add("versioned: unversioned url, code only in 2.0.0", obs(coding(cs("alpha"), "A2")), CODING, url("versioned"))
  add("versioned: url|9.9.9 does not exist", obs(coding(cs("alpha"), "A1")), CODING, "'${vs("versioned")}|9.9.9'")
  add("unversioned ValueSet referenced with a version", obs(coding(cs("alpha"), "A1")), CODING, "'${vs("who-shape")}|1.0.0'")

  // --- shapes that need a CodeSystem to evaluate
  add("filter is-a: descendant", obs(coding(cs("beta"), "B1a")), CODING, url("filter"))
  add("filter is-a: the root itself", obs(coding(cs("beta"), "B1")), CODING, url("filter"))
  add("filter is-a: outside", obs(coding(cs("beta"), "B2")), CODING, url("filter"))
  add("whole system: a code of the system", obs(coding(cs("alpha"), "A4")), CODING, url("whole-system"))
  add("whole system: not a code of the system", obs(coding(cs("alpha"), "ZZ")), CODING, url("whole-system"))
  add("whole system: another system", obs(coding(cs("gamma"), "G2")), CODING, url("whole-system"))
  add("imported ValueSet: member", obs(coding(cs("alpha"), "A1")), CODING, url("import"))
  add("imported ValueSet: non-member", obs(coding(cs("alpha"), "A3")), CODING, url("import"))

  // --- expansion and compose disagree
  add("disagree: code only in compose", obs(coding(cs("alpha"), "A1")), CODING, url("disagree"))
  add("disagree: code only in expansion", obs(coding(cs("alpha"), "A2")), CODING, url("disagree"))
  add("disagree: code in neither", obs(coding(cs("alpha"), "A3")), CODING, url("disagree"))
}
