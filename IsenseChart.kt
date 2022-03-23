package isense.com.ui.iSenseUI

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.annotation.RequiresApi
import com.pdog.dimension.dp
import isense.com.R
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

class IsenseChart : View {
    var isShowIndicator = false
    val rangeText = "Range"

    var currentMS: Long = 0

    private var mLastX: Float = 0F
    private var mCurrentX: Float = 0F
    private var mCurrentY: Float = 0F
    private var mMoveX: Float = 0F


    val TOUCHMOVEDURATION = 200
    val UNCLICKED = -1
    var indexOnClicked = UNCLICKED

    var initialWidth: Float = 367F

    var indicatorTextColor = Color.parseColor("#939296")
    var mAxisColor = Color.parseColor("#F8F8F8")
    var mTextColor = Color.parseColor("#D0D0D0")
    var mLineColor = Color.parseColor("#EA4359")
    var mLineWidth = 8.dp.toFloat()
    var mLineSpace = 6.dp.toFloat()
    var mIndicateLabelCorner = 4.dp.toFloat()

    var mYAxisRange = Pair(0, 150)
    var mXAxisRange = Pair(0, 24)
    var mTextSize = 11.dp
    var mIndicatorTimeSize = 20.dp.toFloat()
    var mIndicatorDataSize = 35.dp.toFloat()
    var mIndicatorBMPSize = 26.dp.toFloat()

    var mAxisPaint = Paint()
    var mLinePaint = Paint()
    var mTextLabelPaint = Paint()
    var mIndicateLabelLinePaint = Paint()
    var mIndicateLabelPaint = Paint()
    var mIndicateLabelTextPaint = Paint()

    var mBottomTextHeight = 15.dp
    var mRightTextWidth = 8F

    var mChartWithLabelHeight: Float = 273.dp.toFloat()
    var mChartHeight: Float = 257.dp.toFloat()
    var mChartWidth: Float = 334.dp.toFloat()

    var mTotalWidth: Float = 367.dp.toFloat()
    var mTotalHeight: Float = 367.dp.toFloat()
    var mIndicateLabelHeight = 76.dp.toFloat()
    var mIndicateLabelWidth = 130.dp.toFloat()
    var mIndicateSpace = (mTotalHeight - mChartWithLabelHeight - mIndicateLabelHeight) / 2

    var hasTimer = false
    var isScrolling = false
    var mHorizontalLineSliceAmount = 3
    var mVerticalLineSliceAmount = 4

    var mVerticalSliceAmount = 24

    var mViewStartX = mTotalWidth - mRightTextWidth
        set(value){
            field = value
            updateCurrentDrawRange()
            invalidate()
        }
    var mInitialStartX: Float = mViewStartX

    var mValueArray: ArrayList<HeartRateChartEntry> = arrayListOf(HeartRateChartEntry())

    var drawRangeLeft = 0
    var drawRangeRight = 0
    var isLongTouch = false

    var mScope = MainScope()
    private lateinit var showIndicatorJob: Job

    var leftBorderLine = (mLineWidth * 2 + mLineSpace * 2 - mLineSpace / 2)
    var rightBorderLine = mViewStartX

    var chartRightMargin = (mLineSpace / 2 + mLineWidth / 2)

    constructor(context: Context) : super(context, null, 0) {
        initValueArray()
        initPaints()
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val styleArray = context.obtainStyledAttributes(attrs, R.styleable.IsenseChart)
        initValueArray()
        initPaints()
    }


    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initValueArray()
        initPaints()
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(
        context,
        attrs,
        defStyleAttr,
        defStyleRes
    ) {
        initValueArray()
        initPaints()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)

        drawBackGroundAxis(canvas)
        drawIndicators(canvas, indexOnClicked)
        drawLines(canvas)
        drawDataLabels(canvas)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {

        mTotalWidth = (width * 0.9175).toFloat()
        mTotalHeight = mTotalWidth
        mLineWidth = mTotalWidth / initialWidth * 8
        mLineSpace = mTotalWidth / initialWidth * 6
        mIndicateLabelHeight = mTotalHeight / initialWidth * 76
        mIndicateLabelWidth = mTotalHeight / initialWidth * 145


        mChartWithLabelHeight = mTotalHeight / initialWidth * 273
        mChartHeight = mTotalHeight / initialWidth * 254
        mChartWidth = mTotalHeight / initialWidth * 334
        mBottomTextHeight = (mTotalHeight / initialWidth * 15).toInt()
        mTextSize = (mTotalHeight / initialWidth * 11).toInt()
        mViewStartX = (mLineWidth * 2 + mLineSpace * 2) + mChartWidth
        mInitialStartX = mViewStartX

        mRightTextWidth = mTextLabelPaint.measureText("${mYAxisRange.second}")

        mIndicatorTimeSize = mTotalWidth / initialWidth * 14
        mIndicatorDataSize = mTotalWidth / initialWidth * 25
        mIndicatorBMPSize = mTotalWidth / initialWidth * 19

        mIndicateSpace = (mTotalHeight - mChartWithLabelHeight - mIndicateLabelHeight) / 2

        leftBorderLine = (mLineWidth * 2 + mLineSpace * 2 - mLineSpace / 2)
        rightBorderLine = mViewStartX

        chartRightMargin = (mLineSpace / 2 + mLineWidth / 2)
        super.onLayout(changed, left, top, right, bottom)
    }

    private fun drawDataLabels(canvas:Canvas?){
        if(isShowIndicator){return}

        //get value of side points
        var rightSideIndex = ((mViewStartX - mInitialStartX)/(mLineWidth+mLineSpace)).toInt()
        var leftSideIndex = rightSideIndex + 24
        if(rightSideIndex<0){
            rightSideIndex = 0
        }
        if(leftSideIndex>=mValueArray.size){
            leftSideIndex = mValueArray.size-1
        }

        mIndicateLabelTextPaint.apply {
            color = indicatorTextColor
            textSize = mIndicatorTimeSize
        }

        //draw "range"
        canvas?.drawText(
            rangeText,
            leftBorderLine,
            mIndicateSpace+mIndicateLabelTextPaint.textSize * (4 / 3),
            mIndicateLabelTextPaint
        )

        //draw "time"
        canvas?.drawText(
            mValueArray[rightSideIndex].time.toString().substring(0, 13) +" To "+
                    mValueArray[leftSideIndex].time.toString().substring(0, 13) ,
            leftBorderLine, mIndicateSpace+mIndicateLabelHeight - mIndicateLabelHeight / 13, mIndicateLabelTextPaint
        )

        //draw data
        mIndicateLabelTextPaint.apply {
            color = Color.parseColor("#000000")
            textSize = mIndicatorDataSize
            isFakeBoldText = true
        }

        //get max and min data
        var max = mValueArray.subList(rightSideIndex, leftSideIndex).maxOf { it.maxValue}
        var min = mValueArray.subList(rightSideIndex, leftSideIndex).minOf { it.minValue}

        var rangeData = "${min}-${max}"
        var rangeDataBottom = (mIndicateSpace + mIndicateLabelHeight + mIndicateSpace)/2+mIndicateLabelTextPaint.textSize/3
        canvas?.drawText(
            rangeData,
            leftBorderLine,rangeDataBottom,
            mIndicateLabelTextPaint
        )

        var rangeDataLength = mIndicateLabelTextPaint.measureText(rangeData)
        mIndicateLabelTextPaint.apply {
            color = indicatorTextColor
            textSize = mIndicatorBMPSize
        }

        canvas?.drawText(
            "BMP",
            (leftBorderLine + rangeDataLength * 1.1).toFloat(),
            rangeDataBottom,
            mIndicateLabelTextPaint
        )

    }

    private fun drawBackGroundAxis(canvas: Canvas?) {
        /*
        Draw Axis:
        1. 计算每个坐标轴线条之间的距离
        2. 循环SliceAmount次，从零坐标开始，绘制SliceAmount+1次
         */
        //Vertical Line
        val mVerticalUnitDistance = mChartHeight / mHorizontalLineSliceAmount
        val mVerticalLabelUnit = (mYAxisRange.second - mYAxisRange.first) / mHorizontalLineSliceAmount

        var currentLabel: Int
        val startX = (mLineWidth * 2 + mLineSpace * 2 - mLineSpace / 2)
        val endX = startX + mChartWidth
        val startY = mTotalHeight - mBottomTextHeight
        var currentY = startY

        mAxisPaint.pathEffect = null

        (0..mHorizontalLineSliceAmount).forEach { i ->
            currentLabel = (mYAxisRange.first + i * mVerticalLabelUnit).toInt()
            currentY = startY - i * mVerticalUnitDistance
            canvas?.drawLine(startX, currentY, endX, currentY, mAxisPaint)
            canvas?.drawText("$currentLabel", endX + mTextSize / 3, currentY + mTextSize / 3, mTextLabelPaint)
        }

        //Draw borderline
        canvas?.drawLine(startX, startY, startX, startY - mChartHeight, mAxisPaint)
    }

    private fun drawIndicators(canvas: Canvas?, pointIndex: Int) {
        if (pointIndex == UNCLICKED || !isShowIndicator) {
            return
        }

        val currentValue = mValueArray[pointIndex]
        val currentX = mViewStartX - (pointIndex) * (mLineWidth + mLineSpace) - chartRightMargin

        //draw indicator line
        canvas?.drawLine(
            currentX,
            mIndicateLabelHeight + mIndicateSpace,
            currentX,
            mTotalHeight - mBottomTextHeight,
            mIndicateLabelLinePaint
        )
        //draw indicator
        var leftBorder = currentX - mIndicateLabelWidth / 2
        var rightBorder = currentX + mIndicateLabelWidth / 2
        val bottom = mIndicateSpace + mIndicateLabelHeight
        val top = mIndicateSpace

        //if leftBorder < chartBorder, than leftBorder = chartBorder
        var rightChartBorder = mInitialStartX +chartRightMargin + mRightTextWidth
        var leftChartBorder = (mLineWidth * 2 + mLineSpace * 2 - mLineSpace / 2)

        if (rightBorder > rightChartBorder) {
            rightBorder = rightChartBorder
            leftBorder = rightChartBorder - mIndicateLabelWidth
        }
        if (leftBorder < leftChartBorder) {
            leftBorder = leftChartBorder
            rightBorder = leftBorder + mIndicateLabelWidth
        }

        canvas?.drawRoundRect(
            leftBorder, top, rightBorder, bottom,
            mIndicateLabelCorner, mIndicateLabelCorner, mIndicateLabelPaint
        )

        //draw text
        mIndicateLabelTextPaint.apply {
            color = indicatorTextColor
            textSize = mIndicatorTimeSize
        }


        var textLeftRange = leftBorder + mIndicateLabelHeight / 7

        //"Range"
        canvas?.drawText(
            rangeText,
            textLeftRange,
            top + mIndicateLabelTextPaint.textSize * (4 / 3),
            mIndicateLabelTextPaint
        )

        //Time
        canvas?.drawText(
            mValueArray[pointIndex].time.toString().substring(0, 13),
            textLeftRange, bottom - mIndicateLabelHeight / 13, mIndicateLabelTextPaint
        )

        //Data
        mIndicateLabelTextPaint.apply {
            color = Color.parseColor("#000000")
            textSize = mIndicatorDataSize
            isFakeBoldText = true
        }

        var rangeData = "${currentValue.minValue}-${currentValue.maxValue}"
        var rangeDataBottom = (bottom + top) / 2 + mIndicateLabelTextPaint.textSize / 3

        canvas?.drawText(
            rangeData,
            textLeftRange, rangeDataBottom,
            mIndicateLabelTextPaint
        )

        var rangeDataLength = mIndicateLabelTextPaint.measureText(rangeData)

        mIndicateLabelTextPaint.apply {
            color = indicatorTextColor
            textSize = mIndicatorBMPSize
        }

        canvas?.drawText(
            "BMP",
            (textLeftRange + rangeDataLength * 1.1).toFloat(),
            rangeDataBottom,
            mIndicateLabelTextPaint
        )


    }

    private fun drawLines(canvas: Canvas?) {
        Log.w("mViewStartX", mViewStartX.toString())
        val baseY = mIndicateLabelHeight + mChartHeight
        var startY: Float
        var endY: Float
        var currentX: Float

        var layerId = canvas?.saveLayer(
            (mLineSpace + mLineWidth) * 2,
            0F,
            (mLineSpace + mLineWidth) * 2 + mChartWidth,
            mTotalHeight,
            null
        )
        //Start Point is part of the lineWidth

        (0 until mValueArray.size).forEach { it ->
            //draw line
            if (it > drawRangeRight || it < drawRangeLeft) {
                return@forEach
            }

            currentX = mViewStartX - (it) * (mLineWidth + mLineSpace) - chartRightMargin
            startY = baseY - mChartHeight / mYAxisRange.second * mValueArray[it].maxValue
            endY = baseY - mChartHeight / mYAxisRange.second * mValueArray[it].minValue


            if (mValueArray[it].maxValue != 0) {
                canvas?.drawLine(currentX, startY, currentX, endY, mLinePaint)
            }
            //draw vertical Line
            //23, 5, 11, 17
            val mVerticalUnitDistance = mChartHeight / mHorizontalLineSliceAmount
            val dashEffect = DashPathEffect(floatArrayOf(mVerticalUnitDistance / 44, mVerticalUnitDistance / 44), 1F)
            var lineX: Float
            var currentTextLabel: String


            var thisHour = mValueArray[it].time.hours

            if ((thisHour + 1) % 6 == 0) {
                if (thisHour + 1 == 24) {
                    mAxisPaint.pathEffect = null
                    lineX = currentX + mLineSpace / 2 + mLineWidth / 2
                } else {
                    mAxisPaint.pathEffect = dashEffect
                    lineX = currentX + mLineSpace / 2 + mLineWidth / 2
                }

                (if (thisHour + 1 < 10) {
                    "0${thisHour + 1}"
                } else if (thisHour + 1 < 20) {
                    "${thisHour + 1}"
                } else if (thisHour + 1 == 24) {
                    "00"
                } else {
                    ""
                }).also { currentTextLabel = it }

                canvas?.drawLine(
                    lineX,
                    mTotalHeight - mChartHeight - mBottomTextHeight,
                    lineX,
                    mTotalHeight,
                    mAxisPaint
                )
                canvas?.drawText(currentTextLabel, lineX + mTextSize / 3, mTotalHeight - mTextSize / 3, mTextLabelPaint)
            }

        }

        canvas?.restoreToCount(layerId!!)


    }

    private fun initValueArray(){
        mValueArray.removeFirst()
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 17, 0, 0), minValue = 32, maxValue = 55))
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 18, 0, 0), minValue = 32, maxValue = 55))
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 19, 0, 0), minValue = 32, maxValue = 55))
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 20, 0, 0), minValue = 48, maxValue = 67))
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 21, 0, 0), minValue = 38, maxValue = 69))
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 22, 0, 0), minValue = 44, maxValue = 69))
        mValueArray.add(HeartRateChartEntry(time = Date(2020, 12, 30, 23, 0, 0), minValue = 66, maxValue = 88))
        repeat(50) {
            val data = HeartRateChartEntry(
                time = Date(2020, 1, it % (24), it.mod(24), 0, 0),
                minValue = it + (0..20).random(),
                maxValue = it + 5 + (20..40).random()
            )
            mValueArray.add(data)
        }
        mValueArray = mValueArray.reversed() as ArrayList<HeartRateChartEntry>

    }

    private fun initPaints() {

        drawRangeLeft = 0
        drawRangeRight = drawRangeLeft + 26

        mAxisPaint.apply {
            isAntiAlias = true
            color = mTextColor
            style = Paint.Style.STROKE
            strokeWidth = 2F
        }

        mTextLabelPaint.apply {
            isAntiAlias = true
            color = mTextColor
            textSize = mTextSize.toFloat()
            textAlign = Paint.Align.LEFT
        }

        mLinePaint.apply {
            isAntiAlias = true
            color = mLineColor
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = mLineWidth
            strokeCap = Paint.Cap.ROUND
        }

        mIndicateLabelLinePaint.apply {
            isAntiAlias = true
            color = Color.parseColor("#D3D2D8")
            style = Paint.Style.FILL_AND_STROKE
            strokeWidth = mLineWidth / 4
        }

        mIndicateLabelPaint.apply {
            isAntiAlias = true
            color = Color.parseColor("#F1F0F5")
            style = Paint.Style.FILL
        }

        mIndicateLabelTextPaint.apply {
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
            textSize = mIndicatorTimeSize
        }

    }

    fun turnOffIndicatorTimer() {
        if (hasTimer) {
            showIndicatorJob.cancel()
            hasTimer = false
        }
    }

    fun startIndicatorTimer() {
        showIndicatorJob = mScope.launch(Dispatchers.Default) {
            hasTimer = true
            delay(TOUCHMOVEDURATION + 10.toLong())
            withContext(Dispatchers.Main) {
                isScrolling = false
                isLongTouch = true
                setCurrentIndexOnClicked()
                isShowIndicator = true
                hasTimer = false
            }
        }
    }

    fun setIndicatorState(state:Boolean){
        isShowIndicator = state
    }

    fun exitChart(){
        isScrolling = false
        isLongTouch = false
        drawBackToBorder()
    }

    fun drawBackToBorder(){
        var endValue:Float = 0F

        endValue =
            //out of right borderline
        if(mViewStartX < mInitialStartX){
            mInitialStartX
            //out of left borderline
        } else if(mViewStartX > mInitialStartX + (mValueArray.size-24)*(mLineWidth+mLineSpace)){
            mInitialStartX + (mValueArray.size-24)*(mLineWidth+mLineSpace)
            //does not reach the bound, need reposition to exact place.
        } else {
            mViewStartX - (mViewStartX - mInitialStartX).mod(mLineSpace+mLineWidth)
        }

        val anim = ObjectAnimator.ofFloat(mViewStartX, endValue)
        anim.interpolator = DecelerateInterpolator()
        anim.addUpdateListener {
            mViewStartX = it.animatedValue as Float
        }
        anim.start()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        mCurrentX = event!!.x
        mCurrentY = event.y
        var topAreaHeight = mTotalHeight - mChartWithLabelHeight

        //find the current index clicked
        setCurrentIndexOnClicked()

        //by default, we do not show indicator label
        setIndicatorState(false)

        if (mCurrentY < topAreaHeight || mCurrentY > mTotalHeight - mBottomTextHeight
            || mCurrentX<leftBorderLine) {
            invalidate()
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                //control mLastX
                mLastX = mCurrentX
                currentMS = System.currentTimeMillis()
                startIndicatorTimer()

            }
            MotionEvent.ACTION_MOVE -> {
                //get move
                mMoveX = mLastX - mCurrentX
                mLastX = mCurrentX

                //if move time <150ms and moveX > 20, set indicator mode to false and move view
                if (((System.currentTimeMillis() - currentMS) < TOUCHMOVEDURATION && (abs(mMoveX) > mLineWidth)) || isScrolling) {
                    //every touch will turn off the indicator timer
                    turnOffIndicatorTimer()
                    isScrolling = true
                    mViewStartX -= mMoveX
                    updateCurrentDrawRange()}



                 else if (isLongTouch) {
                    //every touch will turn off the indicator timer
                    setIndicatorState(true)
                }
                else if(abs(mMoveX)>mLineWidth/2){
                    turnOffIndicatorTimer()
                }

            }

            MotionEvent.ACTION_UP -> {
                //every touch will turn off the indicator timer
                turnOffIndicatorTimer()

                if (!isScrolling) {
                    setIndicatorState(true)
                }
                exitChart()
            }

            MotionEvent.ACTION_CANCEL -> {
                exitChart()
            }
        }
        invalidate()
        return true
    }


    private fun setCurrentIndexOnClicked() {
        indexOnClicked =
            ((mViewStartX  - mCurrentX) / (mLineWidth + mLineSpace)).toInt()
        if (indexOnClicked !in (0 until mValueArray.size)) {
            indexOnClicked = UNCLICKED
        }
        invalidate()
    }


    fun updateCurrentDrawRange() {
        //control drawing range left and right
        drawRangeLeft = ((mViewStartX - mInitialStartX) / (mChartWidth / 24)).toInt()-4
        drawRangeRight = drawRangeLeft + 30
        Log.w("RangeLeftAndRight", "left:${drawRangeLeft},right:${drawRangeRight}")
    }


    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)

        var thisWidth = measureDimension(width.toInt(), widthMeasureSpec)
        var thisHeight = measureDimension(height.toInt(), heightMeasureSpec)
        setMeasuredDimension(thisWidth, thisHeight)

    }

    fun measureDimension(defaultSize: Int, measureSpec: Int): Int {
        var result: Int
        var specMode = MeasureSpec.getMode(measureSpec)
        var specSize = MeasureSpec.getSize(measureSpec)

        if (specMode == MeasureSpec.EXACTLY) {
            result = specSize
        } else {
            result = defaultSize
            if (specMode == MeasureSpec.AT_MOST) {
                result = min(result, specSize)
            }
        }
        return result
    }
}