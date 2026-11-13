/*
 * SPDX-FileCopyrightText: 2026 The Semantifyr Authors
 *
 * SPDX-License-Identifier: EPL-2.0
 */

export interface DiffLine {
  kind: 'same' | 'added' | 'removed';
  text: string;
}

// the lines of a longest common subsequence stay, and the lines removed come before the lines added in their place
export function lineDiff(before: string, after: string): DiffLine[] {
  const left = before.split('\n');
  const right = after.split('\n');
  const width = right.length + 1;
  const common = new Int32Array((left.length + 1) * width);
  const commonAt = (leftIndex: number, rightIndex: number): number => {
    return common[leftIndex * width + rightIndex] ?? 0;
  };
  for (let leftIndex = left.length - 1; leftIndex >= 0; leftIndex--) {
    for (let rightIndex = right.length - 1; rightIndex >= 0; rightIndex--) {
      common[leftIndex * width + rightIndex] = left[leftIndex] === right[rightIndex] ? commonAt(leftIndex + 1, rightIndex + 1) + 1 : Math.max(commonAt(leftIndex + 1, rightIndex), commonAt(leftIndex, rightIndex + 1));
    }
  }
  const lines: DiffLine[] = [];
  let leftIndex = 0;
  let rightIndex = 0;
  while (leftIndex < left.length || rightIndex < right.length) {
    const leftLine = left[leftIndex];
    const rightLine = right[rightIndex];
    if (leftLine !== undefined && leftLine === rightLine) {
      lines.push({ kind: 'same', text: leftLine });
      leftIndex++;
      rightIndex++;
    } else if (leftLine !== undefined && (rightLine === undefined || commonAt(leftIndex + 1, rightIndex) >= commonAt(leftIndex, rightIndex + 1))) {
      lines.push({ kind: 'removed', text: leftLine });
      leftIndex++;
    } else {
      lines.push({ kind: 'added', text: rightLine ?? '' });
      rightIndex++;
    }
  }
  return lines;
}
