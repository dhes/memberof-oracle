package oracle

import org.hl7.fhir.utilities.npm.FilesystemPackageCacheManager

/** Outcome of one evaluation: "true", "false", "empty", "multiple:<n>" or "error:<ExceptionClass>". */
data class Outcome(val value: String, val detail: String? = null)

interface Engine {
  val label: String

  /** Returns null when loaded, or the reason HAPI refused the resource. */
  fun load(resourceJson: String): String?

  fun evaluate(resourceJson: String, expression: String): Outcome
}

private fun outcomeOf(values: List<String?>): Outcome =
  when (values.size) {
    0 -> Outcome("empty")
    1 -> Outcome(values.single() ?: "non-primitive")
    else -> Outcome("multiple:${values.size}", values.joinToString(","))
  }

private fun failure(t: Throwable): Outcome {
  val at = t.stackTrace.firstOrNull()?.let { "${it.className.substringAfterLast('.')}.${it.methodName}" }
  return Outcome("error:${t.javaClass.simpleName}", "${t.message?.take(200)} @ $at")
}

private val packages by lazy { FilesystemPackageCacheManager.Builder().build() }

/** HAPI's R5 engine. This is the maintained code path: the HL7 validator converts R4 content to R5 and runs this. */
class HapiR5(txServer: String? = null) : Engine {
  override val label = "hapiR5"
  val workerContext: org.hl7.fhir.r5.context.SimpleWorkerContext get() = context

  private val context =
    org.hl7.fhir.r5.context.SimpleWorkerContext.SimpleWorkerContextBuilder()
      .withAllowLoadingDuplicates(true)
      .fromPackage(packages.loadPackage("hl7.fhir.r5.core", "5.0.0"))
      .apply {
        setExpansionParameters(org.hl7.fhir.r5.model.Parameters())
        if (txServer == null) {
          setCanRunWithoutTerminology(true)
          setNoTerminologyServer(true)
        } else {
          connectToTx(this, txServer)
        }
      }
  private val parser = org.hl7.fhir.r5.formats.JsonParser()
  private val engine by lazy { org.hl7.fhir.r5.fhirpath.FHIRPathEngine(context) }

  override fun load(resourceJson: String): String? =
    try {
      context.cacheResource(parser.parse(resourceJson))
      null
    } catch (t: Throwable) {
      "${t.javaClass.simpleName}: ${t.message}"
    }

  override fun evaluate(resourceJson: String, expression: String): Outcome =
    try {
      outcomeOf(engine.evaluate(parser.parse(resourceJson), expression).map { it.primitiveValue() })
    } catch (t: Throwable) {
      failure(t)
    }
}

/** HAPI's R4 engine, recorded for comparison. It is the class cited in most discussions of memberOf. */
class HapiR4 : Engine {
  override val label = "hapiR4"
  private val context =
    org.hl7.fhir.r4.context.SimpleWorkerContext.fromPackage(packages.loadPackage("hl7.fhir.r4.core", "4.0.1")).apply {
      setCanRunWithoutTerminology(true)
      setExpansionProfile(org.hl7.fhir.r4.model.Parameters())
    }
  private val parser = org.hl7.fhir.r4.formats.JsonParser()
  private val engine by lazy { org.hl7.fhir.r4.fhirpath.FHIRPathEngine(context) }

  override fun load(resourceJson: String): String? =
    try {
      context.cacheResource(parser.parse(resourceJson))
      null
    } catch (t: Throwable) {
      "${t.javaClass.simpleName}: ${t.message}"
    }

  override fun evaluate(resourceJson: String, expression: String): Outcome =
    try {
      outcomeOf(engine.evaluate(parser.parse(resourceJson), expression).map { it.primitiveValue() })
    } catch (t: Throwable) {
      failure(t)
    }
}

private fun connectToTx(context: org.hl7.fhir.r5.context.SimpleWorkerContext, url: String) {
  context.connectToTSServer(
    org.hl7.fhir.r5.terminologies.client.TerminologyClientR5.TerminologyClientR5Factory(),
    url,
    "memberof-oracle",
    null,
    false,
  )
}
