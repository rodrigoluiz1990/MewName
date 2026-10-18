"""Extract offline Pok?dex metadata from a PokeMiners Game Master JSON snapshot."""
import hashlib
import json
import sys
from pathlib import Path

source = Path(sys.argv[1])
records = json.loads(source.read_text(encoding="utf-8"))
extended = {r["templateId"].removeprefix("EXTENDED_"): r["data"]["pokemonExtendedSettings"].get("sizeSettings")
            for r in records if "pokemonExtendedSettings" in r["data"]}
entries = {}
for record in records:
    p = record["data"].get("pokemonSettings")
    if not p:
        continue
    key = p.get("form") or p["pokemonId"]
    info = {k: p[k] for k in ("pokedexHeightM", "pokedexWeightKg", "kmBuddyDistance", "thirdMove",
            "isTradable", "buddyWalkedMegaEnergyAward", "disableTransferToPokemonHome", "disableTransferToPokemonHomeIfShiny") if k in p}
    # Protobuf omitted booleans are false; base flee rate, however, may be server-controlled.
    info["disableTransferToPokemonHome"] = p.get("disableTransferToPokemonHome", False)
    encounter = p.get("encounter", {})
    if "baseFleeRate" in encounter:
        info["baseFleeRate"] = encounter["baseFleeRate"]
    if encounter:
        info["catchCandy"] = 3 + encounter.get("bonusCandyCaptureReward", 0)
        info["catchStardust"] = 100 + encounter.get("bonusStardustCaptureReward", 0)
    sizes = extended.get(record["templateId"])
    if sizes:
        info["sizes"] = sizes
    entries[key] = info
output = {"source": "https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json",
          "sha256": hashlib.sha256(source.read_bytes()).hexdigest(), "retrieved": sys.argv[2], "entries": entries}
Path("app/src/main/assets/pokemon/pokedex_info.json").write_text(json.dumps(output, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
print(f"Exported {len(entries)} records")
