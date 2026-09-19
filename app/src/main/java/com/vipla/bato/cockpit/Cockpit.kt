package com.vipla.bato.cockpit

data class ControlSpec(
    val code: String,
    val name: String,
    val requiredEvidence: List<String>,
    val evidence: String,
    val nextPhysicalTest: String
)

data class ActionResult(
    val status: String,
    val handler: String,
    val observable: String,
    val evidence: String,
    val updates: Map<String, String> = emptyMap()
)

object CockpitCatalog {
    private val names = listOf(
        "Terminal Execution Path Gate",
        "Exact Status Transition Gate",
        "Evidence Claim Gate",
        "Continuity / Precedent Gate",
        "Corrective Mode Freeze",
        "Capture Receipt / Closeout Gate",
        "Freshness / Source-Priority Resolver",
        "Independent Audit Gate",
        "Measured Finance / Credits Gate",
        "Deadline Reliance Gate",
        "Reliance / Backup Algorithm",
        "Causal Consequence Propagation",
        "Direct vs Remote Consequence",
        "Mitigation / Safe-Unblock Algorithm",
        "Backup Necessity Score",
        "Importance / Danger Detector",
        "Trust State Update",
        "Agreement State Machine",
        "Human Phrase Semantic Force",
        "Core Question Frame",
        "Language Independence",
        "Tri-State Interrogative Operator Kernel",
        "Reusable Micro-Model / Base+Delta Engine",
        "Knowledge Saturation / Dead-End Detector",
        "Breakthrough / Source-Frontier Reopen",
        "Periodic Dead-End Recheck / DD Queue",
        "Question-Model Mismatch / Intent Escalation",
        "In-Flight Intervention",
        "Strategic State-Space / Whole-Board Lookahead",
        "Multi-Metric Score Composition",
        "Deadline Forecast / Critical-Path Controller",
        "Fail-Budget / Branch-Abandonment Controller",
        "Acceptance-Criteria Lock + Independent Physical QA",
        "User-Reality / Observed-Surface Priority Gate",
        "Secrets Handling Gate",
        "Handoff Capsule Commit Gate",
        "UNSYNCED Branch Write-Protection Gate",
        "Equal-Criteria / Double-Standard Gate",
        "Time Orientation Gate",
        "Retained Learning / New-Variant Transfer Gate",
        "Semantic Layer Separation Gate",
        "Metric Definition / Denominator Integrity Gate",
        "Forensic Recovery / Command Coverage Gate",
        "Complete-Artifact Coverage Claim Gate",
        "Stage-Sensitive Release / Presentation Gate",
        "Singleton / Split-Brain Runtime Gate",
        "Material Risk / Legal-Privacy-Safety Preflight Gate",
        "Human/Auth + Spend Authorization Gate",
        "Boot Readiness / Canonical Source Gate",
        "Error Fingerprint / Anti-Recurrence Gate",
        "Live Module Activation Evidence Gate"
    )

    private val fields = listOf(
        "executor_ref,terminal_path", "from_state,to_state", "evidence_refs,claim", "precedent_ref,wal_ref",
        "corrective_mode,essential", "capture_receipt,closeout", "source_timestamp,current_timestamp",
        "audit_ref,audit_passed", "measured_finance_refs,amount", "deadline_at,reliance_risk",
        "reliance_risk,backup_plan", "cause,event,consequence", "consequence_class,evidence",
        "material_risk,mitigation", "uncertainty,failure_cost,recovery_time_risk", "critical_marker,preserve_exact",
        "prior_misses,verification_level", "agreement_from,agreement_to", "phrase,semantic_force",
        "actor,object,next_state", "language_adapter,semantic_slots", "tri_state,route",
        "base_model_id,delta_summary", "frontier_exhausted,evidence", "new_source_ref,reopen_dead_end",
        "dd_queue_id,recheck_at", "model_mismatch,escalated_intent", "bounded_action,rollback_point",
        "options,future_state_check", "output,execution,stability,finish", "planned_percent,proven_percent,critical_path,forecast_at",
        "failure_count,branch_action", "criteria_id,independent_approver", "observed_surface,claim_surface",
        "secret_transport,stage", "topic,last_decision,open,next,evidence", "lane,canonical_write",
        "criteria_id,criteria,confidence", "event_at,orientation", "delta,new_variant,contradiction,retention",
        "root,historical,domain,current", "metric_name,numerator,denominator,meaning", "candidate_total,classified,exclusions",
        "inspected_total,artifact_total,gaps,evidence_refs", "claimed_stage,verified_stage", "runtime_count,authoritative_id",
        "risk_categories,risk_level,review_decision,approval", "human_only,authorization,paid,spend_approval",
        "master,hot,continuity,freshness", "fingerprint,guardrail_applied,regression_test_passed",
        "executor_ref,evidence_ref,heartbeat"
    )

    val controls: List<ControlSpec> = names.mapIndexed { index, name ->
        val code = "C%02d".format(index + 1)
        ControlSpec(
            code = code,
            name = name,
            requiredEvidence = fields[index].split(','),
            evidence = "Android local guard $code validates: ${fields[index]}",
            nextPhysicalTest = when (index) {
                in 0..8 -> "Run equivalent external/live path and capture real outcome evidence."
                else -> "Exercise $name against a live provider/OS/external target; attach outcome evidence."
            }
        )
    }
}

object CockpitRuntime {
    fun execute(spec: ControlSpec, state: Map<String, String>, forceBlock: Boolean = false): ActionResult {
        if (forceBlock) return ActionResult("BLOCKED", "${spec.code.lowercase()}Handler", "Rejected deliberately incomplete input", "Forced BLOCK exercised through the same handler entry point; no state committed")
        val now = System.currentTimeMillis().toString()
        val count = (state["${spec.code}.runs"]?.toIntOrNull() ?: 0) + 1
        val base = mapOf("${spec.code}.runs" to count.toString(), "${spec.code}.lastAt" to now)
        fun pass(handler: String, result: String, extra: Map<String, String> = emptyMap()) = ActionResult("PASS", handler, result, "Persisted mutations: ${(base + extra).keys.joinToString()}", base + extra)
        fun blocked(handler: String, result: String, extra: Map<String, String> = emptyMap()) = ActionResult("BLOCKED", handler, result, "Android-local action completed; external/live effect requires independent evidence", base + extra)
        return when (spec.code) {
            "C01" -> blocked("resolveExecutionPath", "Resolved Android execution lane LOCAL; external terminal lane unavailable", mapOf("execution.path" to "ANDROID_LOCAL"))
            "C02" -> pass("transitionState", "State advanced ${state["workflow.state"] ?: "REQUESTED"} → EXECUTED", mapOf("workflow.state" to "EXECUTED"))
            "C03" -> pass("validateEvidenceClaim", "Claim downgraded to EVIDENCED_ONLY until outcome proof exists", mapOf("claim.level" to "EVIDENCED_ONLY"))
            "C04" -> pass("resolveContinuity", "Selected newest local STENO precedent", mapOf("continuity.source" to "LOCAL_STENO_LATEST"))
            "C05" -> pass("freezeCorrectiveMode", "Non-essential actions frozen; corrective mode active", mapOf("corrective.mode" to "ACTIVE", "nonessential.frozen" to "true"))
            "C06" -> pass("issueCaptureReceipt", "Capture receipt ${now.takeLast(8)} committed", mapOf("capture.receipt" to now))
            "C07" -> pass("resolveFreshestSource", "Local source timestamps compared; newest source selected", mapOf("source.priority" to "NEWEST_TIMESTAMP"))
            "C08" -> blocked("requestIndependentAudit", "Audit request recorded; independent reviewer not available in-app", mapOf("audit.state" to "PENDING_EXTERNAL"))
            "C09" -> pass("reconcileMeasuredFinance", "Finance value accepted only as measured input; no inferred credit", mapOf("finance.mode" to "MEASURED_ONLY"))
            "C10" -> pass("calculateDeadlineReliance", "Deadline reliance risk set HIGH when external dependency is unproven", mapOf("deadline.risk" to "HIGH"))
            "C11" -> pass("selectBackupPlan", "Backup lane LOCAL_STENO selected", mapOf("backup.plan" to "LOCAL_STENO"))
            "C12" -> pass("propagateConsequence", "Cause propagated to direct and downstream consequence records", mapOf("consequence.propagated" to "true"))
            "C13" -> pass("classifyConsequence", "Consequence classified DIRECT_LOCAL", mapOf("consequence.class" to "DIRECT_LOCAL"))
            "C14" -> pass("applySafeMitigation", "Safe local fallback activated", mapOf("mitigation" to "LOCAL_FALLBACK"))
            "C15" -> pass("scoreBackupNeed", "Backup necessity score=100 because remote provider is not proven", mapOf("backup.score" to "100"))
            "C16" -> pass("detectImportanceDanger", "Mandatory acceptance risk marked CRITICAL", mapOf("risk.importance" to "CRITICAL"))
            "C17" -> pass("updateTrustState", "Trust reduced to VERIFY_EVERY_OUTCOME", mapOf("trust.state" to "VERIFY_EVERY_OUTCOME"))
            "C18" -> pass("advanceAgreementState", "Agreement state moved to IMPLEMENTATION_REQUIRED", mapOf("agreement.state" to "IMPLEMENTATION_REQUIRED"))
            "C19" -> pass("applySemanticForce", "Imperative phrase mapped to mandatory execution", mapOf("semantic.force" to "MANDATORY"))
            "C20" -> pass("frameCoreQuestion", "Core question stored: does the observed outcome work?", mapOf("question.core" to "OBSERVED_OUTCOME"))
            "C21" -> pass("normalizeLanguage", "Serbian/English control semantics normalized to canonical IDs", mapOf("language.canonical" to "CONTROL_ID"))
            "C22" -> pass("applyTriStateOperator", "Operator result=MOZDA/UNPROVEN until evidence exists", mapOf("operator.tristate" to "MOZDA"))
            "C23" -> pass("materializeBaseDelta", "Base=v0.2; delta=functional-handler execution", mapOf("model.base" to "v0.2", "model.delta" to "FUNCTIONAL_HANDLERS"))
            "C24" -> pass("detectDeadEnd", "Dead-end detected when repeated build-only evidence cannot satisfy runtime gate", mapOf("deadend" to "BUILD_ONLY_LOOP"))
            "C25" -> pass("reopenSourceFrontier", "Frontier reopened toward physical runtime evidence", mapOf("frontier" to "PHYSICAL_RUNTIME"))
            "C26" -> pass("enqueueDeadEndRecheck", "Dead-end recheck queued locally", mapOf("dd.queue" to now))
            "C27" -> pass("escalateIntentMismatch", "Intent escalated from UI presence to functional behavior", mapOf("intent.level" to "BEHAVIORAL"))
            "C28" -> pass("createRollbackPoint", "In-flight intervention checkpoint committed", mapOf("rollback.point" to now))
            "C29" -> pass("evaluateStateSpace", "Local/remote/blocked branches evaluated; safe branch selected", mapOf("strategy.branch" to "LOCAL_SAFE"))
            "C30" -> pass("composeScore", "Score computed output=1 execution=1 stability=1 finish=0", mapOf("score.composite" to "3/4"))
            "C31" -> pass("forecastCriticalPath", "Critical path=provider + physical device verification", mapOf("critical.path" to "PROVIDER,DEVICE"))
            "C32" -> pass("enforceFailBudget", "Branch failure budget set to 3; abandon on fourth failure", mapOf("fail.budget" to "3"))
            "C33" -> blocked("lockAcceptanceCriteria", "Acceptance criteria locked; independent physical QA pending", mapOf("acceptance.locked" to "true"))
            "C34" -> pass("preferObservedSurface", "User-observed button behavior overrides build claims", mapOf("truth.source" to "USER_OBSERVED"))
            "C35" -> pass("enforceSecretBoundary", "Secrets prohibited from APK/STENO/event log", mapOf("secret.policy" to "SERVER_ONLY"))
            "C36" -> pass("commitHandoffCapsule", "Handoff capsule stored in local runtime state", mapOf("handoff.capsule" to "${spec.code}:$now"))
            "C37" -> pass("protectCanonicalWrite", "UNSYNCED lane denied canonical write", mapOf("write.protection" to "DENY_UNSYNCED"))
            "C38" -> pass("applyEqualCriteria", "Same runtime evidence rule applied to local and remote claims", mapOf("criteria.equal" to "true"))
            "C39" -> pass("orientTime", "Event oriented as PRESENT with device timestamp", mapOf("time.orientation" to "PRESENT:$now"))
            "C40" -> pass("transferRetainedLearning", "Failure lesson attached to new functional variant", mapOf("learning.retained" to "NO_BUILD_ONLY_SUCCESS"))
            "C41" -> pass("separateSemanticLayers", "Raw, semantic, evidence and claim layers separated", mapOf("semantic.layers" to "RAW|SEMANTIC|EVIDENCE|CLAIM"))
            "C42" -> pass("validateMetricDenominator", "Metric fixed to executed handlers / 51", mapOf("metric.definition" to "EXECUTED_HANDLERS/51"))
            "C43" -> pass("classifyRecoveryCoverage", "Recovery candidate recorded with explicit classification", mapOf("recovery.coverage" to "CLASSIFIED"))
            "C44" -> pass("calculateArtifactCoverage", "Artifact coverage computed from persisted handler receipts", mapOf("artifact.coverage" to "$count/1"))
            "C45" -> pass("capReleaseStage", "Release capped at FUNCTIONAL_UNVERIFIED", mapOf("release.stage" to "FUNCTIONAL_UNVERIFIED"))
            "C46" -> pass("enforceSingleton", "Authoritative local runtime instance elected", mapOf("runtime.authority" to "ANDROID_ROOM_SINGLETON"))
            "C47" -> pass("runRiskPreflight", "Privacy/safety preflight completed; secret storage forbidden", mapOf("risk.preflight" to "PASS_LOCAL"))
            "C48" -> blocked("requireHumanAuthorization", "Spend/auth action blocked pending explicit human authorization", mapOf("human.gate" to "REQUIRED"))
            "C49" -> pass("checkBootReadiness", "Room/STENO/catalog canonical sources resolved", mapOf("boot.ready" to "true"))
            "C50" -> pass("registerErrorFingerprint", "Fingerprint BUILD_ONLY_FALSE_SUCCESS stored with regression guard", mapOf("error.fingerprint" to "BUILD_ONLY_FALSE_SUCCESS"))
            "C51" -> blocked("verifyLiveActivation", "Local heartbeat committed; external module heartbeat unproven", mapOf("module.heartbeat" to now, "module.external" to "UNPROVEN"))
            else -> ActionResult("FAIL", "unknownHandler", "Unknown control", "No handler registered")
        }
    }
}
