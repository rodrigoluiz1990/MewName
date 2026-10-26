"""Generate calculated boss CP references, NOT a list of released/active raids.
Run after updating raids/metadata.json. Unsupported categories are omitted.
"""
import json
import math
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "app/src/main/assets/raids"

def generate():
    metadata = json.loads((ROOT / "metadata.json").read_text(encoding="utf-8"))
    entries = []
    for pokemon in sorted(metadata["pokemon"], key=lambda p: (p.get("dex", 0), p["id"])):
        dex, name, stats = pokemon.get("dex", 0), pokemon["id"], pokemon["stats"]
        if dex <= 0 or stats["baseAttack"] <= 0 or stats["baseDefense"] <= 0:
            continue
        if any(x in name for x in ("_PRIMAL", "_GIGANTAMAX", "_DYNAMAX")):
            continue
        tiers = [("RAID_LEVEL_MEGA", 9000)] if "_MEGA" in name else [
            (f"RAID_LEVEL_{level}" + ("_SHADOW" if "_SHADOW" in name else ""), hp)
            for level, hp in ((1, 600), (3, 3600), (5, 15000))]
        entries.append({"dex": dex, "id": name, "bossCp": {
            tier: math.floor((stats["baseAttack"] + 15) * math.sqrt(stats["baseDefense"] + 15) * math.sqrt(hp) / 10)
            for tier, hp in tiers}})
    return {"schema": 1, "source": "raids/metadata.json", "kind": "calculated_reference_not_availability", "pokemon": entries}

if __name__ == "__main__":
    target = ROOT / "boss_cp.json"
    target.write_text(json.dumps(generate(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(target)
