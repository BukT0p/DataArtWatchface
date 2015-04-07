package com.dataart.dataartwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class WatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "WatchFaceService";
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.ENGLISH);
    private static final SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.ENGLISH);
    private static final SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEE", Locale.ENGLISH);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        private Paint hourPaint, minutePaint, secondPaint, tickPaint, textPaint;
        private boolean isMuted;
        private Calendar calendar;
        boolean registeredTimeZoneReceiver = false;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean isLowBitAmbient;
        private Bitmap backgroundBitmap, hourBitmap, minBitmap;
        private Bitmap backgroundScaledBitmap, hourScaledBitmap, minScaledBitmap;

        /**
         * Handler to update the time once a second in interactive mode.
         */
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "updating time");
                        }
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS
                                    - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar = Calendar.getInstance();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = WatchFaceService.this.getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.bg);
            backgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
//            hourBitmap = ((BitmapDrawable) resources.getDrawable(R.drawable.hour_hand)).getBitmap();
//            minBitmap = ((BitmapDrawable) resources.getDrawable(R.drawable.min_hand)).getBitmap();

            hourPaint = new Paint();
            hourPaint.setARGB(255, 200, 200, 200);
            hourPaint.setStrokeWidth(5.f);
            hourPaint.setAntiAlias(true);
            hourPaint.setStrokeCap(Paint.Cap.ROUND);

            minutePaint = new Paint();
            minutePaint.setARGB(255, 200, 200, 200);
            minutePaint.setStrokeWidth(3.f);
            minutePaint.setAntiAlias(true);
            minutePaint.setStrokeCap(Paint.Cap.ROUND);

            secondPaint = new Paint();
            secondPaint.setARGB(255, 55, 204, 230);
            secondPaint.setStrokeWidth(2.f);
            secondPaint.setAntiAlias(true);
            secondPaint.setStrokeCap(Paint.Cap.ROUND);

            tickPaint = new Paint();
            tickPaint.setARGB(100, 255, 255, 255);
            tickPaint.setStrokeWidth(2.f);
            tickPaint.setAntiAlias(true);

            textPaint = new Paint();
            textPaint.setARGB(255, 15, 164, 190);
            textPaint.setStrokeWidth(1.f);
            textPaint.setTextSize(24.f);
            textPaint.setAntiAlias(true);

            calendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            isLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + isLowBitAmbient);
            }
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTimeTick: ambient = " + isInAmbientMode());
            }
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            if (isLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                hourPaint.setAntiAlias(antiAlias);
                minutePaint.setAntiAlias(antiAlias);
                secondPaint.setAntiAlias(antiAlias);
                tickPaint.setAntiAlias(antiAlias);
                textPaint.setAntiAlias(antiAlias);
            }
            invalidate();

            // Whether the timer should be running depends on whether we're in ambient mode (as well
            // as whether we're visible), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == android.support.wearable.watchface.WatchFaceService.INTERRUPTION_FILTER_NONE);
            if (isMuted != inMuteMode) {
                isMuted = inMuteMode;
                hourPaint.setAlpha(inMuteMode ? 100 : 200);
                minutePaint.setAlpha(inMuteMode ? 100 : 200);
                textPaint.setAlpha(inMuteMode ? 100 : 200);
                secondPaint.setAlpha(inMuteMode ? 80 : 200);
                invalidate();
            }
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            calendar = Calendar.getInstance();

            int width = bounds.width();
            int height = bounds.height();

            // Draw the background, scaled to fit.
            if (backgroundScaledBitmap == null
                    || backgroundScaledBitmap.getWidth() != width
                    || backgroundScaledBitmap.getHeight() != height) {
                backgroundScaledBitmap = Bitmap.createScaledBitmap(backgroundBitmap,
                        width, height, true /* filter */);
            }
            canvas.drawBitmap(backgroundScaledBitmap, 0, 0, null);

            // Find the center. Ignore the window insets so that, on round watches with a
            // "chin", the watch face is centered on the entire screen, not just the usable
            // portion.
            float centerX = width / 2f;
            float centerY = height / 2f;

            float secRot = calendar.get(Calendar.SECOND) / 30f * (float) Math.PI;
            int minutes = calendar.get(Calendar.MINUTE);
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((calendar.get(Calendar.HOUR_OF_DAY) + (minutes / 60f)) / 6f) * (float) Math.PI;
            float secLength = centerX - 130;
            float minLength = centerX - 40;
            float hrLength = centerX - 80;

            textPaint.setTextSize(24.f);
            canvas.drawText(monthFormat.format(calendar.getTime()), centerX + 2, centerY - 50, textPaint);
            textPaint.setTextSize(20.f);
            canvas.drawText(dayFormat.format(calendar.getTime()), centerX + 10, centerY - 25, textPaint);
            textPaint.setTextSize(16.f);
            canvas.drawText(dayOfWeekFormat.format(calendar.getTime()), centerX + 34, centerY + 45, textPaint);

            if (!isInAmbientMode()) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;
                canvas.drawLine(centerX - 52, centerY + 18, centerX + secX - 52, centerY + secY + 18, secondPaint);
            }

//            // Draw the background, scaled to fit.
//            if (hourScaledBitmap == null) {
//                hourScaledBitmap = Bitmap.createScaledBitmap(hourBitmap, 9, 80, true /* filter */);
//            }
//
//            if (minScaledBitmap == null) {
//                minScaledBitmap = Bitmap.createScaledBitmap(minBitmap, 14, 120, true /* filter */);
//            }
//            canvas.save();
//            canvas.rotate((hrRot - 90), centerX, centerY);
//            canvas.drawBitmap(hourScaledBitmap, centerX - 4, 80, null);
//            canvas.restore();
//            canvas.save();
//            canvas.rotate((minRot-90), centerX, centerY);
//            canvas.drawBitmap(minScaledBitmap, centerX - 7, 45, null);
//            canvas.restore();
//            Log.d(TAG, "hr Rot = "+(hrRot - 90)+" min="+(minRot-90));

            float minX = (float) Math.sin(minRot) * minLength;
            float minY = (float) -Math.cos(minRot) * minLength;
            canvas.drawLine(centerX, centerY, centerX + minX, centerY + minY, minutePaint);


            float hrX = (float) Math.sin(hrRot) * hrLength;
            float hrY = (float) -Math.cos(hrRot) * hrLength;
            canvas.drawLine(centerX, centerY, centerX + hrX, centerY + hrY, hourPaint);

        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onVisibilityChanged: " + visible);
            }

            if (visible) {
                registerReceiver();
                calendar = Calendar.getInstance();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            WatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }
    }
}
