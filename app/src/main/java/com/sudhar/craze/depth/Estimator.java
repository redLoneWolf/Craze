package com.sudhar.craze.depth;

import android.graphics.Bitmap;

public interface Estimator {
    Bitmap estimateDepth(Bitmap bitmap);

    void close();
}
