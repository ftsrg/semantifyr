# Example models

SysML v2 models that the frontend's tests verify.

A served model holds the cases live serves; its other cases are in `<model>.further-cases` beside it, which the build appends to the model for the tests.

`manifest.json` lists every verification case with its expected verdict and the test tier that runs it; `SysMLCorpusTest` fails when it drifts from the models. The models live serves are in the examples registry, `subprojects/semantifyr-editor-common/src/examples/examples.json`.
