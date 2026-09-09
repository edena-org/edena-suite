# Vendored: T-SNE-Java (Barnes-Hut t-SNE) — BSD 3-Clause

Source: https://github.com/lejon/T-SNE-Java, tag `v2.5.0` (module `tsne-core`), Copyright (c) Leif Jonsson 2014,
original Python implementation Copyright (c) Laurens van der Maaten. License: BSD 3-Clause — see `LICENSE.md`
in this directory (the notice must be retained in source and binary redistributions).

Why vendored: the artifact `com.github.lejon.T-SNE-Java:tsne:v2.5.0` was only ever published through jitpack.io,
which has since purged the built jar (404), so a clean build could no longer resolve it. Newer tags on jitpack are
shaded fat jars that bundle Apache POI / commons unrelocated and would clash with ada-server's own dependencies.

What is included: only the subset reachable from `StatsService.performTSNE` — `BHTSne`, `ParallelBHTsne`,
`TSneConfig` and their helpers (`com.jujutsu.tsne.barneshut.*`, `TSne`, `TSneConfiguration`,
`PrincipalComponentAnalysis`, `com.jujutsu.utils.MatrixOps`). The dense (non Barnes-Hut) implementations,
`BlasOps`/`EjmlOps`, demos and `BTreePrinterTest` are omitted. Files are verbatim copies except `MatrixOps`, which is
trimmed to the single method the Barnes-Hut classes call (`extractRowFromFlatMatrix`); that drops the upstream JAMA
dependency. The only external dependency is EJML 0.26 (`com.googlecode.efficient-java-matrix-library:core`), used by
`PrincipalComponentAnalysis` for the optional PCA pre-step (`TSNESetting.pcaDims`), declared in `ada-server/build.sbt`.
