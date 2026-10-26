#!/usr/bin/env python3
"""Recreate the two distribution figures from the archived local CSV files."""

from __future__ import annotations

import csv
from pathlib import Path

import matplotlib.pyplot as plt


ROOT = Path(__file__).resolve().parent
DATA = ROOT / "distribution"
BLOCK_ORDER = ["IBlock", "JBlock", "LBlock", "ZBlock", "SBlock", "TBlock", "OBlock"]
BLOCK_LABELS = ["I", "J", "L", "Z", "S", "T", "O"]
ITEM_ORDER = ["O", "B", "L", "C", "P"]
COLORS = ["#2563eb", "#ea580c", "#16a34a", "#dc2626", "#9333ea", "#0891b2", "#ca8a04"]


def rows(path: Path) -> list[dict[str, str]]:
    with path.open(newline="", encoding="utf-8") as handle:
        return list(csv.DictReader(handle))


def plot_blocks() -> None:
    data = rows(DATA / "block-distribution.csv")
    grouped = {difficulty: [] for difficulty in ["Easy", "Normal", "Hard"]}
    for row in data:
        grouped[row["difficulty"]].append(row)

    fig, axes = plt.subplots(1, 3, figsize=(15, 5), sharey=True)
    for axis, (difficulty, values) in zip(axes, grouped.items()):
        by_type = {row["block"]: row for row in values}
        observed = [float(by_type[k]["observed_probability"]) * 100 for k in BLOCK_ORDER]
        expected = [float(by_type[k]["expected_probability"]) * 100 for k in BLOCK_ORDER]
        lower = [value * 0.95 for value in expected]
        upper = [value * 1.05 for value in expected]
        x = list(range(len(BLOCK_ORDER)))

        axis.bar(x, observed, color=COLORS, width=0.68, label="Observed")
        axis.scatter(x, expected, color="#111827", marker="D", s=34, zorder=3, label="Expected")
        for i in x:
            axis.vlines(i, lower[i], upper[i], color="#111827", linewidth=3, alpha=0.45)
            axis.text(i, observed[i] + 0.35, f"{observed[i]:.2f}%", ha="center", fontsize=8)
        axis.set_title(f"{difficulty} · 100,000 draws")
        axis.set_xticks(x, BLOCK_LABELS)
        axis.set_ylim(0, 19)
        axis.grid(axis="y", alpha=0.2)

    axes[0].set_ylabel("Observed selection rate (%)")
    handles, labels = axes[0].get_legend_handles_labels()
    fig.legend(handles, labels, loc="lower center", ncol=2, frameon=False)
    fig.suptitle("Production block generator distribution\nBar: observed · Diamond: expected · Line: ±5% relative-error limit", y=0.98)
    fig.tight_layout(rect=(0, 0.08, 1, 0.90))
    fig.savefig(ROOT / "block-distribution.png", dpi=180, bbox_inches="tight")
    plt.close(fig)


def plot_items() -> None:
    data = rows(DATA / "item-distribution.csv")
    by_label = {row["label"]: row for row in data}
    observed = [float(by_label[k]["observed"]) * 100 for k in ITEM_ORDER]
    counts = [int(by_label[k]["count"]) for k in ITEM_ORDER]
    x = list(range(len(ITEM_ORDER)))

    fig, axis = plt.subplots(figsize=(9.5, 5.5))
    axis.axhspan(19, 21, color="#bbf7d0", alpha=0.65, label="±5% relative-error limit")
    axis.axhline(20, color="#111827", linestyle="--", linewidth=1.5, label="Expected 20%")
    bars = axis.bar(x, observed, color=["#f59e0b", "#ef4444", "#22c55e", "#06b6d4", "#a855f7"], width=0.62)
    for bar, rate, count in zip(bars, observed, counts):
        axis.text(bar.get_x() + bar.get_width() / 2, rate + 0.45, f"{count:,}\n{rate:.2f}%", ha="center", fontsize=9)
    axis.set_xticks(x, ITEM_ORDER)
    axis.set_ylim(0, 24)
    axis.set_ylabel("Observed selection rate (%)")
    axis.set_xlabel("Item label")
    axis.set_title("Production item generator · 100,000 draws (seed 20261025)")
    axis.grid(axis="y", alpha=0.2)
    axis.legend(loc="lower center", bbox_to_anchor=(0.5, -0.20), ncol=2, frameon=False)
    fig.tight_layout(rect=(0, 0.10, 1, 1))
    fig.savefig(ROOT / "item-distribution.png", dpi=180, bbox_inches="tight")
    plt.close(fig)


if __name__ == "__main__":
    plot_blocks()
    plot_items()
