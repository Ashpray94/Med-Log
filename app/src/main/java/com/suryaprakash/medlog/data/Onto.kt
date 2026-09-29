package com.suryaprakash.medlog.data

private fun <T> pick(edited: T, original: T, fresh: T) = if (edited != original) edited else fresh

/**
 * A page that edits a medicine holds the copy it loaded ([original]) and the copy the person changed (this). To save, it writes onto the
 * freshest row ([fresh]) only the fields the person changed, so a change another phone made meanwhile (Ravi stopped it) is not undone
 * by a Save of an old copy. Use with [Repo.updateMedicine].
 */
fun Medicine.onto(original: Medicine, fresh: Medicine): Medicine = fresh.copy(
    name = pick(name, original.name, fresh.name), strength = pick(strength, original.strength, fresh.strength), form = pick(form, original.form, fresh.form),
    amount = pick(amount, original.amount, fresh.amount), food = pick(food, original.food, fresh.food), times = pick(times, original.times, fresh.times),
    days = pick(days, original.days, fresh.days), startDate = pick(startDate, original.startDate, fresh.startDate), endDate = pick(endDate, original.endDate, fresh.endDate),
    critical = pick(critical, original.critical, fresh.critical), asNeeded = pick(asNeeded, original.asNeeded, fresh.asNeeded),
    minGapHours = pick(minGapHours, original.minGapHours, fresh.minGapHours), purpose = pick(purpose, original.purpose, fresh.purpose),
    photoPath = pick(photoPath, original.photoPath, fresh.photoPath), pillsLeft = pick(pillsLeft, original.pillsLeft, fresh.pillsLeft),
    pillsAt = pick(pillsAt, original.pillsAt, fresh.pillsAt), active = pick(active, original.active, fresh.active),
    bloodThinner = pick(bloodThinner, original.bloodThinner, fresh.bloodThinner), changedAt = pick(changedAt, original.changedAt, fresh.changedAt),
    changeNote = pick(changeNote, original.changeNote, fresh.changeNote), calendarEventId = pick(calendarEventId, original.calendarEventId, fresh.calendarEventId),
    shape = pick(shape, original.shape, fresh.shape), color = pick(color, original.color, fresh.color), feedInfo = pick(feedInfo, original.feedInfo, fresh.feedInfo),
)

/** The same for "My details": the fields the person edited; the care plan is written only through [Repo.updatePlan]. */
fun Profile.onto(original: Profile, fresh: Profile): Profile = fresh.copy(
    name = pick(name, original.name, fresh.name), dob = pick(dob, original.dob, fresh.dob), sex = pick(sex, original.sex, fresh.sex),
    bloodGroup = pick(bloodGroup, original.bloodGroup, fresh.bloodGroup), hospitalId = pick(hospitalId, original.hospitalId, fresh.hospitalId),
    conditions = pick(conditions, original.conditions, fresh.conditions), allergies = pick(allergies, original.allergies, fresh.allergies),
    doctorName = pick(doctorName, original.doctorName, fresh.doctorName), doctorPhone = pick(doctorPhone, original.doctorPhone, fresh.doctorPhone),
    onBloodThinner = pick(onBloodThinner, original.onBloodThinner, fresh.onBloodThinner), notes = pick(notes, original.notes, fresh.notes),
)
