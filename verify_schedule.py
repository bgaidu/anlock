#!/usr/bin/env python3
# Standalone port of QuestionEngine.buildSchedule to verify per-subject quantity rules.
# Mirrors the FIXED logic: caps are HARD (never exceeded). total may be < N in degenerate
# configs (too few subjects to reach N under caps); engine wraps schedule so unlock still works.
PINYIN, MATH, ENGLISH, CITU = "pinyin", "math", "english", "ciyu"
ORDER = [PINYIN, MATH, ENGLISH, CITU]

def can_add(s, counts, pin_on, ciyu_on):
    if s == MATH:
        return counts[s] < 4
    if s == ENGLISH:
        return counts[s] < 4
    if s == PINYIN:
        if ciyu_on:
            return (counts[PINYIN] + counts[CITU]) < 4
        return counts[s] < 4
    if s == CITU:
        if pin_on:
            return (counts[PINYIN] + counts[CITU]) < 4
        return counts[s] < 4
    return True

def build_schedule(subjects, total):
    if total < 1:
        total = 1
    subjects = [s for s in ORDER if s in subjects]
    list_ = list(subjects)
    counts = {s: 1 for s in list_}  # each subject >= 1
    remaining = total - len(list_)
    if remaining < 0:
        for k in range(total, len(list_)):
            counts[list_[k]] = 0
        remaining = 0
    apply_caps = (total == 10)
    pin_on = PINYIN in subjects
    ciyu_on = CITU in subjects
    guard = 0
    while remaining > 0 and guard < 10000:
        guard += 1
        added = False
        for s in list_:
            if remaining <= 0:
                break
            if (not apply_caps) or can_add(s, counts, pin_on, ciyu_on):
                counts[s] += 1
                remaining -= 1
                added = True
        if not added:
            break
    # NO spillover: caps are HARD. remaining (if any) is discarded.
    return counts

def check(name, subjects, total):
    counts = build_schedule(subjects, total)
    total_actual = sum(counts[s] for s in subjects)
    errs = []
    # rule 1: each enabled subject >= 1  (only feasible when total >= len(subjects))
    if total >= len(subjects):
        for s in subjects:
            if counts.get(s, 0) < 1:
                errs.append(f"{s} < 1 ({counts.get(s,0)})")
    else:
        # infeasible to give every subject >=1: at least `total` subjects must get >=1
        got = sum(1 for s in subjects if counts.get(s, 0) >= 1)
        if got < total:
            errs.append(f"only {got} subjects >=1 but total={total}")
    # rule 2: when total == 10, caps are HARD
    if total == 10:
        if counts.get(MATH, 0) > 4:
            errs.append(f"math > 4 ({counts[MATH]})")
        if counts.get(ENGLISH, 0) > 4:
            errs.append(f"english > 4 ({counts[ENGLISH]})")
        pc = counts.get(PINYIN, 0) + counts.get(CITU, 0)
        if pc > 4:
            errs.append(f"pinyin+ciyu > 4 ({pc})")
    # never exceed N
    if total_actual > total:
        errs.append(f"sum {total_actual} > {total}")
    status = "OK " if not errs else "FAIL"
    print(f"[{status}] {name}: N={total} subjects={subjects} -> {counts} sum={total_actual}")
    for e in errs:
        print(f"        ! {e}")
    return not errs

ok = True
# N == 10 (caps HARD)
ok &= check("all 4 subjects, N=10", [PINYIN, MATH, ENGLISH, CITU], 10)
ok &= check("math+english+ciyu, N=10", [MATH, ENGLISH, CITU], 10)
ok &= check("math+english+pinyin, N=10", [MATH, ENGLISH, PINYIN], 10)
ok &= check("math+english only, N=10 (degenerate: sum<10, caps ok)", [MATH, ENGLISH], 10)
ok &= check("pinyin+ciyu only, N=10 (degenerate)", [PINYIN, CITU], 10)
ok &= check("math only, N=10 (degenerate)", [MATH], 10)
ok &= check("pinyin only, N=10 (degenerate)", [PINYIN], 10)
# N != 10 (only each >= 1)
ok &= check("all 4, N=5", [PINYIN, MATH, ENGLISH, CITU], 5)
ok &= check("all 4, N=3 (< subjects, drop extra)", [PINYIN, MATH, ENGLISH, CITU], 3)
ok &= check("all 4, N=15", [PINYIN, MATH, ENGLISH, CITU], 15)
ok &= check("math+english, N=7", [MATH, ENGLISH], 7)
ok &= check("pinyin+ciyu, N=2 (min)", [PINYIN, CITU], 2)
ok &= check("all 4, N=1 (< subjects, drop extra)", [PINYIN, MATH, ENGLISH, CITU], 1)

print("\nRESULT:", "ALL PASS" if ok else "SOME FAILED")
