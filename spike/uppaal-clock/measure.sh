#!/bin/sh
cd "$(dirname "$0")"
mkdir -p runs
for scale in 1 10; do
    for variant in counter delay fixed; do
        if [ $variant = counter ]; then model=model$scale.xml; else model=clock-$variant-$scale.xml; fi
        for case_name in example proof; do
            if [ $case_name = proof ]; then flags="-t 1 -A"; else flags="-t 1"; fi
            for repetition in 1 2 3; do
                quiet=$(echo not checked)
                output=runs/$variant.$scale.$case_name.$repetition
                start=$(date +%s.%N)
                timeout 600 verifyta -u $flags $model $case_name.q > $output.out 2> $output.err
                end=$(date +%s.%N)
                awk "BEGIN{print $end - $start}" > $output.time
                verdict=$(grep -o "Formula is [A-Za-z ]*satisfied\|MAY[A-Z a-z]*" $output.out | head -1)
                explored=$(grep -o "States explored *: *[0-9]*" $output.out | grep -o "[0-9]*$")
                echo "$variant scale=$scale $case_name rep=$repetition time=$(tail -1 $output.time) explored=$explored verdict=$verdict ($quiet)"
            done
        done
    done
done
