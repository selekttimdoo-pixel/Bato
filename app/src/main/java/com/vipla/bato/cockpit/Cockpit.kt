package com.vipla.bato.cockpit

data class ControlSpec(
    val code: String,
    val name: String,
    val requiredEvidence: List<String>,
    val evidence: String,
    val nextPhysicalTest: String
)

data class GuardResult(val allowed: Boolean, val evidence: String)

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

object LocalGuardEngine {
    fun evaluate(spec: ControlSpec, payload: Map<String, String>): GuardResult {
        val missing = spec.requiredEvidence.filter { payload[it].isNullOrBlank() }
        if (missing.isNotEmpty()) return GuardResult(false, "BLOCK ${spec.code}: missing ${missing.joinToString()}")

        val semanticBlock = when (spec.code) {
            "C02", "C18" -> payload.values.any { it.contains("ILLEGAL", true) }
            "C03", "C08", "C09", "C33", "C44", "C51" -> payload.values.any { it.equals("false", true) || it == "0" }
            "C05" -> payload["corrective_mode"] == "true" && payload["essential"] == "false"
            "C15", "C30", "C31", "C42", "C43", "C46" -> payload.values.any { it.toDoubleOrNull() == 0.0 }
            "C22" -> payload["tri_state"] !in setOf("DA", "NE", "MOZDA")
            "C32" -> (payload["failure_count"]?.toIntOrNull() ?: 0) >= 3 && payload["branch_action"] != "ABANDON"
            "C35" -> payload["secret_transport"] == "CHAT"
            "C37" -> payload["lane"] == "UNSYNCED" && payload["canonical_write"] == "true"
            "C45" -> stage(payload["claimed_stage"]) > stage(payload["verified_stage"])
            "C47" -> payload["risk_level"] in setOf("HIGH", "CRITICAL") && payload["approval"] != "true"
            "C48" -> (payload["human_only"] == "true" && payload["authorization"] != "true") ||
                (payload["paid"] == "true" && payload["spend_approval"] != "true")
            else -> false
        }
        return if (semanticBlock) GuardResult(false, "BLOCK ${spec.code}: semantic guard rejected payload")
        else GuardResult(true, "PASS ${spec.code}: required local evidence present")
    }

    fun positivePayload(spec: ControlSpec): Map<String, String> = spec.requiredEvidence.associateWith { key ->
        when (key) {
            "tri_state" -> "DA"
            "corrective_mode", "canonical_write", "human_only", "paid" -> "false"
            "essential", "audit_passed", "approval", "authorization", "spend_approval", "guardrail_applied", "regression_test_passed" -> "true"
            "claimed_stage", "verified_stage" -> "PILOT"
            "runtime_count", "denominator", "candidate_total", "classified", "inspected_total", "artifact_total" -> "1"
            else -> "verified-$key"
        }
    }

    fun forcedBlockPayload(spec: ControlSpec): Map<String, String> = positivePayload(spec) - spec.requiredEvidence.first()

    private fun stage(value: String?) = listOf("EXPERIMENTAL", "INTERMEDIATE", "PILOT", "PRODUCTION").indexOf(value)
}

data class SelfTestResult(val spec: ControlSpec, val pass: GuardResult, val forcedBlock: GuardResult) {
    val verified get() = pass.allowed && !forcedBlock.allowed
}
