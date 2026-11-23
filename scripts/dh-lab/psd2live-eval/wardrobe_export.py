"""Pure checks for psd2live export dirs against wardrobe-spec production bindings."""
from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

_MOC_STRING = re.compile(rb"[A-Za-z_][A-Za-z0-9_]{3,63}")


def load_spec(spec_path: Path) -> dict:
    return json.loads(spec_path.read_text())


def production_wear_params(spec: dict) -> list[dict[str, str]]:
    """Declared underwear (etc.) toggle params from SSOT — not runtime-verified."""
    out: list[dict[str, str]] = []
    for slot in spec.get("slots") or []:
        binding = slot.get("productionBinding")
        if not binding:
            continue
        prefix = binding["parameterPrefix"]
        for variant in slot.get("variants") or []:
            token = variant.get("token")
            if not token or token == "none":
                continue
            out.append({
                "slot": slot["slot"],
                "token": token,
                "parameter": prefix + token,
                "status": variant.get("status", ""),
            })
    return out


def scan_moc3_strings(blob: bytes) -> set[str]:
    return {m.group().decode() for m in _MOC_STRING.finditer(blob)}


def inspect_directory(
    out_dir: Path,
    *,
    prefix: str,
    spec: dict | None = None,
    require_demo_smoke: bool = False,
) -> dict[str, Any]:
    out_dir = out_dir.resolve()
    model3 = out_dir / f"{prefix}.model3.json"
    moc3 = out_dir / f"{prefix}.moc3"
    cdi3 = out_dir / f"{prefix}.cdi3.json"
    errors: list[str] = []
    summary: dict[str, Any] = {"dir": str(out_dir), "prefix": prefix, "kind": "export-fixture"}

    if not model3.is_file():
        errors.append(f"missing {prefix}.model3.json")
    if not moc3.is_file():
        errors.append(f"missing {prefix}.moc3")
    if not cdi3.is_file():
        errors.append(f"missing {prefix}.cdi3.json")

    params_in_moc: set[str] = set()
    arts_in_moc: set[str] = set()
    if moc3.is_file():
        blob = moc3.read_bytes()
        summary["moc3_size"] = len(blob)
        summary["magic"] = blob[:4].decode("ascii", "replace")
        if blob[:4] != b"MOC3":
            errors.append(f"bad moc3 magic: {blob[:4]!r}")
        strs = scan_moc3_strings(blob)
        params_in_moc = {s for s in strs if s.startswith("Param")}
        arts_in_moc = {s for s in strs if s.startswith("ArtMesh")}
        summary["param_count"] = len(params_in_moc)
        summary["artmesh_count"] = len(arts_in_moc)
        if require_demo_smoke:
            if "ParamAngleX" not in params_in_moc:
                errors.append("ParamAngleX missing from moc3 strings")
            if "ArtMeshTopwear" not in arts_in_moc:
                errors.append("ArtMeshTopwear missing (expected from topwear layer)")

    cdi_param_ids: set[str] = set()
    if cdi3.is_file():
        cdi = json.loads(cdi3.read_text())
        cdi_param_ids = {p.get("Id") for p in (cdi.get("Parameters") or []) if p.get("Id")}
        summary["cdi_param_count"] = len(cdi_param_ids)
        summary["cdi_drawable_count"] = len(cdi.get("Drawables") or [])

    if model3.is_file():
        ref = json.loads(model3.read_text())["FileReferences"]
        expected_moc = f"{prefix}.moc3"
        summary["model3_moc"] = ref.get("Moc")
        if ref.get("Moc") != expected_moc:
            errors.append(f"model3 Moc ref unexpected: {ref.get('Moc')!r}")
        for name in ref.get("Textures") or []:
            if not (out_dir / name).is_file():
                errors.append(f"missing texture {name}")

    wear_declared: list[dict[str, str]] = []
    wear_found: list[str] = []
    wear_missing: list[str] = []
    if spec is not None:
        wear_declared = production_wear_params(spec)
        summary["wardrobe_declared_count"] = len(wear_declared)
        for item in wear_declared:
            pid = item["parameter"]
            in_moc = pid in params_in_moc if moc3.is_file() else False
            in_cdi = pid in cdi_param_ids if cdi3.is_file() else False
            if in_moc and in_cdi:
                wear_found.append(pid)
            else:
                wear_missing.append(pid)
                if moc3.is_file() and not in_moc:
                    errors.append(f"{pid} missing from moc3 (slot={item['slot']} token={item['token']})")
                if cdi3.is_file() and not in_cdi:
                    errors.append(f"{pid} missing from cdi3 (slot={item['slot']} token={item['token']})")
        summary["wardrobe_params_declared"] = [w["parameter"] for w in wear_declared]
        summary["wardrobe_params_found"] = wear_found
        summary["wardrobe_params_missing"] = wear_missing
    summary["runtime_verified"] = False
    summary["ok"] = not errors
    summary["errors"] = errors
    return summary


def detect_prefix(out_dir: Path) -> str | None:
    for name in ("layout", "demo", "wardrobe"):
        if (out_dir / f"{name}.model3.json").is_file():
            return name
    return None
