"""Audit collected timetables, exact pairwise diversity, and training splits."""
import argparse
from collections import Counter
import csv
import hashlib
import json
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

import numpy as np

from collect import HERE, SRC, verify_encoding, write_json


def stats(values):
    a = np.asarray(values)
    return dict(n=int(a.size), min=float(a.min()), p05=float(np.percentile(a, 5)),
                median=float(np.median(a)), mean=float(a.mean()), p95=float(np.percentile(a, 95)), max=float(a.max()))


def hash_status(path, expected):
    raw = path.read_bytes()
    if hashlib.sha256(raw).hexdigest() == expected:
        return "exact_bytes"
    lf = raw.replace(b"\r\n", b"\n")
    if expected in {hashlib.sha256(lf).hexdigest(), hashlib.sha256(lf.replace(b"\n", b"\r\n")).hexdigest()}:
        return "line_endings_only"
    raise AssertionError(f"Unexplained hash mismatch: {path}")


def distances(matrix):
    n = len(matrix)
    output = np.empty((n, n), dtype=np.uint16)
    for start in range(0, n, 32):
        output[start:start+32] = np.count_nonzero(matrix[start:start+32, None, :] != matrix[None, :, :], axis=2)
    assert np.array_equal(output, output.T) and np.all(np.diag(output) == 0)
    return output


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("batch", type=Path)
    args = parser.parse_args()
    batch = args.batch.resolve()
    out = batch / "analysis"
    out.mkdir(exist_ok=True)
    schema = json.loads((batch / "encoding_schema.json").read_text())
    with np.load(batch / "dataset/encoded.npz") as archive:
        data = {key: archive[key] for key in archive.files}
    x, onehot, dfs, seeds = (data[k] for k in ["choices", "onehot", "df", "seeds"])
    blocks = schema["blocks"]
    n = len(x)
    with (batch / "dataset/metadata.csv").open() as stream:
        metadata = list(csv.DictReader(stream))
    assert len(metadata) == n and len(set(seeds.tolist())) == n
    assert x.shape == (n, len(blocks)) and onehot.shape == (n, schema["width"])
    assert np.all(onehot.sum(axis=1) == len(blocks))
    for j, b in enumerate(blocks):
        assert np.all((x[:, j] >= 0) & (x[:, j] < b["size"]))
        sub = onehot[:, b["offset"]:b["offset"] + b["size"]]
        assert np.array_equal(sub.argmax(axis=1), x[:, j]) and np.all(sub.sum(axis=1) == 1)
    listing = []
    hash_matches = Counter()
    checkpoints = []
    for i, row in enumerate(metadata):
        assert int(row["row"]) == i and int(row["seed"]) == seeds[i] and int(row["df"]) == dfs[i]
        xml = batch / row["xml"]
        record = json.loads((xml.parent / "summary.json").read_text())
        assert record["verified"] and record["choices"] == x[i].tolist()
        hash_matches[hash_status(xml, record["xml_sha256"])] += 1
        assert record["nfe"] == int(row["nfe"]) and record["final_df"] == dfs[i]
        verify_encoding(xml, x[i], schema)
        nodes = ET.parse(xml).getroot().findall("class")
        assert len(nodes) == len(schema["class_order"]) and len({e.get("id") for e in nodes}) == len(nodes)
        listing.append(f"{seeds[i]}\t{xml}\n")
        checkpoints.append(record["checkpoints"])
    instance_hash_status = hash_status(batch / "instance.xml", schema["problem_sha256"])
    (out / "verification_inputs.tsv").write_text("".join(listing))
    subprocess.run(["javac", "--release", "17", "-d", str(HERE / "build"), "-sourcepath", str(SRC),
                    str(HERE / "EvaluateSolutions.java")], check=True)
    result = subprocess.run(["java", "-cp", str(HERE / "build"), "solver.DO.EvaluateSolutions",
                 str(batch / "instance.xml"), str(out / "verification_inputs.tsv")], capture_output=True, text=True, check=True)
    (out / "verification.log").write_text(result.stdout + result.stderr)
    verified, violations, residuals = {}, Counter(), []
    for line in result.stdout.splitlines():
        if line.startswith("ERROR\t"):
            raise AssertionError(line)
        if line.startswith("RESULT\t"):
            parts = line.split("\t")
            verified[int(parts[1])] = int(parts[2])
            names = parts[4].split(",") if len(parts) > 4 and parts[4] else []
            violations.update(names)
            residuals.append(dict(seed=int(parts[1]), df=int(parts[2]), room_conflicts=int(parts[3]), distributions=names))
    assert verified == dict(zip(map(int, seeds), map(int, dfs)))
    write_json(out / "residual_constraints.json", residuals)
    print(f"Audited {n} XMLs and recomputed all DFs", flush=True)
    variable = np.array([b["size"] > 1 for b in blocks])
    observed = np.array([len(np.unique(x[:, j])) for j in range(len(blocks))])
    entropy, modal = [], []
    for j, b in enumerate(blocks):
        counts = np.bincount(x[:, j], minlength=b["size"])
        p = counts[counts > 0] / n
        entropy.append(float(-(p * np.log2(p)).sum()))
        modal.append(float(counts.max() / n))
    block_distance = distances(x[:, variable])
    class_ids = schema["class_order"]
    by_class = {ident: [] for ident in class_ids}
    for j, b in enumerate(blocks):
        by_class[b["class_id"]].append(j)
    class_matrix = np.zeros((n, len(class_ids)), dtype=np.int32)
    for j, ident in enumerate(class_ids):
        for k in by_class[ident]:
            class_matrix[:, j] = class_matrix[:, j] * blocks[k]["size"] + x[:, k]
    class_variable = np.array([len(np.unique(class_matrix[:, j])) > 1 for j in range(len(class_ids))])
    class_distance = distances(class_matrix)
    upper = np.triu_indices(n, 1)
    pair_blocks, pair_classes = block_distance[upper], class_distance[upper]
    nearest_distance = class_distance.copy()
    np.fill_diagonal(nearest_distance, np.iinfo(np.uint16).max)
    neighbor = nearest_distance.argmin(axis=1)
    nearest = nearest_distance[np.arange(n), neighbor]
    with (out / "nearest_neighbors.csv").open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["seed", "df", "nearest_seed", "nearest_df", "different_classes", "different_blocks"])
        for i, j in enumerate(neighbor):
            writer.writerow([seeds[i], dfs[i], seeds[j], dfs[j], nearest[i], block_distance[i, j]])
    with (out / "block_statistics.csv").open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["block", "class_id", "kind", "domain_size", "observed_categories", "entropy_bits", "modal_fraction"])
        for j, b in enumerate(blocks):
            writer.writerow([j, b["class_id"], b["kind"], b["size"], observed[j], entropy[j], modal[j]])
    np.savez_compressed(out / "distance_distributions.npz", pair_classes=pair_classes,
                        pair_blocks=pair_blocks, nearest_classes=nearest, observed_categories=observed,
                        entropy_bits=entropy, df=dfs)
    rng = np.random.default_rng(20261001)
    split = np.empty(n, dtype="U10")
    for df in sorted(set(dfs.tolist())):
        idx = rng.permutation(np.flatnonzero(dfs == df))
        nv, nt = max(1, round(len(idx) * .15)), max(1, round(len(idx) * .15))
        if len(idx) < 4:
            nv, nt = 0, 0
        split[idx[:nv]] = "validation"
        split[idx[nv:nv+nt]] = "test"
        split[idx[nv+nt:]] = "train"
    train = split == "train"
    missing = {}
    unseen_categories = 0
    for part in ["validation", "test"]:
        indices = np.flatnonzero(split == part)
        unseen, baseline_correct = 0, 0
        for j in np.flatnonzero(variable):
            categories = np.unique(x[train, j])
            unseen += int((~np.isin(x[indices, j], categories)).sum())
            modal_category = np.bincount(x[train, j], minlength=blocks[j]["size"]).argmax()
            baseline_correct += int((x[indices, j] == modal_category).sum())
        missing[part] = dict(samples=len(indices), unseen_block_assignments=unseen,
                            unseen_fraction=unseen / (len(indices) * int(variable.sum())),
                            majority_baseline_variable_accuracy=baseline_correct / (len(indices) * int(variable.sum())))
    for j, b in enumerate(blocks):
        unseen_categories += b["size"] - len(np.unique(x[train, j]))
    with (out / "proposed_split.csv").open("w", newline="") as stream:
        writer = csv.writer(stream)
        writer.writerow(["row", "seed", "df", "split"])
        writer.writerows(zip(range(n), seeds, dfs, split))
    # Only suggest dropping constants defined by the instance, not constants inferred from all data.
    used_width = sum(b["size"] for b in blocks if b["size"] > 1)
    checkpoint_summary = {}
    for j in range(4):
        checkpoint_summary[str((j + 1) * 30)] = dict(df=stats([c[j]["df"] for c in checkpoints]),
                                                   nfe=stats([c[j]["nfe"] for c in checkpoints]))
    other_batches = []
    for sibling in batch.parent.iterdir():
        if sibling.is_dir() and sibling != batch:
            other_batches.append(dict(batch=sibling.name, completed=len(list((sibling / "completed").glob("*/summary.json"))),
                                      included=False, reason="separate incomplete historical batch"))
    report = dict(batch=batch.name, samples=n, class_count=len(class_ids), block_count=len(blocks),
        time_blocks=sum(b["kind"] == "time" for b in blocks), room_blocks=sum(b["kind"] == "room" for b in blocks),
        choices_shape=list(x.shape), onehot_shape=list(onehot.shape), choices_dtype=str(x.dtype), onehot_dtype=str(onehot.dtype),
        domain_variable_blocks=int(variable.sum()), domain_constant_blocks=int((~variable).sum()),
        observed_variable_blocks=int((observed > 1).sum()), observed_constant_blocks=int((observed == 1).sum()),
        observed_variable_classes=int(class_variable.sum()), observed_constant_classes=int((~class_variable).sum()),
        variable_onehot_width=used_width, observed_onehot_columns=int((onehot.sum(axis=0) > 0).sum()),
        unobserved_onehot_columns=int((onehot.sum(axis=0) == 0).sum()),
        distinct_assignment_vectors=len(np.unique(x, axis=0)), distinct_seeds=len(set(seeds.tolist())),
        df_distribution=dict(sorted(Counter(map(str, dfs)).items())), df_stats=stats(dfs),
        pair_count=len(pair_classes), pairwise_different_classes=stats(pair_classes),
        pairwise_different_blocks=stats(pair_blocks), nearest_neighbor_different_classes=stats(nearest),
        near_pairs_at_5_blocks=int((pair_blocks <= 5).sum()),
        pairs_at_most_10_classes=int((pair_classes <= 10).sum()),
        audit=dict(xmls_verified=n, df_recomputed=n, onehot_and_xml_match=True,
                   xml_hash_matches=dict(hash_matches), instance_hash_match=instance_hash_status),
        residual_constraint_counts=dict(violations), proposed_split_counts=dict(Counter(split.tolist())),
        split_evaluation=missing, train_unobserved_domain_categories=unseen_categories,
        existing_split_counts=dict(Counter(r["split"] for r in metadata)),
        final_nfe=stats([int(r["nfe"]) for r in metadata]),
        final_seconds=stats([float(r["elapsed_seconds"]) for r in metadata]),
        checkpoint_summary=checkpoint_summary, other_batches=other_batches)
    write_json(out / "statistics.json", report)
    print(json.dumps(report, indent=2), flush=True)


if __name__ == "__main__":
    main()
