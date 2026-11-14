#!/bin/sh
set -eu
cd "$(dirname "$0")"
root=../..
cli=$root/subprojects/semantifyr-cli/build/install/semantifyr/bin/semantifyr
library=$root/subprojects/frontends/gamma/models/libraries/default
compiled=$root/subprojects/frontends/gamma/gamma-frontend/build/compiled-examples/Crossroads.oxsts
cp $compiled Crossroads1.oxsts
sed -E 's/(redefine refers value: int = )([0-9]+)$/\1\20/' $compiled > Crossroads10.oxsts
for scale in 1 10; do
    for case_name in ACanBeGreen UnreachableGreenGreen; do
        $cli verify -l $library --case-name Crossroads::$case_name --portfolio uppaal-default -t 300 -d generated$scale/$case_name Crossroads$scale.oxsts || true
    done
    cp generated$scale/ACanBeGreen/*/model.xml model$scale.xml
    for variant in delay fixed; do
        python3 -I to_clock.py model$scale.xml clock-$variant-$scale.xml $variant
    done
done
cp generated1/ACanBeGreen/*/model.q example.q
cp generated1/UnreachableGreenGreen/*/model.q proof.q
