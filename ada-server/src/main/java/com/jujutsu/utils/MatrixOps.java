package com.jujutsu.utils;

/**
 * Trimmed copy of {@code com.jujutsu.utils.MatrixOps} from T-SNE-Java v2.5.0 (BSD 3-Clause, see LICENSE.md in
 * the parent directory). The upstream class is ~1600 lines of general matrix helpers depending on JAMA and EJML;
 * the Barnes-Hut t-SNE classes vendored here call exactly one of them, so only that method is kept, verbatim.
 */
public class MatrixOps {

	public static double [] extractRowFromFlatMatrix(double[] flatMatrix, int rowIdx, int dimension) {
		double [] point = new double[dimension];
		int offset = rowIdx * dimension;
		for (int j = 0; j < dimension; j++) {
			point[j] = flatMatrix[offset+j];
		}
		return point;
	}

}
