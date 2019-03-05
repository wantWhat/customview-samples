package com.sky.hyh.customviewsamples.customview.automaitcEditText;

import android.content.Context;
import android.graphics.Paint;
import android.support.v7.widget.AppCompatEditText;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import com.sky.hyh.customviewsamples.span.spandata.CustomTextSpanData;
import com.sky.hyh.customviewsamples.utils.SizeUtils;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by hyh on 2019/2/26 15:47
 * E-Mail Address：fjnuhyh122@gmail.com
 * 自动排版输入框,文本每行的字体大小可能不一样
 */
public class AutomaticEditText extends AppCompatEditText {
    public static final int WIDTH_OFFSET = 10;
    public static final int HEIGHT_OFFSET = 0;

    //默认字体大小
    public static final float DEF_FONT_SIZE = 16;
    /**
     * 控件的初始宽度，随着输入文本的变化，控件的宽度会改变
     */
    private int mInitWidgetWidth;
    private boolean mResetWidgetSize;

    /**
     * 最大文本高度
     */
    private int mMaxTextHeight;
    /**
     * 最大文本宽度
     */
    private int mMaxTextWidth;
    /**
     * 默认字体大小，单位：px
     */
    private float mDefFontSizePx;

    private List<LineData> mLineDataList;
    private String mLastText = "";
    private TextSizeAdjustHelper mTextSizeAdjustHelper;

    public AutomaticEditText(Context context) {
        this(context,null);
    }

    public AutomaticEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        setTextSize(DEF_FONT_SIZE);
        mDefFontSizePx = SizeUtils.sp2px(DEF_FONT_SIZE);
        Log.d("hyh", "AutomaticEditText: init: mDefFontSizePx="+mDefFontSizePx);
        mLineDataList = new ArrayList<>();
        //设为false，且不设置行间距，这样每行高度的累加值才等于文本总高度，即Layout.getHeight == Layout.getLineCount * TextPaint.getFontMetricsInt
        setIncludeFontPadding(false);
        //不设置行间距
        setLineSpacing(0,1);
        mTextSizeAdjustHelper = new TextSizeAdjustHelper(this);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        if((w != oldw || h != oldh) && !mResetWidgetSize){
            mResetWidgetSize = false;
            mMaxTextHeight = h - getPaddingTop() - getPaddingBottom() - SizeUtils.dp2px(HEIGHT_OFFSET);
            mInitWidgetWidth = w;
            mMaxTextWidth = w - getPaddingLeft() - getPaddingRight() - SizeUtils.dp2px(WIDTH_OFFSET);
            Log.d("hyh", "AutomaticEditText: onSizeChanged: mMaxTextHeight="+ mMaxTextHeight+ " ,mMaxTextWidth="+mMaxTextWidth);
        }
    }

    @Override
    public boolean onPreDraw() {
        boolean result = super.onPreDraw();
        refresh();
        return result;
    }

    private void refresh(){
        if(getLayout() != null){
            //注意这里需要构建一个text不是Spannable，字体大小是默认字体大小的Layout，这样才能准确得出每行应该显示的文本
            Layout defLayout = getDefLayout();
            String text = defLayout.getText().toString();
            Log.d("hyh", "AutomaticEditText: refresh: text="+text);
            boolean update = isUpdateText(defLayout);
            Log.d("hyh", "AutomaticEditText: refresh: update="+update);
            if(update) {
                spliteLineData(defLayout);
                matchMaxWidthFontSize();
                matchMaxHeightFontSize();
                updateText(text);
                int maxTextWidth = (int) calculateMaxWidth();
                if(maxTextWidth > 0){
                    mResetWidgetSize = true;
                    ViewGroup.LayoutParams layoutParams = getLayoutParams();
                    layoutParams.width = maxTextWidth + getPaddingLeft() +getPaddingRight() + SizeUtils.dp2px(WIDTH_OFFSET);
                    Log.d("hyh", "AutomaticEditText: refresh: layoutParams.width="+layoutParams.width);
                    setLayoutParams(layoutParams);
                }
            }
            mLastText = text;
        }
    }

    /**
     * 判断是否需要更新文本
     * @return
     */
    private boolean isUpdateText(Layout layout){
        boolean update = false;
        String text = layout.getText().toString();
        if(!mLastText.equals(text)) {
            Log.d("hyh", "AutomaticEditText: isUpdateText: 1");
            update = true;
        }else{
            int lineCount = layout.getLineCount();
            int size = mLineDataList.size();
            if(lineCount != size){
                Log.d("hyh", "AutomaticEditText: isUpdateText: 2");
                update = true;
            }else{
                for (int i = 0; i < lineCount; i++) {
                    int start = layout.getLineStart(i);
                    int end =layout.getLineEnd(i);
                    String rowStr = text.substring(start, end);
                    LineData lineData = mLineDataList.get(i);
                    String lineText = lineData.getLineText();
                    if (!rowStr.equals(lineText)) {
                        Log.d("hyh", "AutomaticEditText: isUpdateText: 3");
                        //原本的每行文字跟现在的每行文字不相同，说明排版变了，需要重新更新文本
                        update = true;
                        break;
                    }
                }
            }
        }
        return  update;
    }

    /**
     * 按行分割文本
     */
    private void spliteLineData(Layout layout){
        mLineDataList.clear();
        String text = layout.getText().toString();
        int lineCount = layout.getLineCount();
        Log.d("hyh", "AutomaticEditText: spliteLineData: text="+text+" ,lineCount="+lineCount);
        for (int i = 0; i < lineCount; i++) {
            int start = layout.getLineStart(i);
            int end = layout.getLineEnd(i);
            String rowStr = text.substring(start,end);
            CustomTextSpanData customTextSpanData = new CustomTextSpanData.Builder(start,end)
                .setTextSizePx(getPaint().getTextSize())
                .build();
            LineData lineData = new LineData(rowStr,customTextSpanData);
            Log.d("hyh", "AutomaticEditText: spliteLineData: lineData="+lineData.toString());
            mLineDataList.add(lineData);
        }
    }

    /**
     * 计算匹配最大文本宽度的字体大小
     */
    private void matchMaxWidthFontSize(){
        for(LineData lineData: mLineDataList){
            Paint paint = new Paint(getPaint());
            String lineText = lineData.getLineText();
            float textSize = paint.getTextSize();
            if(!TextUtils.isEmpty(lineText)){
                float textWidth = paint.measureText(lineText);
                Log.d("hyh", "AutomaticEditText: matchMaxWidthFontSize: textWidth="+textWidth+" ,mMaxTextWidth="+mMaxTextWidth);
                //按照宽高比缩放字体
                textSize = mMaxTextWidth / textWidth * textSize;
                paint.setTextSize(textSize);
                //缩放字体后还得检查行宽度是否大于最大文本宽度，如果大于则还需要调小字体
                textSize = mTextSizeAdjustHelper.calculateMatchWidthSize(paint,lineText,mMaxTextWidth);
            }
            lineData.setFontSizePx(textSize);
        }
    }

    /**
     * 计算匹配最大文本高度的字体大小
     */
    private void matchMaxHeightFontSize(){
        List<CustomTextSpanData> customTextSpanDataList = new ArrayList<>();
        for(LineData lineData: mLineDataList){
            CustomTextSpanData customTextSpanData = lineData.getCustomTextSpanData();
            customTextSpanDataList.add(customTextSpanData);
        }
        mTextSizeAdjustHelper.calculateMatchHeightSize(customTextSpanDataList,mMaxTextHeight);
    }

    private float calculateMaxWidth(){
        String maxLengthText = "";
        LineData maxLengthLineData = null;
        Paint copyPaint = new Paint(getPaint());
        float maxLineWidth = 0;
        for(LineData lineData: mLineDataList){
            String lineText = lineData.getLineText();
            Log.d("hyh", "AutomaticEditText: calculateMaxWidth: lineText="+lineText+" ,length="+lineText.length());
            if(lineText.length() > maxLengthText.length()){
                maxLengthText = lineText;
                maxLengthLineData = lineData;
            }

            copyPaint.setTextSize(lineData.getFontSizePx());
            float width = copyPaint.measureText(lineData.getLineText());
            if(width > maxLineWidth){
                maxLineWidth = width;
            }
        }

        if(maxLengthLineData == null){
            return maxLineWidth;
        }

        float maxLengthLineWidth = calculateMaxLengthLineWidth(maxLengthLineData);
        Log.d("hyh", "AutomaticEditText: calculateMaxWidth: maxLineWidth="+maxLineWidth+" ,maxLengthLineWidth="+maxLengthLineWidth);
        float maxWidth = maxLineWidth > maxLengthLineWidth? maxLineWidth:maxLengthLineWidth;
        Log.d("hyh", "AutomaticEditText: calculateMaxWidth: maxWidth="+maxWidth+" ,mMaxTextWidth="+mMaxTextWidth);
        if(maxWidth > mMaxTextWidth){
            maxWidth = mMaxTextWidth;
        }

        return maxWidth;
    }

    private float calculateMaxLengthLineWidth(LineData lineData){
        Paint paint = new Paint(getPaint());
        float width = paint.measureText(lineData.getLineText());
        float rate = (mInitWidgetWidth - getPaddingLeft() - getPaddingRight()) / width;
        Log.d("hyh", "AutomaticEditText: calculateMaxWidth: width="+width+" ,rate="+rate);
        float fontSize = lineData.getFontSizePx();
        Log.d("hyh", "AutomaticEditText: calculateMaxWidth: oriFontSize="+paint.getTextSize()+" ,nowFontSize="+fontSize);
        paint.setTextSize(fontSize);
        float textWidth = paint.measureText(lineData.getLineText());
        float maxWidth = textWidth * rate;
        Log.d("hyh", "AutomaticEditText: calculateMaxWidth: textWidth="+textWidth+" ,maxWidth="+maxWidth);
        return maxWidth;
    }

    private void updateText(String text){
        Log.d("hyh", "AutomaticEditText: updateText: text="+text);
        SpannableString spannableString = new SpannableString(text);
        for(LineData lineData: mLineDataList){
            Log.d("hyh", "AutomaticEditText: updateText: lineText="+lineData.getLineText()+" ,lineFontSize="+lineData.getFontSizePx());
            CustomTextSpanData customTextSpanData = lineData.getCustomTextSpanData();
            spannableString.setSpan(customTextSpanData.onCreateSpan(),customTextSpanData.getStartIndex(),customTextSpanData.getEndIndex(),
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        setText(spannableString);
        setSelection(text.length());
    }

    private Layout getDefLayout(){
        int width = mInitWidgetWidth - getPaddingLeft() - getPaddingRight();
        //注意这里的text是String不是Spannable
        String textString = getText().toString();
        DynamicLayout dynamicLayout = new DynamicLayout(textString,new TextPaint(getPaint()),width,getLayout().getAlignment(),getLayout().getSpacingMultiplier(),getLayout().getSpacingAdd(),getIncludeFontPadding());
        return dynamicLayout;
    }

    public static class LineData{
        //行文本
        private String mLineText;
        private CustomTextSpanData mCustomTextSpanData;

        public LineData(String lineStr, CustomTextSpanData customTextSpanData) {
            mLineText = lineStr;
            mCustomTextSpanData = customTextSpanData;
        }

        public String getLineText() {
            return mLineText;
        }

        public int getStartIndex() {
            return mCustomTextSpanData.getStartIndex();
        }

        public int getEndIndex() {
            return mCustomTextSpanData.getEndIndex();
        }

        public float getFontSizePx(){
            return mCustomTextSpanData.getTextSizePx();
        }

        public void setFontSizePx(float textSizePx){
            mCustomTextSpanData.setTextSizePx(textSizePx);
        }

        public CustomTextSpanData getCustomTextSpanData() {
            return mCustomTextSpanData;
        }

        @Override
        public String toString() {
            return "LineData{" +
                "mLineText='" + mLineText + '\'' +
                ", mStartIndex=" + mCustomTextSpanData.getStartIndex() +
                ", mEndIndex=" + mCustomTextSpanData.getEndIndex() +
                '}';
        }
    }
}