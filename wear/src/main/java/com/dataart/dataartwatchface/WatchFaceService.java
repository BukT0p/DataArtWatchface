package com.dataart.dataartwatchface;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class WatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "WatchFaceService";
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final SimpleDateFormat dayFormat = new SimpleDateFormat("dd", Locale.ENGLISH);
    private static final SimpleDateFormat dayOfWeekFormat = new SimpleDateFormat("EEE", Locale.ENGLISH);
    private static final SimpleDateFormat monthFormat = new SimpleDateFormat("MMM", Locale.ENGLISH);

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        static final int MSG_UPDATE_TIME = 0;

        private Paint secondPaint, textPaint, monthTextPaint;
        private boolean mMute;
        private Calendar calendar;
        private boolean registeredTimeZoneReceiver = false;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean lowBitAmbient;

        private Bitmap backgroundBitmap, hourHandBmp, minHandBmp, backgroundScaledBitmap;
        private Matrix handMatrix;
        /**
         * Handler to update the time once a second in interactive mode.
         */
        final Handler updateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                if (MSG_UPDATE_TIME == message.what) {
                    invalidate();
                    if (shouldTimerBeRunning()) {
                        long timeMs = System.currentTimeMillis();
                        long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                        updateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                    }
                }
            }
        };

        final BroadcastReceiver timeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                calendar = Calendar.getInstance();
            }
        };

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(WatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            Resources resources = WatchFaceService.this.getResources();
            Drawable backgroundDrawable = resources.getDrawable(R.drawable.bg, getTheme());
            if (backgroundDrawable != null) {
                backgroundBitmap = ((BitmapDrawable) backgroundDrawable).getBitmap();
            }

            backgroundDrawable = resources.getDrawable(R.drawable.hand_min, getTheme());
            if (backgroundDrawable != null) {
                minHandBmp = Bitmap.createScaledBitmap(((BitmapDrawable) backgroundDrawable).getBitmap(), 131, 4, true);
            }

            backgroundDrawable = resources.getDrawable(R.drawable.hand_hour, getTheme());
            if (backgroundDrawable != null) {
                hourHandBmp = Bitmap.createScaledBitmap(((BitmapDrawable) backgroundDrawable).getBitmap(), 84, 8, true);
            }

            secondPaint = new Paint();
            secondPaint.setARGB(255, 55, 204, 230);
            secondPaint.setStrokeWidth(2.f);
            secondPaint.setAntiAlias(true);
            secondPaint.setStrokeCap(Paint.Cap.ROUND);

            textPaint = new Paint();
            textPaint.setARGB(255, 15, 164, 190);
            textPaint.setStrokeWidth(1.f);
            textPaint.setAntiAlias(true);

            monthTextPaint = new Paint();
            monthTextPaint.setARGB(255, 55, 204, 230);
            monthTextPaint.setStrokeWidth(1.f);
            monthTextPaint.setTextSize(24.f);
            monthTextPaint.setAntiAlias(true);

            calendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            lowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (lowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                secondPaint.setAntiAlias(antiAlias);
                textPaint.setAntiAlias(antiAlias);
                monthTextPaint.setAntiAlias(antiAlias);
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
            if (mMute != inMuteMode) {
                mMute = inMuteMode;
                textPaint.setAlpha(inMuteMode ? 100 : 200);
                monthTextPaint.setAlpha(inMuteMode ? 100 : 200);
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
                backgroundScaledBitmap = Bitmap.createScaledBitmap(backgroundBitmap, width, height, true);
                backgroundBitmap.recycle();
            }
            canvas.drawBitmap(backgroundScaledBitmap, 0, 0, null);
            float centerX = width / 2f;
            float centerY = height / 2f;

            float secRot = calendar.get(Calendar.SECOND) / 30f * (float) Math.PI;
            int minutes = calendar.get(Calendar.MINUTE);
            float minRot = minutes / 30f * (float) Math.PI;
            float hrRot = ((calendar.get(Calendar.HOUR) + (minutes / 60f)) / 6f) * (float) Math.PI;

            float secLength = centerX - 135;
            textPaint.setTextSize(28.f);
            canvas.drawText(dayFormat.format(calendar.getTime()), centerX + 4, centerY - 25, textPaint);
            textPaint.setTextSize(20.f);
            canvas.drawText(dayOfWeekFormat.format(calendar.getTime()), centerX + 35, centerY + 45, textPaint);
            canvas.drawText(monthFormat.format(calendar.getTime()), centerX, centerY - 55, monthTextPaint);
            if (!isInAmbientMode()) {
                float secX = (float) Math.sin(secRot) * secLength;
                float secY = (float) -Math.cos(secRot) * secLength;

                canvas.drawLine(centerX - 48, centerY + 15, centerX + secX - 48, centerY + secY + 15, secondPaint);
            }
            float degrees = (float) (Math.toDegrees(minRot) + 90) % 360;
            handMatrix = new Matrix();
            handMatrix.setRotate(degrees - 180);
            Bitmap rotatedMinBmp = Bitmap.createBitmap(minHandBmp, 0, 0, minHandBmp.getWidth(), minHandBmp.getHeight(), handMatrix, true);
            if (degrees <= 90) {
                canvas.drawBitmap(rotatedMinBmp, centerX - rotatedMinBmp.getWidth() + 2, centerY - rotatedMinBmp.getHeight() + 2, null);
            } else if (degrees <= 180) {
                canvas.drawBitmap(rotatedMinBmp, centerX - 2, centerY - rotatedMinBmp.getHeight() + 2, null);
            } else if (degrees < 270) {
                canvas.drawBitmap(rotatedMinBmp, centerX - 2, centerY - 2, null);
            } else {
                canvas.drawBitmap(rotatedMinBmp, centerX + 2 - rotatedMinBmp.getWidth(), centerY - 2, null);
            }

            degrees = (float) (Math.toDegrees(hrRot) + 90) % 360;
            handMatrix = new Matrix();
            handMatrix.setRotate(degrees - 180);
            Bitmap rotatedHourBmp = Bitmap.createBitmap(hourHandBmp, 0, 0, hourHandBmp.getWidth(), hourHandBmp.getHeight(), handMatrix, true);
            if (degrees <= 90) {
                canvas.drawBitmap(rotatedHourBmp, centerX - rotatedHourBmp.getWidth() + 4, centerY - rotatedHourBmp.getHeight() + 4, null);
            } else if (degrees <= 180) {
                canvas.drawBitmap(rotatedHourBmp, centerX - 4, centerY - rotatedHourBmp.getHeight() + 4, null);
            } else if (degrees <= 270) {
                canvas.drawBitmap(rotatedHourBmp, centerX - 4, centerY - 4, null);
            } else {
                canvas.drawBitmap(rotatedHourBmp, centerX - rotatedHourBmp.getWidth() + 4, centerY - 4, null);
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
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
            WatchFaceService.this.registerReceiver(timeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!registeredTimeZoneReceiver) {
                return;
            }
            registeredTimeZoneReceiver = false;
            WatchFaceService.this.unregisterReceiver(timeZoneReceiver);
        }

        /**
         * Starts the {@link #updateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            updateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                updateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #updateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

    }
}
