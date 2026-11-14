#!/bin/sh
# usage: matrix.sh <output root> <entry> ...; entry is name=library,model,case,portfolio; three interleaved repetitions
spike=/home/armin/work/ftsrg/semantifyr/.claude/worktrees/spike-trace-elapsed-time/spike
lock=/tmp/claude-1000/-home-armin-work-arminzavada-phd/d655dda2-8412-445a-a5c4-fd56cb88f6bf/scratchpad/heavy.lock
root=$1
shift
for repetition in 1 2 3; do
    for entry in "$@"; do
        name=${entry%%=*}
        IFS=, read library model case_name portfolio <<END
${entry#*=}
END
        output=$root/$name.$repetition
        flock "$lock" sh -c "
            quiet=\$(sh $spike/quiet.sh)
            before=\$(sh $spike/others.sh)
            sh $spike/run.sh $library $model $case_name $portfolio $output
            after=\$(sh $spike/others.sh)
            echo \"\$quiet \$(( (after - before) / 100 ))\" > $output/quiet-and-others.txt
        "
        echo "$output $(cat $output/quiet-and-others.txt)"
    done
done
