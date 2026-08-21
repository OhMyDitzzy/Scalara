package id.ditzzy.scalara.setup;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.ColorInt;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

import id.ditzzy.scalara.R;

/**
 * A segmented step indicator, similar to Material 3's "wizard" stepper pattern.
 *
 * Unlike {@link com.google.android.material.progressindicator.LinearProgressIndicator},
 * which renders one continuous track, this draws {@link #stepCount} discrete
 * segments separated by a fixed gap, so each step of a wizard has a clear boundary.
 * Completed segments animate their fill and colour when {@link #setCurrentStep(int)}
 * is called.
 */
public class StepIndicatorView extends View {

    private static final long SEGMENT_ANIM_DURATION_MS = 320;

    private int stepCount = 3;
    private int currentStep = 1; // 1-indexed: the step currently active/being completed
    private boolean hasRenderedOnce = false;

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF segmentRect = new RectF();

    private float gapPx;
    private float trackHeightPx;
    private float cornerRadiusPx;

    @ColorInt private int trackColor;
    @ColorInt private int fillColor;

    private final List<Float> segmentFraction = new ArrayList<>();

    public StepIndicatorView(Context context) {
        this(context, null);
    }

    public StepIndicatorView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StepIndicatorView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        gapPx = dp(8);
        trackHeightPx = dp(4);
        cornerRadiusPx = trackHeightPx / 2f;

        trackColor = ContextCompat.getColor(context, R.color.blue_theme_surfaceContainerHighest);
        fillColor = ContextCompat.getColor(context, R.color.blue_theme_primary);

        trackPaint.setStyle(Paint.Style.FILL);
        fillPaint.setStyle(Paint.Style.FILL);

        setUpSegments();
    }

    private void setUpSegments() {
        segmentFraction.clear();
        for (int i = 0; i < stepCount; i++) {
            segmentFraction.add(0f);
        }
    }

    public void setStepCount(int stepCount) {
        if (stepCount < 1 || stepCount == this.stepCount) {
            return;
        }
        this.stepCount = stepCount;
        setUpSegments();
        hasRenderedOnce = false;
        invalidate();
    }

    /**
     * Sets the active step (1-indexed) and animates every segment to its resolved
     * fraction: fully filled for steps before {@code step}, empty after it.
     * The very first call (e.g. from {@code onCreate}) jumps straight to the
     * target state instead of animating, so the indicator doesn't visibly
     * "fill up" when the screen first appears.
     */
    public void setCurrentStep(int step) {
        int clamped = Math.max(1, Math.min(step, stepCount));
        currentStep = clamped;

        boolean animate = hasRenderedOnce;
        hasRenderedOnce = true;

        for (int i = 0; i < stepCount; i++) {
            float target = i < currentStep ? 1f : 0f;
            animateSegment(i, target, animate);
        }
    }

    private void animateSegment(int index, float target, boolean animate) {
        float current = segmentFraction.get(index);
        if (current == target) {
            return;
        }

        if (!animate) {
            segmentFraction.set(index, target);
            invalidate();
            return;
        }

        ValueAnimator animator = ValueAnimator.ofFloat(current, target);
        animator.setDuration(SEGMENT_ANIM_DURATION_MS);
        animator.setStartDelay(index * 60L);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(
                anim -> {
                    segmentFraction.set(index, (float) anim.getAnimatedValue());
                    invalidate();
                });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int totalGap = (int) (gapPx * (stepCount - 1));
        float segmentWidth = (width - totalGap) / (float) stepCount;

        float top = (getHeight() - trackHeightPx) / 2f;
        float bottom = top + trackHeightPx;

        trackPaint.setColor(trackColor);
        fillPaint.setColor(fillColor);

        for (int i = 0; i < stepCount; i++) {
            float left = i * (segmentWidth + gapPx);
            float right = left + segmentWidth;

            segmentRect.set(left, top, right, bottom);
            canvas.drawRoundRect(segmentRect, cornerRadiusPx, cornerRadiusPx, trackPaint);

            float fraction = segmentFraction.get(i);
            if (fraction > 0f) {
                float filledRight = left + segmentWidth * fraction;
                segmentRect.set(left, top, filledRight, bottom);
                canvas.drawRoundRect(segmentRect, cornerRadiusPx, cornerRadiusPx, fillPaint);
            }
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int desiredHeight = (int) dp(12);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);
        int height =
                MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.EXACTLY
                        ? heightSize
                        : desiredHeight;
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
