package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * The focus highlight, tvOS style: it leaves the artwork untouched and adds only light. A soft halo in
 * the app's own colour, a thin rim that is brightest at the top, a faint top specular, and a sheen that
 * sweeps across once when focus lands. One instance floats over the grid and springs between tiles.
 */
public class GlassView extends View {

    /** Space around the tile reserved for the halo. */
    static final float INSET_DP = 14f;
    private static final int HALO_STEPS = 6;

    private final Paint specular = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint halo = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path clip = new Path();
    private final float density;
    private float radius;
    private int haloRgb = 0xFFFFFF;
    private float sweep = 1f;   // 0..1 while the sheen crosses, 1 when idle
    private final ValueAnimator sweepAnim = ValueAnimator.ofFloat(0f, 1f);

    public GlassView(Context context, AttributeSet attrs) {
        super(context, attrs);
        density = getResources().getDisplayMetrics().density;
        radius = 20f * density;
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(1.5f * density);
        halo.setStyle(Paint.Style.STROKE);
        sweepAnim.setDuration(620);
        sweepAnim.setInterpolator(new DecelerateInterpolator(1.4f));
        sweepAnim.addUpdateListener(a -> {
            sweep = (float) a.getAnimatedValue();
            invalidate();
        });
    }

    void setRadius(float px) {
        radius = px;
        rebuildClip();
        invalidate();
    }

    /** Colours the halo: the app's colour lifted halfway to white so it reads as light, not paint. */
    void setTint(int color) {
        int r = ((color >> 16 & 0xFF) + 255) / 2, g = ((color >> 8 & 0xFF) + 255) / 2, b = ((color & 0xFF) + 255) / 2;
        int rgb = r << 16 | g << 8 | b;
        if (rgb == haloRgb) return;
        haloRgb = rgb;
        invalidate();
    }

    /** One pass of light across the tile. */
    void playSheen() {
        sweepAnim.cancel();
        sweepAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        specular.setShader(new LinearGradient(0, 0, 0, h * 0.5f, 0x30FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, 0xF2FFFFFF, 0x4DFFFFFF, Shader.TileMode.CLAMP));
        float band = w * 0.34f;
        sheen.setShader(new LinearGradient(0, 0, band, 0,
                new int[]{0x00FFFFFF, 0x3DFFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        rebuildClip();
    }

    private void rebuildClip() {
        float inset = INSET_DP * density;
        rect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        clip.reset();
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (rect.width() <= 0) return;

        // Halo: widening strokes whose inner edge stays on the rim, so they stack into a soft falloff.
        for (int i = 1; i <= HALO_STEPS; i++) {
            float width = i * 2.2f * density;
            float o = width / 2f;
            halo.setStrokeWidth(width);
            halo.setColor((0x2A / i) << 24 | haloRgb);
            canvas.drawRoundRect(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o, radius + o, radius + o, halo);
        }

        canvas.save();
        canvas.clipPath(clip);
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.5f, specular);
        if (sweep < 1f) {
            float band = getWidth() * 0.34f;
            float skew = 0.35f;
            float travel = rect.width() + band + rect.height() * skew;
            canvas.translate(rect.left - band + travel * sweep, rect.bottom);
            canvas.skew(-skew, 0f);
            sheen.setAlpha((int) (255 * (1f - sweep)));
            canvas.drawRect(0, -rect.height(), band, 0, sheen);
        }
        canvas.restore();

        canvas.drawRoundRect(rect, radius, radius, rim);
    }
}
