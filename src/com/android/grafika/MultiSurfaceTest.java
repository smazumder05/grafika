/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.grafika;

import android.opengl.GLES20;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;

import com.android.grafika.gles.EglCore;
import com.android.grafika.gles.WindowSurface;

/**
 * Exercises some less-commonly-used aspects of SurfaceView.  In particular:
 * <ul>
 * <li> We have three overlapping SurfaceViews.
 * <li> One is at the default depth, one is at "media overlay" depth, and one is on top of the UI.
 * <li> One is marked "secure".
 * </ul>
 */
public class MultiSurfaceTest extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = MainActivity.TAG;

    private SurfaceView mSurfaceView1;
    private SurfaceView mSurfaceView2;
    private SurfaceView mSurfaceView3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multi_surface_test);

        // #1 is at the bottom; mark it as secure just for fun
        mSurfaceView1 = (SurfaceView) findViewById(R.id.multiSurfaceView1);
        mSurfaceView1.getHolder().addCallback(this);
        mSurfaceView1.setSecure(true);

        // #2 is above it, in the "media overlay"; must be translucent or we will
        // totally obscure #1 and it will be ignored by the compositor
        mSurfaceView2 = (SurfaceView) findViewById(R.id.multiSurfaceView2);
        mSurfaceView2.getHolder().addCallback(this);
        mSurfaceView2.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mSurfaceView2.setZOrderMediaOverlay(true);

        // #3 is above everything, including the UI
        mSurfaceView3 = (SurfaceView) findViewById(R.id.multiSurfaceView3);
        mSurfaceView3.getHolder().addCallback(this);
        mSurfaceView3.getHolder().setFormat(PixelFormat.TRANSLUCENT);
        mSurfaceView3.setZOrderOnTop(true);
    }

    /**
     * Returns an ordinal value for the SurfaceHolder, or -1 for an invalid surface.
     */
    private int getSurfaceId(SurfaceHolder holder) {
        if (holder.equals(mSurfaceView1.getHolder())) {
            return 1;
        } else if (holder.equals(mSurfaceView2.getHolder())) {
            return 2;
        } else if (holder.equals(mSurfaceView3.getHolder())) {
            return 3;
        } else {
            return -1;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        int id = getSurfaceId(holder);
        if (id < 0) {
            Log.w(TAG, "surfaceCreated UNKNOWN holder=" + holder);
        } else {
            Log.d(TAG, "surfaceCreated #" + id + " holder=" + holder);

        }
    }

    /**
     * SurfaceHolder.Callback method
     * <p>
     * Draws when the surface changes.  Since nothing else is touching the surface, and
     * we're not animating, we just draw here and ignore it.
     */
    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d(TAG, "surfaceChanged fmt=" + format + " size=" + width + "x" + height +
                " holder=" + holder);

        int id = getSurfaceId(holder);
        boolean portrait = height > width;
        Surface surface = holder.getSurface();

        switch (id) {
            case 1:
                // default layer: circle on left / top
                if (portrait) {
                    drawCircleSurface(surface, width / 2, height / 4, width / 4);
                } else {
                    drawCircleSurface(surface, width / 4, height / 2, height / 4);
                }
                break;
            case 2:
                // media overlay layer: circle on right / bottom
                if (portrait) {
                    drawCircleSurface(surface, width / 2, height * 3 / 4, width / 4);
                } else {
                    drawCircleSurface(surface, width * 3 / 4, height / 2, height / 4);
                }
                break;
            case 3:
                // top layer: faint blue line
                if (portrait) {
                    int halfLine = width / 16 + 1;
                    drawRectSurface(surface, width/2 - halfLine, 0, width/2 + halfLine, height);
                } else {
                    int halfLine = height / 16 + 1;
                    drawRectSurface(surface, 0, height/2 - halfLine, width, height/2 + halfLine);
                }
                break;
            default:
                throw new RuntimeException("wha?");
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // ignore
        Log.d(TAG, "Surface destroyed holder=" + holder);
    }

    /**
     * Clears the surface, then draws a blue alpha-blended rectangle with GL.
     * <p>
     * Creates a temporary EGL context just for the duration of the call.
     */
    private void drawRectSurface(Surface surface, int left, int top, int right, int bottom) {
        EglCore eglCore = new EglCore();
        WindowSurface win = new WindowSurface(eglCore, surface, false);
        win.makeCurrent();
        GLES20.glClearColor(0, 0, 0, 0);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(left, top, right - left, bottom - top);
        GLES20.glClearColor(0.0f, 0.0f, 0.5f, 0.25f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        win.swapBuffers();
        win.release();
        eglCore.release();
    }

    /**
     * Clears the surface, then draws a filled circle with a shadow.
     * <p>
     * The Canvas drawing we're doing may not be fully implemented for hardware-accelerated
     * renderers (shadow layers only supported for text).  However, Surface#lockCanvas()
     * currently only returns an unaccelerated Canvas, so it all comes out looking fine.
     */
    private void drawCircleSurface(Surface surface, int x, int y, int radius) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setShadowLayer(radius / 4 + 1, 0, 0, Color.RED);

        Canvas canvas = surface.lockCanvas(null);
        Log.d(TAG, "drawCircleSurface: isHwAcc=" + canvas.isHardwareAccelerated());
        canvas.drawARGB(0, 0, 0, 0);
        canvas.drawCircle(x, y, radius, paint);
        surface.unlockCanvasAndPost(canvas);
    }
}
