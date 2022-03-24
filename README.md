# 1. 文件列表
IsenseChart.kt 自定义view类，没有设置外部参数  
HeartRateChartEntry.kt 数据类，是每一个数据条的数据来源。

# 2. 页面实现
在使用自定义View实现页面前，结合上述对布局的分析，思考一下我们的工作流程：
1. 画一个图表的框架草图，**标注出重要的尺寸，确保这些尺寸能够让我们计算出每一个点的坐标**；
2. 准备一个数据类来容纳每个时间点的数据，用ArrayList打包起来，作为我们的数据源；
3. 横向背景线、y轴刻度都是全程静态的，优先绘制它；
4. 将**纵向背景线、x轴刻度与数据条绑定起来绘制**；结合ArrayList中每一个**item的索引来计算坐标**、使用**item的数值计算数据条的y轴位置**；
5. 实现数据标注的绘制函数，它可以通过指定一个item的索引来展示出对应点的具体信息；
5. 通过**重写onTouchEvent**来实现**点击/触摸触发数据标注的效果**，实现**图表的滑动效果**。

脑子里粗略思考一遍每一步的可能难度，发现我们主要面临三个难题😥：
1. 使用怎样的布局可以让我们**轻松地通过item的索引来计算坐标**？
2. 该怎么用**最简洁优雅的方式让我们的数据条动起来**？
3. 同样是滑动，有时候用户需要数据条左右滑动，有时候却需要数据条不动，数据标注动，这该怎么区分呢？

为保证阅读体验，实现部分不会列出所有代码并阐述所有细节，代码可以在最下方Ctrl C+V获取。
## 2.1 图表的基础结构
我们按照拟定的工作流程一步步来：
### 2.1.1画一个图表的框架草图。
提前拆解思考过图表以后，我们可以快速画出以下结构图：
![image.png](https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/f0c452265eee489b9f2a1bdedc84e095~tplv-k3u1fbpfcp-watermark.image?)
对于数据条宽度(lineWidth)，及数据条间隙宽度(lineSpace)的选取，假设我们最大可视数据条为n个，为了实现规整的页面，需要保证以下等式成立： 

$\rm{(lineWidth\ +\ lineSpace)\ *\ n = chartWidth}$  

其中chartWidth我们在上方结构图中标出的——存放数据条的chart的宽度；  
这么做的原因很简单：假设现在n为24，那么这个chart的宽度就是 **24\* lineWidth +23\* lineSpace + 最左侧空白宽度 + 最右侧空白宽度**；如上等式保证了**左右侧空白宽度都为 0.5 \* lineSpace**。

### 2.1.2 准备一个数据类
目前的需求是，存放时间，一个最小值一个最大值，所以创建一个简单的DataClass即可。
```kotlin
data class HeartRateChartEntry(
        
    val time: Date = Date(), val minValue:Int = 66, val maxValue:Int = 88
)
```
然后我们创建一些随机数据，用ArrayList存储。

### 2.1.3 绘制横向背景线、y轴刻度
他们是静态的，直接用绘制出来的结构图计算chart、文本的起讫点坐标直接画就好。  
+ startX = (getWidth() - chartWidth)/2。当然，你也可以自己定义chart的起点，我建议这个起点的x坐标与lineWidth+lineSpace成正比
+ endX = startX + chartWidth
+ endY = startY = totalHeight - bottomTextHeight
我们要绘制k条线，就首先计算线之间的距离unitDistance = chartHeight/(k-1)，每次绘制让unitDistance\*i - startY就可以获取到当前横线的纵坐标了。
```kotlin
(0..mHorizontalLineSliceAmount).forEach{ i ->
    //获取当前要写上去的刻度
    currentLabel = .....
    
    //计算当前Y
    currentY = startY - i * mVerticalUnitDistance
    
    //画线
    canvas.drawLine(startX, currentY, endX, currentY, mAxisPaint)
    //画text
    canvas?.drawText("${currentLabel}", endX + mTextSize/3, currentY+mTextSize/3, mTextLabePaint)
    
//再画上最左侧的边界线
canvas.drawLine(startX, startY, startX, startY-mChartHeight, mAxisPaint)
}
```


### 2.1.4绘制数据条与纵向背景线
好，遇到了我们预料的难题，用什么方式绘制数据条，可以让他符合我们的滑动需求呢？    
  
**被否定的方案：**  
    假设我们**通过onTouchEvent计算手指滑动的距离**，用**滑动的距离来计算我们需要绘制的数据索引**；但这种方式虽然符合我们静态页面的需求，**但没法实现顺畅的动画效果**，滑动过程中只会**不停地闪烁**。  
    究其原因是他实际上没有改变数据条绘制时的横坐标，我们再去根据onTouchEvent的滑动距离来微调他们吗？但这仍然无法避免边缘数据条的闪烁。  
      
**更好的方案：窗口**  
<img src="https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/c970340df6f0436cb1fe072812749640~tplv-k3u1fbpfcp-watermark.image?" width=500/>  

想象我们正对着坐在窗口前，我们把这个窗口假设为一个viewPort，在这个窗口，**我们能够看到横向切换的风景**，是因为**窗口和背景之间的相对移动**。
  
如果我们将其设想为我们的chart和数据条，可不可以**把chart理解为窗口，数据条是浮在其表面的风景**，然后我们**只需要移动数据条，就可以切换风景(数据条滑动的视觉效果)**，这可以保证不会出现割裂感，毕竟所有东西都已经绘制了，只是位置调整了。  

想法看来可以一试，上手前，我们还是先画图理一下思路。
+ 我们需要从右往左绘制数据条以展现时间格式
+ 初始起点不如设定为chart的最右端

<img src="https://p1-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/ccc9a9b4fc9c4e64a8aa4a0412a78d7a~tplv-k3u1fbpfcp-watermark.image?" width=500/>  

+ 如果要向右滑动，是不是把绘图的起始点往右边移就可以了？
<img src="https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/547216a5553442d78f6027f737f07c05~tplv-k3u1fbpfcp-watermark.image?" width=500/>  

看来这个思路没错，我们用viewStartX作为起始点，从右向左画数据条(for循环配合数据下标计算x轴坐标)，然后去onTouchEvent的ActionMove里计算滑动的距离，动态调整viewStartX就搞定了。

不过有一点要想一想，如果我们每次都滑动都重新绘制了所有的数据条，如果数据量一大，必定会造成性能问题呀！

不过他很好解决，我们只需要计算**当前窗口展示的最左和最右的数据条索引**，分别为**leftRangeIndex, rightRangeIndex**，我们在遍历画数据条的过程中设置为**只执行(leftRangeIndex-3, rightRangeIndex+3)范围**即可，这就实现了**每次只画窗口内+窗口边缘的数据条**了。
<img src="https://p9-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/af96d02e26af487a9eeff9a7de18cc14~tplv-k3u1fbpfcp-watermark.image?" width=500/>  

最后，我们需要在绘制完数据条以后，截取一个窗口下来，放回到我们的chart里，我们可以通过``canvas.saveLayer()``和``canvas.restoreToCount()``配对使用来实现。

以下是绘制数据条的核心代码，看个思路就好

1. 用saveLayer()来确定一个窗口范围
```kotlin
val windowLayer = canvas?.saveLayer(
    left = chartLeftMargin, //chart左边界的x坐标
    top = 0F,
    right = chartRightBorner, //chart右边界的x坐标
    bottom = widthBottom //chart下边界的y坐标
)
```

2. 遍历我们存储数据的ArrayList，使用viewStartX和索引来计算每个数据条的横坐标，绘制出来
```kotlin
(0 until mValueArray.size).forEach { it ->
    //如果不在我们预期的绘制范围内，那就溜溜球，不画了
    if (it > drawRangeRight || it < drawRangeLeft) {
        return@forEach
    }
    //计算坐标x，数据条的y轴起讫点
    currentX = mViewStartX - (it) * (mLineWidth + mLineSpace) - chartRightMargin
    startY = baseY - mChartHeight / mYAxisRange.second * mValueArray[it].maxValue
    endY = baseY - mChartHeight / mYAxisRange.second * mValueArray[it].minValue

    if (mValueArray[it].maxValue != 0) {
        canvas?.drawLine(currentX, startY, currentX, endY, mLinePaint)
    }
```

3. 在我们既定的特定时间点，绘制纵向背景线和刻度（代码略了，完整版在最下方）

4. 最后，把这个窗口再存储到我们的view里去就完成了
```kotlin
cavas?.restoreToCount(windowLayer!!)
```

### 2.1.5 数据标注的绘制函数
前文有提到，我们的图表一共有两种数据标注的形式，**一是默认形态，二是指示形态**，他们是**非此即彼**的，我们只需要设置一个**boolean变量**，**isShowIndicator**，然后在onTouchEvent中动态设置这个变量，就可以实现他们的切换了。

同时，我们在onTouchEvent中维护一个**变量indexOnClicked**，它用来表示**当前被点击**的那个**数据条的索引**，并**绘制指示形态的数据标注**。

这里的绘制流程不赘述了。

## 2.2 图表的触摸事件
还是一样，理清思路再上手写代码。  
我们希望：

+ 图表能够**判定用户的长触摸、快速滑动行为**
    + 我们的图表需要能够判断以下两个状态值
        + **正在数据条滑动**状态—**isScrolling**：表示用户通过快速的手指滑动 来切换 数据条（也就是改变viewStartX的坐标）
        + **正在长触摸**状态-**isLongTouch**: 用户的手指一直停留在我们的屏幕上，这是因为他想要查看数据标注，这个状态下的切换不会切换数据条，而是切换数据标注的下标。

+ 图表能够计算**每次滑动的距离**，动态调整viewStartX与要绘制的数组左右边界
### onTouchEvent事件链
为了实现以上需求，我们需要研究一下onTouchEvent(event: MotionEvent?)

对于触摸事件，我们处理以下回调：
+ **ACTION_DOWN**
    + 手指按下：无论是点击还是滑动，ACTION_DOWN都是他们的初始动作
+ **ACTION_MOVE**
    + 手指滑动：在ACTION_DOWN触发后，如果手指滑动，MOVE就会被触发若干次，以表示手指在图表上的滑动
+ **ACTION_UP**
    + 手指抬起：一定是点击事件的结束步，可能是滑动事件的结束步(也可能是ACTION_CANCEL)
+ **ACTION_CANCEL**
    + 手势放弃：可能是滑动事件的结束步(也可能是ACTION_UP)

![image.png](https://p3-juejin.byteimg.com/tos-cn-i-k3u1fbpfcp/6e3c8dbd1cd34340a5e9b69138ccb521~tplv-k3u1fbpfcp-watermark.image?)


我们先处理该怎么让图表判断是快速滑动：
1. 我们维护一个当前时间currentTime
1. 每次ACTION_DOWN**手指按下的时候，我们就记录那一时刻的时间**；
1. 在遇到ACTION_MOVE的时候，我们就**首先获取当前时间**，**减去记录的currentTime来获取时间间隔**
1. **如果这个间隔小于某个时间阈值TIMEDURATION，我们把它认定为是一次快速滑动**
1. 但是，我们添加限制条件，这一次move的距离必须大于某个阈值，否则视为一次轻微move(手滑产生的，不是用户的内心想法)
6. 对于**后续的滑动事件来说(上图中的n号ACTION_MOVE)**，他们**时间可能已经超过了阈值**，**但他们也需要执行这个滑动任务**；还记得我们提到的**状态变量isScrolling**吗，我们在1号ACTION_MOVE中将isScrolling设置为true，后续的n号滑动事件中，只要**发现当前是isScrolling==true 是正在滑动状态**，它就**可以大胆开始执行滑动事件了**。

据上，我们有了以下代码：
```kotlin
override fun onTouchEvent(event:MotionEvent?):Boolean{
//获取当前触摸点的横坐标
mCurrentX = event!!.x

when (event.action) {
    MotionEvent.ACTION_DOWN -> {
        //记录一下触摸的点，用来记录滑动距离
        mLastX = mCurrentX
        //记录现在的时间，用来判断快速滑动
        currentMS = System.currentTimeMillis()

    }
    MotionEvent.ACTION_MOVE -> {
        //获得滑动的距离
        mMoveX = mLastX - mCurrentX
        //记录一下触摸的点
        mLastX = mCurrentX

        //如果 move time <Xms and moveX > Xpx, 这是快速滑动
        if (((System.currentTimeMillis() - currentMS) < TOUCHMOVEDURATION && (abs(mMoveX) > mLineWidth)) || isScrolling) {
            isScrolling = true
            
            //更新viewStartX，实现数据条切换，记得给mViewStartX的setter加invalidate()
            mViewStartX -= mMoveX
            
            //更新左右边界
            updateCurrentDrawRange()
        }
    }
}
```

接着，我们来处理该怎么让图表判断是长触摸-isLongTouch:
+ 怎样的事件流是长触摸呢？
    + 长触摸，就是用户的手放上去以后，没有抬起，只有轻微滑动
    + 我们将这个阈值设置为判断快速滑动的时间阈值为TIMEDURATION
    + 如果我们在执行ACTION_DOWN后，TIMEDURATION时间内，**除了轻微滑动外**，**没有任何**其他ACTION事件触发，那就认定为是长触摸
+ 用代码来实现：
    + 我们在每次ACTION_DOWN后，都**开启一个子线程**，**在TIMEDURATION后，如果他没有被取消运行，那就将isLongTouch设置为true**
    + 这样我们就开启了长触摸模式，可以在ACTION_MOVE中增加判断，配合isLongTouch来展示我们的数据标注切换。
    + 同样，我们在ACTION_UP和 ACTION_MOVE显著移动的事件中，取消这个子线程。

这里，我用kotlin协程来实现的这个判断长触摸的子线程  

开启协程的函数：
```kotlin
fun startIndicatorTimer() {
    showIndicatorJob = mScope.launch(Dispatchers.Default) {
        //用了hasTimer来辅助外面判断有没有子线程在运行
        hasTimer = true
        //延时任务进行
        delay(TOUCHMOVEDURATION + 10.toLong())
        withContext(Dispatchers.Main) {
            //长触摸了，那正在滑动状态就必须是false啦
            isScrolling = false
            //长触摸：轮到我了
            isLongTouch = true
            //找到当前被触摸的数据条索引
            setCurrentIndexOnClicked()
            //展示指示形态的数据标签
            isShowIndicator = true
            //子线程运行完毕，把标记设置为false
            hasTimer = false
        }
    }
}
```
关闭协程的函数：
```kotlin
fun turnOffIndicatorTimer() {
    if (hasTimer) {
        showIndicatorJob.cancel()
        hasTimer = false
    }
}
```

触摸事件里的核心代码

```kotlin
//节选
when(event.action){
    MotionEvent.ACTION_DOWN->{
        //记录坐标，记录时间
        mLastX = mCurrentX
        currentMS = System.currentTimeMillis()
        
        //开始子线程的任务
        startIndicatorTimer()
    }
    MotionEvent.ACTION_MOVE->{
        mMoveX = mLastX - mCurrentX
        mLastX = mCurrentX
    if(是快速滑动){
        //关闭这个长触摸判断线程
        turnOffIndicatorTimer()
    }
    //是长触摸状态，那我们激活isShowIndicator
    else if(isLongTouch){
        isShowIndicator = true
    }
    else if(不是轻微滑动){
        //关闭长触摸判断事件
        turnOffIndicatorTimer()
    }
    }
}
```

### 自动回滚
1. 我们需要每次滑动结束后去判断，让窗口内呈现完成的N个数据条
    + 基于我们的结构，这很容易实现，只需要让我们的viewStartX(绘画初始点)的坐标变为(lineWidth+lineSpace)的整数即可
 ```kotlin
 mViewStartX - (mViewStartX - mInitialStartX).mod(mLineSpace+mLineWidth)
```
2. 我们要在滑动超出边界后，让窗口自动回滚到边界值
    + 这同样同意实现，我们通过viewStartX来判断是否出界，然后让viewStartX回到设定的边界值就好了
    
但我们不能采用直接给viewStartX赋值的方法，而是通过ObjectAnimator来实现顺滑的切换，我们将这个逻辑写在方法drawBackToBorder()中，并把它添加到ACTION_CANCEL和ACTION_UP的回调中，因为只有他们俩可能是触摸事件流的结尾。

别放了给viewStartX的Setter方法添加invalidate()，否则动画不会触发。😈
```

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
```
