package com.suryaprakash.medlog.data

import com.suryaprakash.medlog.MedLogApp
import kotlinx.coroutines.launch

/*
 * Settings that describe the PERSON, not the phone, live in the profile row (one nullable column each, so each is shared and merged on its
 * own): water goal, diabetes, emergency number, morning check-in, reminder times, SOS countdown, the doctor's kcal and protein targets, the
 * foods the person added, and the pending "tell me more" reminders. Whoever changes one (the person, or a helper on a replica) changes it for
 * every phone. What the phone does with them still reads [Settings]: on the person's own phone the settings follow the profile ([mirrorPerson]),
 * and while a helper looks at a replica the screens read the replica person's values ([withPerson] over the helper's own settings).
 * Left on the phone: everything about how THIS phone looks, sounds, locks and connects (see docs/PERSONA_TEST_REPORT.md, "Round 3").
 */

/**
 * [this] with the person's values from [p] laid over it. A value the profile does not have yet (null) is taken from [unset]: this phone's own
 * setting for the person's own phone, the plain defaults while a helper looks at someone else's replica.
 */
fun Settings.withPerson(p: Profile?, unset: Settings = this): Settings = copy(
    waterGoal = p?.waterGoal ?: unset.waterGoal, diabetic = p?.diabetic ?: unset.diabetic, emergencyNumber = p?.emergencyNumber ?: unset.emergencyNumber,
    checkInEnabled = p?.checkInEnabled ?: unset.checkInEnabled, checkInTime = p?.checkInTime ?: unset.checkInTime,
    snoozeMinutes = p?.snoozeMinutes ?: unset.snoozeMinutes, escalateMinutes = p?.escalateMinutes ?: unset.escalateMinutes,
    escalateCriticalMinutes = p?.escalateCriticalMinutes ?: unset.escalateCriticalMinutes, sosCountdown = p?.sosCountdown ?: unset.sosCountdown,
)

/** One time: the person's values this phone had in its settings become the profile's, for every value the profile does not have yet. */
fun Profile.fillFrom(s: Settings, kcalTarget: Double?, proteinTarget: Double?, customFoods: String?, followups: String?): Profile = copy(
    waterGoal = waterGoal ?: s.waterGoal, diabetic = diabetic ?: s.diabetic, emergencyNumber = emergencyNumber ?: s.emergencyNumber,
    checkInEnabled = checkInEnabled ?: s.checkInEnabled, checkInTime = checkInTime ?: s.checkInTime,
    snoozeMinutes = snoozeMinutes ?: s.snoozeMinutes, escalateMinutes = escalateMinutes ?: s.escalateMinutes,
    escalateCriticalMinutes = escalateCriticalMinutes ?: s.escalateCriticalMinutes, sosCountdown = sosCountdown ?: s.sosCountdown,
    kcalTarget = this.kcalTarget ?: kcalTarget, proteinTarget = this.proteinTarget ?: proteinTarget,
    customFoods = this.customFoods ?: customFoods?.takeIf { it.isNotBlank() }, followups = this.followups ?: followups?.takeIf { it.isNotBlank() },
)

/** Makes this phone's settings follow its own profile (called when the profile changes, by an edit here or by sync). */
suspend fun MedLogApp.mirrorPerson() {
    val p = ownRepo.profile()
    if (settings.value.withPerson(p) != settings.value) settings.update { it.withPerson(p) }
}

/** Keeps the phone's settings following its own profile for as long as the app runs. */
suspend fun MedLogApp.followOwnProfile() {
    var follow: String? = null; var first = true
    ownDb.profile().flow().collect { p ->
        if (p == null) return@collect
        val changed = settings.value.withPerson(p) != settings.value || follow != p.followups
        if (settings.value.withPerson(p) != settings.value) settings.update { it.withPerson(p) }
        follow = p.followups
        // reminder times, the check-in time and the follow-ups are planned into the next alarm: plan again when a helper changed one
        if (changed && !first) runCatching { com.suryaprakash.medlog.meds.Scheduler.reschedule(this) }
        first = false
    }
}

/**
 * Changes a setting that describes the person whose data is shown (this phone's own, or the replica of the person a helper looks after):
 * written to the shared profile on the freshest row, so it reaches every phone. On the phone's own data its settings follow at once.
 */
fun MedLogApp.editPerson(f: (Profile) -> Profile) {
    val r = repo; val own = viewing.state.value == null
    scope.launch { r.updateProfile(f); if (own) mirrorPerson() }
}

/**
 * One time on the person's phone (not a helper's): what the person had chosen in this phone's settings moves into the profile, for every
 * value the profile does not already have (a helper may have set some from a replica). The old phone-wide keys are removed.
 */
suspend fun MedLogApp.movePersonSettings() {
    if (settings.getString("person_moved") != null || !settings.value.onboarded) return
    if (settings.value.role != "helper") {
        val s = settings.value
        val followups = settings.getString("followups").orEmpty().split(";").mapNotNull { e ->
            val p = e.split(":"); val uid = if (p.size == 2) p[0].toLongOrNull()?.let { ownDb.notes().get(it)?.uid }?.takeIf { it.isNotEmpty() } else null
            uid?.let { "$it:${p[1]}" }
        }.joinToString(";")
        ownRepo.updateProfile { it.fillFrom(s, settings.getString("kcal_target")?.toDoubleOrNull(), settings.getString("protein_target")?.toDoubleOrNull(), settings.getString("custom_foods"), followups) }
        mirrorPerson()
    }
    for (k in listOf("kcal_target", "protein_target", "custom_foods", "followups")) settings.putString(k, null)
    settings.putString("person_moved", "1")
}
