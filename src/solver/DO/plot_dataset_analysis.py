"""Generate exportable scientific charts from audited dataset statistics."""
import argparse
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import numpy as np

parser = argparse.ArgumentParser()
parser.add_argument("batch", type=Path)
args = parser.parse_args()
out = args.batch / "analysis"
stats = json.loads((out / "statistics.json").read_text())
with np.load(out / "distance_distributions.npz") as archive:
    data = {k: archive[k] for k in archive.files}
plt.rcParams.update({"font.size": 11, "axes.spines.top": False, "axes.spines.right": False,
                     "figure.facecolor": "#fafbfc", "axes.facecolor": "white"})
fig, axes = plt.subplots(2, 2, figsize=(13, 8.5), layout="constrained")
colors = ["#1f876e", "#3266ad", "#779ecb", "#aab9ce", "#c7cdd4", "#e0e2e5"]
ax = axes[0, 0]
keys = np.array([int(k) for k in stats["df_distribution"]])
values = np.array([stats["df_distribution"][str(k)] for k in keys])
ax.bar(keys, values, color=colors, width=.7)
for x, value in zip(keys, values):
    ax.text(x, value + 30, f"{value:,}\n({value / values.sum():.2%})", ha="center", fontsize=10)
ax.set(xticks=keys, ylim=(0, 2300), xlabel="Final DF", ylabel="Timetables", title="A. Final distance to feasibility")
ax = axes[0, 1]
pair = data["pair_classes"]
ax.hist(pair, bins=np.arange(pair.min() - .5, pair.max() + 1.5), color="#3266ad", edgecolor="white", linewidth=.3)
ax.axvline(np.median(pair), color="#16385e", linestyle="--", label=f"Median = {np.median(pair):.0f} classes")
ax.legend(frameon=False)
ax.set(xlabel="Classes with different time and/or room", ylabel="Pairs", title=f"B. All {len(pair):,} distinct solution pairs")
ax = axes[1, 0]
near = data["nearest_classes"]
ax.hist(near, bins=np.arange(near.min() - .5, near.max() + 1.5), color="#1f876e", edgecolor="white", linewidth=.5)
ax.axvline(np.median(near), color="#125842", linestyle="--", label=f"Median = {np.median(near):.0f} classes")
ax.legend(frameon=False)
ax.set(xlabel="Different classes to closest other timetable", ylabel="Timetables", title="C. Nearest-neighbor distance for each timetable")
ax = axes[1, 1]
t = [30, 60, 90, 120]
mean = [stats["checkpoint_summary"][str(x)]["df"]["mean"] for x in t]
median = [stats["checkpoint_summary"][str(x)]["df"]["median"] for x in t]
ax.plot(t, mean, "o-", color="#3266ad", label="Mean DF", linewidth=2)
ax.plot(t, median, "s--", color="#1f876e", label="Median DF", linewidth=2)
for x, y in zip(t, mean):
    ax.annotate(f"{y:.2f}", (x, y), xytext=(0, 10), textcoords="offset points", ha="center")
ax.set(xticks=t, ylim=(1.5, 4.8), xlabel="Search time per seed (seconds)", ylabel="DF", title="D. Progress across 2,500 independent seeds")
ax.legend(frameon=False)
fig.suptitle("muni-fsps-spr17 postcompetition2 | 2,500 terminal HC timetables", fontsize=17, fontweight="bold")
fig.savefig(out / "dataset_distribution.png", dpi=180)
fig.savefig(out / "dataset_distribution.svg")
plt.close(fig)
print(out / "dataset_distribution.png")
