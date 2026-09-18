package oracle

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import java.io.File
import java.security.MessageDigest

private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

private fun sha256(files: List<File>): String {
  val md = MessageDigest.getInstance("SHA-256")
  files.sortedBy { it.path }.forEach { md.update(it.readBytes()) }
  return md.digest().joinToString("") { "%02x".format(it) }
}

private fun jsonFiles(dir: String): List<File> =
  File(dir).listFiles { f -> f.name.endsWith(".json") }!!.sortedBy { it.name }

/** A named way of setting HAPI up. Each gets a fresh context so nothing is cached across configurations. */
private class Config(val label: String, val make: () -> Engine, val withCodeSystems: Boolean)

private fun synthetic() {
  val codeSystems = jsonFiles("fixtures/synthetic/codesystems")
  val valueSets = jsonFiles("fixtures/synthetic/valuesets")
  val cases = syntheticCases()
  val configs =
    listOf(
      Config("hapiR5", { HapiR5() }, withCodeSystems = true),
      Config("hapiR5-noCodeSystem", { HapiR5() }, withCodeSystems = false),
      Config("hapiR4", { HapiR4() }, withCodeSystems = true),
      Config("hapiR4-noCodeSystem", { HapiR4() }, withCodeSystems = false),
    )
  val loadFailures = linkedMapOf<String, MutableList<String>>()
  val results: Map<String, List<Outcome>> =
    configs.associate { config ->
      val engine = config.make()
      val toLoad = (if (config.withCodeSystems) codeSystems else emptyList()) + valueSets
      toLoad.forEach { f ->
        engine.load(f.readText())?.let { loadFailures.getOrPut(config.label) { mutableListOf() }.add("${f.name}: $it") }
      }
      config.label to cases.map { engine.evaluate(it.resource, it.expression) }
    }

  val out =
    linkedMapOf(
      "generator" to "memberof-oracle",
      "hapiCoreVersion" to System.getProperty("hapiCoreVersion"),
      "terminologyServer" to "none (offline)",
      "reference" to "hapiR5",
      "fixturesSha256" to sha256(codeSystems + valueSets),
      "configurations" to
        linkedMapOf(
          "hapiR5" to "org.hl7.fhir.r5 FHIRPathEngine, CodeSystems loaded. The reference column.",
          "hapiR5-noCodeSystem" to "Same, CodeSystems withheld: how HAPI degrades when it cannot see the code system.",
          "hapiR4" to "org.hl7.fhir.r4 FHIRPathEngine, CodeSystems loaded.",
          "hapiR4-noCodeSystem" to "Same, CodeSystems withheld.",
        ),
      "loadFailures" to loadFailures,
      "cases" to
        cases.mapIndexed { i, case ->
          linkedMapOf(
            "id" to case.id,
            "note" to case.note,
            "expression" to case.expression,
            "resource" to JsonParser.parseString(case.resource),
            "results" to configs.associateTo(linkedMapOf()) { it.label to results.getValue(it.label)[i].value },
            "details" to
              configs
                .mapNotNull { c -> results.getValue(c.label)[i].detail?.let { c.label to it } }
                .toMap(linkedMapOf())
                .ifEmpty { null },
          ).filterValues { it != null }
        },
    )
  File("golden").mkdirs()
  File("golden/synthetic.json").writeText(gson.toJson(out) + "\n")

  loadFailures.forEach { (k, v) -> v.forEach { println("LOAD FAILURE [$k] $it") } }
  println("%-5s %-8s %-10s %-8s %-10s  %s".format("id", "R5", "R5-noCS", "R4", "R4-noCS", "note"))
  cases.forEachIndexed { i, c ->
    fun v(l: String) = results.getValue(l)[i].value.removePrefix("error:").take(10)
    println("%-5s %-8s %-10s %-8s %-10s  %s".format(c.id, v("hapiR5"), v("hapiR5-noCodeSystem"), v("hapiR4"), v("hapiR4-noCodeSystem"), c.note))
  }
}

fun main(args: Array<String>) {
  when (args.firstOrNull() ?: "synthetic") {
    "synthetic" -> synthetic()
    "who" -> who(args[1], args[2], args.getOrNull(3))
    "why" -> why(args[1], args[2], args[3], args[4], args.getOrNull(5))
    else -> error("unknown mode: ${args.first()}")
  }
}
