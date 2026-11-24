"""Integration tests. Run against an expendable database: successful test records are retained."""
import argparse
import concurrent.futures
import datetime as dt
import json
import secrets
import urllib.error
import urllib.request

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument("--url", default="http://127.0.0.1:8080")
parser.add_argument("--token", default="school-demo")
args = parser.parse_args()


def call(path, payload=None, token=args.token):
    data = json.dumps(payload, ensure_ascii=False).encode() if payload is not None else None
    headers = {"Authorization": "Bearer " + token}
    if data is not None:
        headers["Content-Type"] = "application/json"
    request = urllib.request.Request(args.url.rstrip("/") + path, data=data, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        return error.code, json.loads(error.read())


def expect(result, code):
    assert result[0] == code, f"Expected HTTP {code}, got {result!r}"
    return result[1]


def tables():
    return expect(call("/api/v1/snapshot"), 200)["tables"]


def post(table, fields, record=None, code=204):
    payload = {"fields": fields}
    if record is not None:
        payload.update(id=record["id"], version=record["version"])
    return expect(call("/api/v1/catalogs/" + table, payload), code)


def find(table, key, value):
    return next(row for row in tables()[table] if row["fields"][key] == value)


def check(name):
    print("OK", name, flush=True)


expect(call("/health"), 200)
expect(call("/api/v1/snapshot", token="invalid"), 401)
expect(call("/api/v1/catalogs/not_a_table", {}), 404)
expect(call("/api/v1/catalogs/categories", {}), 405)
expect(call("/api/v1/catalogs/students", {"fields": {"name": 123}}), 400)
initial = tables()
assert set(initial) == {"categories", "instructors", "cars", "study_groups", "students", "lessons"}
assert len(initial["students"]) >= 12 and len(initial["lessons"]) >= 10
check("health_auth_protocol_seed")

nonce = secrets.randbelow(10**8)
title = f"Тест-{nonce:08}"
phone = f"+7800{nonce:08}"
group_fields = dict(title=title, category_id="1", instructor_id="1", start_date="2098-01-01", end_date="2099-01-01", capacity="2")
post("study_groups", group_fields)
group = find("study_groups", "title", title)
post("study_groups", dict(group["fields"], capacity="1"), group)
group = find("study_groups", "title", title)
assert group["version"] == 2
post("study_groups", dict(group["fields"], category_id="2"), group, code=409)
check("create_edit_group_and_constant_category")

student_fields = dict(name="Тестовый Ученик", phone=phone, group_id=str(group["id"]), status="STUDYING", enrolled_on="2098-01-05")
post("students", student_fields)
student = find("students", "phone", phone)
post("students", dict(student_fields, status="PAUSED"), student)
changed = find("students", "phone", phone)
assert changed["version"] == 2 and changed["fields"]["status"] == "PAUSED"
post("students", dict(student_fields, status="GRADUATED"), student, code=409)
post("students", dict(student_fields, phone=phone + "1"), code=409)
post("students", dict(student_fields, group_id="99999999"), code=409)
post("students", dict(student_fields, group_id="1"), code=409)
post("students", dict(student_fields, phone="bad"), code=400)
assert len(tables()["students"]) == len(initial["students"]) + 1
assert find("students", "phone", phone) == changed
check("create_edit_student_and_rollback_on_errors")

start = dt.datetime(2099, 1, 1, 9) + dt.timedelta(minutes=nonce % 180_000)
lesson_fields = dict(student_id=str(student["id"]), instructor_id="1", car_id="1",
                     starts_at=start.isoformat(timespec="minutes"), duration_minutes="60", status="PLANNED", note=title)
post("lessons", dict(lesson_fields, car_id="4"), code=409)
post("lessons", dict(lesson_fields, status="DONE"), code=409)
post("lessons", lesson_fields)
lesson = find("lessons", "note", title)
post("lessons", dict(lesson_fields, note=title + "-overlap"), code=409)
post("lessons", dict(lesson_fields, student_id="2", car_id="2", note=title + "-instructor"), code=409)
post("lessons", dict(lesson_fields, student_id="2", instructor_id="2", note=title + "-car"), code=409)
post("students", dict(changed["fields"], group_id="2"), changed, code=409)
check("lesson_constraints_and_student_transfer_guard")

later = (start + dt.timedelta(hours=1)).isoformat(timespec="minutes")
post("lessons", dict(lesson_fields, starts_at=later, note=title + "-adjacent"))
post("lessons", dict(lesson_fields, status="CANCELED"), lesson)
post("lessons", lesson_fields)
canceled = next(row for row in tables()["lessons"] if row["id"] == lesson["id"])
assert canceled["version"] == 2 and canceled["fields"]["status"] == "CANCELED"
post("lessons", lesson_fields, canceled, code=409)
check("adjacent_slots_cancel_and_release_slot")

parallel_at = (start + dt.timedelta(days=1)).isoformat(timespec="minutes")


def compete(label):
    return call("/api/v1/catalogs/lessons", {"fields": dict(lesson_fields, note=label, starts_at=parallel_at)})[0]


with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    assert sorted(pool.map(compete, [title + "-A", title + "-B"])) == [204, 409]
check("one_of_two_concurrent_writes_succeeds")
assert len(tables()["lessons"]) == len(initial["lessons"]) + 4
print("All integration checks passed.")
