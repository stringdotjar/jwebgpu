/*
 * MIT License
 *
 * Copyright (c) 2026 stringdotjar
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.stringdotjar.jwebgpu.samples.android;

import android.app.Activity;
import android.os.Bundle;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

/**
 * Shows a surface and runs the device checks of {@link DeviceRun} on a background thread once the surface exists.
 *
 * <p>Start it from the launcher, or with {@code adb shell am start -n
 * io.github.stringdotjar.jwebgpu.samples.android/.MainActivity}, and read the results with
 * {@code adb logcat -s jwebgpu}.
 */
public final class MainActivity extends Activity implements SurfaceHolder.Callback {

  private DeviceRun run;
  private boolean started;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    // The test needs a visible surface, so the app asks to show over the lock screen and to keep the screen on.
    setShowWhenLocked(true);
    setTurnScreenOn(true);
    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    SurfaceView view = new SurfaceView(this);
    view.getHolder().addCallback(this);
    setContentView(view);
  }

  @Override
  public void surfaceCreated(SurfaceHolder holder) {}

  @Override
  public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
    // The size is known now, so the run can configure the surface. One run per surface is enough.
    if (!started) {
      started = true;
      Surface surface = holder.getSurface();
      run = new DeviceRun(surface, width, height);
      run.start();
    }
  }

  @Override
  public void surfaceDestroyed(SurfaceHolder holder) {
    DeviceRun current = run;
    run = null;
    if (current != null) {
      current.stopAndWait();
    }
  }
}
