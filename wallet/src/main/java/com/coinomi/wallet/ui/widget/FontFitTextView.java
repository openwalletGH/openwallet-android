package com.coinomi.wallet.ui.widget;

import android.content.Context;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.widget.TextView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on
 * http://stackoverflow.com/questions/2617266/how-to-adjust-text-font-size-to-fit-textview?answertab=votes#tab-top
 */
public class FontFitTextView extends TextView {
    private static final Logger log = LoggerFactory.getLogger(FontFitTextView.class);

    //Attributes
    private float maxTextSize;
    private Paint mTestPaint;

    public FontFitTextView(Context context) {
        super(context);
        initialise();
    }

    public FontFitTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialise();
    }

    private void initialise() {
        mTestPaint = new Paint();
        mTestPaint.set(this.getPaint());
        maxTextSize = getTextSize();
        //max size defaults to the initially specified text size unless it is too small
    }

    /* Re size the font so the specified text fits in the text box
     * assuming the text box is the specified width.
     */
    private void refitText(String text, int textWidth)
    {
        if (textWidth <= 0)
            return;
        int targetWidth = textWidth - this.getPaddingLeft() - this.getPaddingRight();
        float hi = maxTextSize;
        float lo = 10;
        final float threshold = 0.5f; // How close we have to be

        mTestPaint.set(this.getPaint());

        while((hi - lo) > threshold) {
            float size = (hi+lo)/2;
            mTestPaint.setTextSize(size);
            if(mTestPaint.measureText(text) >= targetWidth)
                hi = size; // too big
            else
                lo = size; // too small
        }
        // Use lo so that we undershoot rather than overshoot
        super.setTextSize(TypedValue.COMPLEX_UNIT_PX, lo);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
        int width = getMeasuredWidth();
        int height = getMeasuredHeight();
        refitText(this.getText().toString(), parentWidth);
        this.setMeasuredDimension(parentWidth > width ? width : parentWidth, height);
    }

    @Override
    protected void onTextChanged(final CharSequence text, final int start, final int before, final int after) {
        super.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxTextSize);
    }

    @Override
    public void setTextSize(float size) {
        log.error("Use setMaxTextSize instead");
    }

    @Override
    public void setTextSize(int unit, float size) {
        log.error("Use setMaxTextSize instead");
    }

    @Override
    /** {@inheritDoc} */
    public void setText(CharSequence text, BufferType type) {
        final String DOUBLE_BYTE_WORDJOINER = "\u2060";
        String fixString = "";
        /* bug workaround https://code.google.com/p/android/issues/detail?id=17343#c9 */
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB_MR1
                && android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
            fixString = DOUBLE_BYTE_WORDJOINER;
        }
        super.setText(text + fixString, type);
    }

    /**
     * Set the max size (in pixels) of the default text size in this TextView.
     */
    public void setMaxTextSize(float maxTextSize) {
        this.maxTextSize = maxTextSize;
        super.setTextSize(TypedValue.COMPLEX_UNIT_PX, maxTextSize);
    }

    /**
     * Get the max size (in pixels) of the default text size in this TextView.
     */
    public float getMaxTextSize() {
        return maxTextSize;
    }
}