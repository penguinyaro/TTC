package com.example.jbyoung.ttc_ms;

import android.util.Log;

//		Class for solving 3 linear equations in 3 unknowns and
//		manipulating 3-vectors and 3x3 matrices

//		Avoided "new" in the following to limit garbage collection
//		(which is why we pass in "work area" vectors and matrices)

class Solve33 {
    private static double dotProduct (double[] a, double[] b)
    {
        return a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
    }

    private static void crossProduct (double[]a, double[] b, double[] c)
    {	// result in c
        c[0] = a[1]*b[2] - a[2]*b[1];
        c[1] = a[2]*b[0] - a[0]*b[2];
        c[2] = a[0]*b[1] - a[1]*b[0];
    }

    private static double vectorMagnitude (double[] v)
    {
        return Math.sqrt(dotProduct(v, v));
    }

    // tripleProduct needs extra arg to hold a cross product (to avoid allocation)
    private static double tripleProduct (double[] a, double[] b, double[] c, double[] crs)
    {
        crossProduct(b, c, crs);
        return dotProduct(a, crs);
    }

    // determinant needs extra arg to hold a cross product (to avoid allocation)
    private static double determinant (double[][] M, double[] crs)
    {
        return tripleProduct(M[0], M[1], M[2], crs);
    }

    private static void matrixTimesVector (double[][] M, double[] x, double[] b)
    {	// result in b
        for (int i=0; i < 3; i++) b[i] = dotProduct(M[i], x);
    }

    private static void vectorSum (double[] a, double[] b, double[] c)
    {	// not used
        for (int i=0; i < 3; i++) c[i] = a[i] + b[i];
    }

    private static void vectorDifference (double[] a, double[] b, double[] c)
    {
        for (int i=0; i < 3; i++) c[i] = a[i] - b[i];
    }

    private static void vectorScale (double[] a, double [] b, double s)
    {	// result in b
        for (int i=0; i < 3; i++) b[i] = a[i] * s;
    }

    private static void vectorNormalize (double[] a, double[] b)
    { // not used
        double sze = vectorMagnitude(a);
        if (sze == 0.0) {
            Log.e("vectorNormalize", "Vector normalization error");
        }
        else vectorScale(a, b, 1.0/sze);
    }

    //  needs extra argument for the difference (to avoid allocation)
    private static double vectorDifferenceMagnitude (double[] a, double[] b, double[] c)
    {
        vectorDifference(a, b, c);
        return vectorMagnitude(c);
    }

    private static void matrixTranspose (double [][] M, double [][] Mtranspose)
    {
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++)
                Mtranspose[j][i] = M[i][j];
        }
    }

    // solveEquations makes use of matrix Mtranspose and vector crs to avoid allocation
	// returns determinant (which is zero if the matrix is singular)
	
    static double solveEquations (double[][] M, double[][]Mtranspose, double[] b, double[] x, double crs[])
    {	// 3 equations, 3 unknowns
        String TAG="solveEquations";
        boolean debugFlag=false;
        double deter = determinant(M, crs);
        if (debugFlag) Log.d(TAG, "Determinant " + deter);
        if (deter == 0.0) {	// NOTE: Determinant here may be huge...
            x[0] = x[1] = x[2] = 0;
            if (debugFlag) Log.e(TAG, "Determinant is zero " + deter);
            return deter;	// failure
        }
        else {
            matrixTranspose(M, Mtranspose);
            x[0] = tripleProduct(Mtranspose[1], Mtranspose[2], b, crs)/deter;
            x[1] = tripleProduct(Mtranspose[2], Mtranspose[0], b, crs)/deter;
            x[2] = tripleProduct(Mtranspose[0], Mtranspose[1], b, crs)/deter;
            if (debugFlag) Log.d(TAG, "x: " + x[0] + " " + x[1] + " " + x[2]);
            if (debugFlag) {	//	check the solution by back substitution
                matrixTimesVector(M, x, crs);
                double err = vectorDifferenceMagnitude(crs, b, crs);
//				err should be small  
              Log.d(TAG, "error in solution is " + err);
            }
        }
        return deter;	// success
    }
}
