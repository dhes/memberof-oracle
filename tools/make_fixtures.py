#!/usr/bin/env python3
"""Writes the synthetic CodeSystem and ValueSet fixtures. All systems are made up, so the fixtures carry no licensed codes."""
import json

B = "http://example.org/fhirpath-memberof"
TS = "2026-01-01T00:00:00Z"
cs = lambda n: f"{B}/CodeSystem/{n}"
vs = lambda n: f"{B}/ValueSet/{n}"


def write(kind, name, obj):
    with open(f"fixtures/synthetic/{kind}/{name}.json", "w") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def code_system(name, concepts, **kw):
    d = {"resourceType": "CodeSystem", "id": name, "url": cs(name), "version": "1.0.0", "name": name.capitalize(),
         "status": "active", "content": "complete", "caseSensitive": True, "concept": concepts}
    d.update(kw)
    write("codesystems", name, d)


def value_set(name, compose=None, expansion=None, version=None, id=None):
    d = {"resourceType": "ValueSet", "id": id or name, "url": vs(name), "name": name.replace("-", "_"), "status": "active"}
    if version:
        d["version"] = version
    if compose is not None:
        d["compose"] = compose
    if expansion is not None:
        d["expansion"] = {"timestamp": TS, "contains": expansion}
    write("valuesets", id or name, d)


inc = lambda s, codes: {"system": cs(s), "concept": [{"code": c} for c in codes]}
con = lambda s, c, **kw: dict({"system": cs(s), "code": c}, **kw)

code_system("alpha", [{"code": c, "display": f"Alpha {c}"} for c in ["A1", "A2", "A3", "A4"]])
code_system("beta", [{"code": "B1", "display": "Beta B1", "concept": [{"code": "B1a", "display": "Beta B1a"}, {"code": "B1b", "display": "Beta B1b"}]},
                     {"code": "B2", "display": "Beta B2"}], hierarchyMeaning="is-a")
# gamma deliberately reuses the code "A1" so that system guessing for a bare code can be probed
code_system("gamma", [{"code": "A1", "display": "Gamma A1"}, {"code": "G2", "display": "Gamma G2"}])

# The shape WHO SMART Guidelines publish: enumerated multi-system compose plus a matching expansion.
value_set("who-shape", {"include": [inc("alpha", ["A1", "A2"]), inc("beta", ["B2"])]}, [con("alpha", "A1"), con("alpha", "A2"), con("beta", "B2")])
value_set("expansion-only", None, [con("alpha", "A1")])
value_set("compose-only", {"include": [inc("alpha", ["A1", "A2"])]})
value_set("nested", {"include": [inc("beta", ["B1", "B1a", "B1b"])]}, [con("beta", "B1", contains=[con("beta", "B1a"), con("beta", "B1b")])])
value_set("abstract", None, [con("beta", "B1", abstract=True, contains=[con("beta", "B1a")])])
value_set("inactive", None, [con("alpha", "A1"), con("alpha", "A3", inactive=True)])
value_set("exclude", {"include": [inc("alpha", ["A1", "A2", "A3"])], "exclude": [inc("alpha", ["A2"])]})
value_set("versioned", {"include": [inc("alpha", ["A1"])]}, version="1.0.0", id="versioned-1")
value_set("versioned", {"include": [inc("alpha", ["A2"])]}, version="2.0.0", id="versioned-2")
value_set("filter", {"include": [{"system": cs("beta"), "filter": [{"property": "concept", "op": "is-a", "value": "B1"}]}]})
value_set("whole-system", {"include": [{"system": cs("alpha")}]})
value_set("disagree", {"include": [inc("alpha", ["A1"])]}, [con("alpha", "A2")])
value_set("import", {"include": [{"valueSet": [vs("compose-only")]}]})
