package com.michelvilleneuve.fieldcad

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.*
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.text.InputType
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import kotlin.math.pow
import kotlin.math.sqrt
import android.os.Parcel
import kotlin.math.max
import kotlin.math.min
import com.google.gson.Gson
import java.io.File
import java.io.Serializable
import android.graphics.PointF
import android.util.Log
import android.view.WindowManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

import android.graphics.Canvas
import android.graphics.Color

import android.view.KeyEvent
import android.view.ViewConfiguration
import android.widget.LinearLayout
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.viewpager2.widget.ViewPager2


// import com.google.mlkit.vision.common.InputImage
// import com.google.mlkit.vision.text.TextRecognition
// import com.google.mlkit.vision.text.TextRecognizer


// Log.d("TAG", "Current Drawing Mode: $currentDrawingMode")

enum class DrawingMode {
    NONE,FREEHAND, AUTO, CIRCLE, ARC, RECTANGLE, TEXT, ERASE, ERASER_EFFECT,
    GRIP_DISPLAY_MODE, GRIP_DRAG_MODE
}

enum class Handle {
    START, END, MIDDLE
}

fun Parcel.readPointF(): PointF {
    val x = readFloat()
    val y = readFloat()
    return PointF(x, y)
}

// Extension function to write PointF to a Parcel
fun Parcel.writePointF(point: PointF) {
    writeFloat(point.x)
    writeFloat(point.y)
}

interface PageChangeListener {
    fun loadNextPage()
    fun loadPreviousPage()
}

@SuppressLint("ClickableViewAccessibility")
class MyCustomDrawingView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    var viewPager: ViewPager2? = null

    private var currentInputText: String = ""
    private var currentTextSize: Float = 48f // Default text size
    private var currentTextColor: Int = Color.BLACK // Default text color

    // List to hold all drawable shapes including text elements

    private var inputBoxRect: RectF? = null
    private var inputText: String = ""
    private var isTyping: Boolean = false
  //  private var textPaint = Paint().apply {
  //      color = Color.BLACK
  //      textSize = 48f
  //  }

    init {
        // Enable key events for this view
        isFocusableInTouchMode = true
    }

    private val bitmapPaint = Paint(Paint.DITHER_FLAG)

 //   private var textRecognizer: TextRecognizer? = null



 //   private var currentDrawingMode: DrawingMode = DrawingMode.NONE
    
    private val handler = Handler(Looper.getMainLooper())

    private var path = Path()

    private val paint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }
    private val handlePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f // Default text size
        isAntiAlias = true
    }

    var textSize = 30f
        set(value) {
            field = value
            textPaint.textSize = value
        }

    private val eraserEffectPath = Path()

    private val eraserEffectPaint = Paint().apply {
        color = Color.WHITE // Color of the canvas background
        strokeWidth = 5f // Adjust this to change the size of the eraser
        style = Paint.Style.STROKE
    }


    var lines: MutableList<Line> = mutableListOf()
    var texts: MutableList<Text> = mutableListOf()
    var circles: MutableList<Circle> = mutableListOf()
    var arcs: MutableList<Arc> = mutableListOf()
    var rectangles: MutableList<Rectangle> = mutableListOf()
    val textElements = mutableListOf<TextElement>()
    private val eraserTrail = mutableListOf<PointF>()

    private val shapes = mutableListOf<Shape>()

    private var startX = 0f
    private var startY = 0f

    private var lastX = 0f
    private var lastY = 0f

    private val freehandPath = Path()
    private val freehandLines = mutableListOf<Line>()
    private val paths = mutableListOf<Path>()

    val displayMetrics = Resources.getSystem().displayMetrics
    private val canvasWidth = displayMetrics.widthPixels
    private val canvasHeight = (displayMetrics.heightPixels * 0.75).toInt()
 //   private val canvasHeight = displayMetrics.heightPixels

//    private var canvasWidth = 8.5f * 72f // Example width in points (8.5 inches * 72 points per inch)
 //   private var canvasHeight = 11f * 72f // Example height in points (11 inches * 72 points per inch)

    private val canvasWidthInPixels = canvasWidth
    private val canvasHeightInPixels = canvasHeight


    private val borderPaint = Paint().apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }
    private var scale = 1.0f
    private var currentPageNumber: Int = 1

    private var timerRunnable: Runnable? = null

    private var translationX = 0f
    private var translationY = 0f
    private val matrix = Matrix()

    private var currentX = 0f
    private var currentY = 0f

    private var selectedLine: Line? = null
    private var selectedRectangle: Rectangle? = null
    private var selectedCircle: Circle? = null
    private var selectedArc: Arc? = null
    private var selectedHandle: Handle? = null
    private var selectedText: Text? = null
    enum class Handle {
        START, END, MIDDLE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, LEFT, RIGHT, TOP, BOTTOM,
        CENTER, LMIDDLE, RMIDDLE, TLEFT, TMIDDLE, TRIGHT, LSTART, LEND
    }

    private val handleRadius = 20f

    private val gestureDetector = GestureDetector(context, GestureListener())
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var isLongPressTriggered = false
    private var isDrawingInProgress = false
    private val longPressTimeout = 2000 // 3 second
    private var areGripsDisplayed = false



    var selectedShape: Shape? = null
    private var isSnappingEnabled = false
    private var currentTextInput: Text? = null

    private var scaleFactor = 1f
    private var lastScaleFactor = 1f
    private var focusX = 0f
    private var focusY = 0f
    private val scaleDetector = ScaleGestureDetector(context, ScaleListener())

    private var isScaling = false
    private var originalDrawingMode: DrawingMode = DrawingMode.NONE
    private var currentDrawingMode: DrawingMode = DrawingMode.NONE
    private var isMultiTouch = false
    private var isDrawing: Boolean = false
    //    private val snapAngle: Double = 10.0 // Snap angle in degrees
    private val SNAP_THRESHOLD_ANGLE = Math.toRadians(10.0) // 10 degrees
    private var initialPointer1X = 0f
    private var initialPointer1Y = 0f
    private var initialPointer2X = 0f
    private var initialPointer2Y = 0f
    private var lastPointer1X = 0f
    private var lastPointer1Y = 0f
    private var lastPointer2X = 0f
    private var lastPointer2Y = 0f
    private var isHandleMode = false
    private var isEraseMode = false
    private var isSnapping = false
    private var snapPointX = 0f
    private var snapPointY = 0f
    private val snapIndicatorRadius = 10f
    private val snapIndicatorPaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
    }
    private var pageTitle: String = ""

    private val ttextPaint = Paint().apply {
        color = Color.RED
        textSize = 50f
        isAntiAlias = true
    }

    fun enterEraseMode() {
        isEraseMode = true
    }

    val snapThresholdInches = 0.5f // 1/16th of an inch

  //  val threshold = 0.5f


   val threshold = ViewConfiguration.get(context).scaledTouchSlop
    private var snapRadius = threshold

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    var textElement: TextElement? = null
    var minimumDistanceThreshold = 5f
    private var isMultiTouchGesture = false

 private val MOVE_THRESHOLD = 1f
    private var initialX = 0f
    private var initialY = 0f
    private var isDragging = false

    var isHandDrawnModeEnabled: Boolean = false

    interface Shape : Parcelable, Serializable {
        fun contains(x: Float, y: Float): Boolean
    }

    private data class SnapResult(
        val startX: Float,
        val startY: Float,
        val endX: Float,
        val endY: Float
    )

     data class Line(
         var startX: Float, var startY: Float, var endX: Float, var endY: Float,
         val startp: PointF, val endp: PointF,
         var locked: Boolean = false, var color: Int = Color.BLACK,
         var isHandDrawn: Boolean = false


     ) : Shape{
         //Parcelable, Serializable {
         override fun contains(x: Float, y: Float): Boolean {
        return x >= startX && x <= endX && y >= startY && y <= endY
    }
        constructor(parcel: Parcel) : this(
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readPointF(),
            parcel.readPointF()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(startX)
            parcel.writeFloat(startY)
            parcel.writeFloat(endX)
            parcel.writeFloat(endY)
            parcel.writePointF(startp)
            parcel.writePointF(endp)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Line> {
            override fun createFromParcel(parcel: Parcel): Line {
                return Line(parcel)
            }

            override fun newArray(size: Int): Array<Line?> {
                return arrayOfNulls(size)
            }
        }
        fun getMidPoint(): Pair<Float, Float> {
            return Pair((startX + endX) / 2, (startY + endY) / 2)
        }
    }

    private fun calculateRadii(startX: Float, startY: Float, endX: Float, endY: Float): Pair<Float, Float> {
        val radiusX = Math.abs(endX - startX) / 2
        val radiusY = Math.abs(endY - startY) / 2
        return Pair(radiusX, radiusY)
    }

    data class Text(
        var x: Float,
        var y: Float,
        val text: String,
        val textSize: Float,
        var angle: Float = 0f  // Add angle variable
    ) : Shape {


        val width: Float
        val height: Float

        init {
            val bounds = calculateTextBounds(text, textSize)
            width = bounds.width()
            height = bounds.height()
        }

        override fun contains(x: Float, y: Float): Boolean {
            return x >= this.x && x <= this.x + width && y >= this.y && y <= this.y + height
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Text) return false

            return this.text == other.text && this.x == other.x && this.y == other.y && this.angle == other.angle
        }

        override fun hashCode(): Int {
            var result = x.hashCode()
            result = 31 * result + y.hashCode()
            result = 31 * result + text.hashCode()
            result = 31 * result + textSize.hashCode()
            result = 31 * result + angle.hashCode()  // Include angle in hashCode
            return result
        }

        private fun calculateTextBounds(text: String, textSize: Float): RectF {
            val textPaint = Paint().apply {
                this.textSize = textSize
                isAntiAlias = true
            }
            val bounds = Rect()
            textPaint.getTextBounds(text, 0, text.length, bounds)
            return RectF(bounds)
        }

        // Parcelable implementation
        constructor(parcel: Parcel) : this(
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readString() ?: "",
            parcel.readFloat(),
            parcel.readFloat()  // Read the angle from the parcel
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(x)
            parcel.writeFloat(y)
            parcel.writeString(text)
            parcel.writeFloat(textSize)
            parcel.writeFloat(angle)  // Write the angle to the parcel
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Text> {
            override fun createFromParcel(parcel: Parcel): Text {
                return Text(parcel)
            }

            override fun newArray(size: Int): Array<Text?> {
                return arrayOfNulls(size)
            }
        }
    }



    data class Circle(
        var centerX: Float,
        var centerY: Float,
        var radius: Float,
        var top: PointF,
        var right: PointF,
        var bottom: PointF,
        var left: PointF,
        var locked: Boolean = false,
        var color: Int = Color.BLACK

    ) : Shape{
        //Parcelable, Serializable {
        override fun contains(x: Float, y: Float): Boolean {
            return x >= centerX && x <= centerY && y >= radius && y <= radius
        }
        constructor(parcel: Parcel) : this(
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readParcelable(PointF::class.java.classLoader)!!,
            parcel.readParcelable(PointF::class.java.classLoader)!!,
            parcel.readParcelable(PointF::class.java.classLoader)!!,
            parcel.readParcelable(PointF::class.java.classLoader)!!
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(centerX)
            parcel.writeFloat(centerY)
            parcel.writeFloat(radius)
            parcel.writeParcelable(top, flags)
            parcel.writeParcelable(right, flags)
            parcel.writeParcelable(bottom, flags)
            parcel.writeParcelable(left, flags)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Circle> {
            override fun createFromParcel(parcel: Parcel): Circle {
                return Circle(parcel)
            }

            override fun newArray(size: Int): Array<Circle?> {
                return arrayOfNulls(size)
            }
        }
    }

    data class Arc(
        val centerX: Float,
        val centerY: Float,
        val radiusX: Float,
        val radiusY: Float,
        val startAngle: Float,
        val sweepAngle: Float,
        var color: Int = Color.BLACK

    ) : Shape{
        override fun contains(x: Float, y: Float): Boolean {
            // Implement your logic for checking if a point is within the arc's bounds
            // This is a simplified example
            val dx = x - centerX
            val dy = y - centerY
            val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            return distance <= Math.max(radiusX, radiusY)
        }
        //Parcelable, Serializable {
        constructor(parcel: Parcel) : this(
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat()
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(centerX)
            parcel.writeFloat(centerY)
            parcel.writeFloat(radiusX)
            parcel.writeFloat(radiusY)
            parcel.writeFloat(startAngle)
            parcel.writeFloat(sweepAngle)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Arc> {
            override fun createFromParcel(parcel: Parcel): Arc {
                return Arc(parcel)
            }

            override fun newArray(size: Int): Array<Arc?> {
                return arrayOfNulls(size)
            }
        }
    }
    data class Rectangle(
        var startX: Float,
        var startY: Float,
        var endX: Float,
        var endY: Float,
        var left: Float = 0f,
        var top: Float = 0f,
        var right: Float = 0f,
        var bottom: Float = 0f,
        var locked: Boolean = false,
        var color: Int = Color.BLACK
    ) :  Shape{
        //Parcelable, Serializable {
        override fun contains(x: Float, y: Float): Boolean {
            return x >= startX && x <= endX && y >= startY && y <= endY
        }
        constructor(parcel: Parcel) : this(
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readByte() != 0.toByte(),
            parcel.readInt()
        )

        fun updateBounds() {
            left = minOf(startX, endX)
            top = minOf(startY, endY)
            right = maxOf(startX, endX)
            bottom = maxOf(startY, endY)
        }

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeFloat(startX)
            parcel.writeFloat(startY)
            parcel.writeFloat(endX)
            parcel.writeFloat(endY)
            parcel.writeFloat(left)
            parcel.writeFloat(top)
            parcel.writeFloat(right)
            parcel.writeFloat(bottom)
            parcel.writeByte(if (locked) 1 else 0)
            parcel.writeInt(color)
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<Rectangle> {
            override fun createFromParcel(parcel: Parcel): Rectangle {
                return Rectangle(parcel)
            }

            override fun newArray(size: Int): Array<Rectangle?> {
                return arrayOfNulls(size)
            }
        }
    }
    data class TextElement(
        val text: String,
        val x: Float,
        val y: Float,
        var angle: Float = 0f,
        val paint: Paint

    ) : AbstractShape() {

        override fun contains(x: Float, y: Float): Boolean {
            val bounds = Rect()
            paint.getTextBounds(text, 0, text.length, bounds)
            return x >= this.x && x <= this.x + bounds.width() && y >= this.y && y <= this.y + bounds.height()
        }

        fun draw(canvas: Canvas) {
            canvas.drawText(text, x, y, paint)
        }

        // Parcelable implementation
        constructor(parcel: Parcel) : this(
            parcel.readString() ?: "",
            parcel.readFloat(),
            parcel.readFloat(),
            parcel.readFloat(),
            Paint().apply {
                // Read paint properties from the parcel
                color = parcel.readInt()
                textSize = parcel.readFloat()
                isAntiAlias = parcel.readByte() != 0.toByte()
                // Read other paint properties as needed
            }
        )

        override fun writeToParcel(parcel: Parcel, flags: Int) {
            parcel.writeString(text)
            parcel.writeFloat(x)
            parcel.writeFloat(y)
            parcel.writeFloat(angle)
            parcel.writeInt(paint.color)
            parcel.writeFloat(paint.textSize)
            parcel.writeByte(if (paint.isAntiAlias) 1 else 0)
            // Write other paint properties as needed
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<TextElement> {
            override fun createFromParcel(parcel: Parcel): TextElement {
                return TextElement(parcel)
            }

            override fun newArray(size: Int): Array<TextElement?> {
                return arrayOfNulls(size)
            }
        }
    }


    abstract class AbstractShape : Shape, Parcelable {
        // Provide default implementations for Parcelable methods if needed
        override fun writeToParcel(parcel: Parcel, flags: Int) {
            // Write fields to parcel
        }

        override fun describeContents(): Int {
            return 0
        }

        companion object CREATOR : Parcelable.Creator<AbstractShape> {
            override fun createFromParcel(parcel: Parcel): AbstractShape {
                // Create an instance from the parcel
                // You might need to use a factory or other method to instantiate the correct subclass
                // Example:
                // return TextElement(parcel)
                throw UnsupportedOperationException("Not implemented yet")
            }

            override fun newArray(size: Int): Array<AbstractShape?> {
                return arrayOfNulls(size)
            }
        }
    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            //   resetSelections()
            isMultiTouchGesture = true
            selectedShape = null
            areGripsDisplayed = false
           currentDrawingMode=DrawingMode.NONE
            invalidate()
        //    currentDrawingMode = DrawingMode.NONE
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            isMultiTouchGesture = true
            //   resetSelections()
            selectedShape = null
            areGripsDisplayed = false
            currentDrawingMode=DrawingMode.NONE
            invalidate()
         //   currentDrawingMode = DrawingMode.NONE

        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            isMultiTouchGesture = true
            scaleFactor *= detector.scaleFactor
            scaleFactor = 0.1f.coerceAtLeast(scaleFactor.coerceAtMost(10.0f))
            currentDrawingMode=DrawingMode.NONE
            selectedShape = null
            areGripsDisplayed = false
            invalidate()
            return true
        }
    }


  //  fun setPageChangeListener(listener: MainActivity) {
  //      this.pageChangeListener = listener
 //   }

    private lateinit var pageChangeListener: PageChangeListener
    fun setPageChangeListener(listener: PageChangeListener) {
        this.pageChangeListener = listener
    }


   inner class GestureListener : GestureDetector.SimpleOnGestureListener() {

 

       override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
           Log.d("TAG", "single Tap")
           //    resetSelections()
           selectedShape = null
           areGripsDisplayed = false
           Log.d("TAG", "Grips display - $areGripsDisplayed")
           if (currentDrawingMode != DrawingMode.TEXT) {
               currentDrawingMode = DrawingMode.NONE
               invalidate()
           }
           return true
       }


       override fun onDoubleTap(e: MotionEvent): Boolean {
           //   resetSelections()
           selectedShape = null
           areGripsDisplayed = false
           currentDrawingMode = DrawingMode.NONE
           invalidate()
           zoomToFitPage()
           return true
       }


       // Handle long-press gesture
       override fun onLongPress(e: MotionEvent) {
           isLongPressTriggered = true
           //  areGripsDisplayed = true
           //   isDrawing = false
           //   resetSelections()
           selectedShape = null
           currentDrawingMode = DrawingMode.NONE

           isSnapping = false
           invalidate()
           handleLongPress(e)

       }
   }


    override fun onTouchEvent(event: MotionEvent): Boolean {
        Log.d("TAG", "onTouchEvent")


        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val pointerCount = event.pointerCount
        val action = event.actionMasked

        // Variables for scaledTouchSlop threshold to ignore small movements
        val touchSlop = 30f
      //  val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        var movedBeyondSlop = false

        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor
        val (snappedX, snappedY) = snapToGrid(x, y)

        if (pointerCount > 1) {
            val pointer1Index = event.findPointerIndex(0)
            val pointer2Index = event.findPointerIndex(1)

            val pointer1X = event.getX(pointer1Index)
            val pointer1Y = event.getY(pointer1Index)
            val pointer2X = event.getX(pointer2Index)
            val pointer2Y = event.getY(pointer2Index)

            when (action) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    if (pointerCount == 2) {
                        initialPointer1X = pointer1X
                        initialPointer1Y = pointer1Y
                        initialPointer2X = pointer2X
                        initialPointer2Y = pointer2Y
                        lastPointer1X = pointer1X
                        lastPointer1Y = pointer1Y
                        lastPointer2X = pointer2X
                        lastPointer2Y = pointer2Y
                    }
                }

                MotionEvent.ACTION_MOVE -> {
                    if (pointerCount == 2) {
                        val deltaX1 = pointer1X - lastPointer1X
                        val deltaY1 = pointer1Y - lastPointer1Y
                        val deltaX2 = pointer2X - lastPointer2X
                        val deltaY2 = pointer2Y - lastPointer2Y

                        val averageDeltaX = (deltaX1 + deltaX2) / 2
                        val averageDeltaY = (deltaY1 + deltaY2) / 2

                        val speedFactor = 3.0f  // Increase this factor to make scrolling faster
                        translationX += averageDeltaX * speedFactor / scaleFactor
                        translationY += averageDeltaY * speedFactor / scaleFactor

                        lastPointer1X = pointer1X
                        lastPointer1Y = pointer1Y
                        lastPointer2X = pointer2X
                        lastPointer2Y = pointer2Y

                        invalidate()
                    }
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    if (pointerCount == 2) {
                        lastPointer1X = pointer1X
                        lastPointer1Y = pointer1Y
                        lastPointer2X = pointer2X
                        lastPointer2Y = pointer2Y
                    }
                }
            }
        } else {
            // Handle single-touch events (e.g., drawing) here...
            val x = (event.x - translationX) / scaleFactor
            val y = (event.y - translationY) / scaleFactor

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {

                    Log.d("TAG", "ACTION_DOWN - $inputBoxRect")
                    isMultiTouchGesture = true
                    // If grips are visible and we're starting to draw a new shape, hide the grips
                    if (areGripsDisplayed) {
                        areGripsDisplayed = false
                        selectedShape = null // Clear the selected shape reference
                        invalidate() // Redraw canvas without grips
                    }
                    // Double touch - reset selection
                    if (event.pointerCount == 2) {
                        resetSelections()
                    }

                    if (currentDrawingMode != DrawingMode.NONE) {
                        isDrawing = true
                        startX = snappedX
                        startY = snappedY
                        currentX = snappedX
                        currentY = snappedY
                        path.moveTo(startX, startY)

                        when (currentDrawingMode) {
                            DrawingMode.ERASER_EFFECT -> eraserEffectPath.moveTo(startX, startY)
                            DrawingMode.TEXT -> addTextDirectly(startX, startY)
                            DrawingMode.FREEHAND -> {
                                freehandPath.moveTo(startX, startY)
                                freehandLines.clear()
                            }
                            else -> { /* no-op */ }
                        }
                    } else {
                        // Check for handle selection
                        if (selectedHandle != null) {
                            // If we are selecting a grip, disable snapping for precise selection
                            isSnapping = false
                            handleActionDown(event, x, y)
                            invalidate()
                        }
                    }

                    // Reset long press flag and schedule long press check
                    handler.postDelayed({
                        if (!isLongPressTriggered && !isDrawing) {
                            isLongPressTriggered = true
                            handleLongPress(event)

                            // Disable grid snapping during long press to allow selection of grips
                            isSnapping = false
                        }
                    }, longPressTimeout.toLong())
                }

                MotionEvent.ACTION_POINTER_UP -> {
                    // When a finger is lifted, we might still be in multi-touch mode
                    if (event.pointerCount > 1) {
                        isMultiTouchGesture = true
                    } else {
                        isMultiTouchGesture = false
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    // Calculate how much movement has occurred
                    val dx = Math.abs(event.x - startX)
                    val dy = Math.abs(event.y - startY)

                    // Only respond to movements that exceed the threshold (to avoid accidental jitter)
                    if (dx > touchSlop || dy > touchSlop) {
                        movedBeyondSlop = true
                    }

                    // Handle single-touch move
                    if (isDrawing && movedBeyondSlop) {
                        currentX = snappedX
                        currentY = snappedY

                        // Check for proximity to edges and adjust translation values
                        handleEdgeProximity(event.x, event.y)

                        when (currentDrawingMode) {
                            DrawingMode.FREEHAND -> {
                                path.lineTo(snappedX, snappedY)
                                freehandPath.lineTo(snappedX, snappedY)
                                freehandLines.add(Line(startX, startY, snappedX, snappedY, PointF(startX, startY), PointF(snappedX, snappedY)))
                                startX = snappedX
                                startY = snappedY
                            }

                            DrawingMode.ERASER_EFFECT -> {
                                eraserEffectPath.lineTo(x, y)
                                eraseElementsAlongPath(eraserEffectPath)
                            }

                            else -> {
                                lastX = x
                                lastY = y
                            }
                        }
                        areGripsDisplayed = false
                        invalidate()
                    }

                    if (selectedHandle != null && movedBeyondSlop) {
                        handleActionMove(event, x, y)
                        invalidate()
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    Log.d("TAG", "ACTION_UP")
                    isMultiTouchGesture = false
                    // Handle single-touch up
                    if (isDrawing) {

                        // Snap the final end point to the nearest grid or predefined snapping logic
                        val (snappedEndX, snappedEndY) = snapToGrid(currentX, currentY)
                        currentX = snappedEndX
                        currentY = snappedEndY

                        when (currentDrawingMode) {
                            DrawingMode.AUTO -> {
                                // Snap the start and end points to a horizontal/vertical orientation
                                val (snappedX, snappedY) = snapToHorizontalOrVertical(startX, startY, currentX, currentY)
                                lines.add(Line(startX, startY, snappedX, snappedY, PointF(startX, startY), PointF(snappedX, snappedY)))
                            }

                            DrawingMode.RECTANGLE -> {
                                // Snap all rectangle corners to the grid
                                val left = min(startX, currentX)
                                val top = min(startY, currentY)
                                val right = max(startX, currentX)
                                val bottom = max(startY, currentY)
                                rectangles.add(Rectangle(startX, startY, currentX, currentY, left, top, right, bottom))
                            }

                            DrawingMode.CIRCLE -> {
                                // Snap radius to the grid if needed
                                val radius = distance(startX, startY, currentX, currentY)
                                val snappedRadius = snapToGrid(radius, radius).first // snapping radius to grid
                                circles.add(Circle(startX, startY, snappedRadius, PointF(), PointF(), PointF(), PointF()))
                            }

                            DrawingMode.ARC -> {
                                // Snap arc radii and center
                                val (radiusX, radiusY) = calculateRadii(startX, startY, currentX, currentY)
                                val (snappedCenterX, snappedCenterY) = snapToGrid((startX + currentX) / 2, (startY + currentY) / 2)
                                val startAngle = 0f
                                val sweepAngle = 180f // Example sweep angle
                                arcs.add(Arc(snappedCenterX, snappedCenterY, radiusX, radiusY, startAngle, sweepAngle))
                            }

                            DrawingMode.FREEHAND -> {
                                path.lineTo(currentX, currentY)
                                freehandPath.lineTo(currentX, currentY)
                                freehandLines.add(Line(startX, startY, currentX, currentY, PointF(startX, startY), PointF(currentX, currentY)))
                                lines.addAll(freehandLines)
                            }

                            DrawingMode.ERASER_EFFECT -> {
                                // Reset eraser effect path
                                eraserEffectPath.reset()
                            }

                            else -> {
                                // End drawing

                                currentDrawingMode = DrawingMode.NONE
                            }
                        }

                        // Reset the drawing path after the action completes
                        path.reset()
                 //       isDrawing = false

                        invalidate() // Refresh the canvas
                    }

                    // Handle handle (grip) selection release
                    if (selectedHandle != null) {
                        handleActionUp(event)
                        invalidate()
                    }
                }
            }
        }
        return true
    }




    private fun resetDrawingState() {
        isDrawing = false
        currentDrawingMode = DrawingMode.NONE
        selectedHandle = null
        selectedText = null
        startX = 0f
        startY = 0f
        lastX = 0f
        lastY = 0f
        invalidate()
    }

   // private fun addText(x: Float, y: Float, text: String) {
   //     textElements.add(TextElement(x, y, text))
   //     invalidate()
  //  }

 //   private fun calculateAngle(centerX: Float, centerY: Float, pointX: Float, pointY: Float): Float {
 //       val angle = Math.toDegrees(Math.atan2((pointY - centerY).toDouble(), (pointX - centerX).toDouble())).toFloat()
 //       return if (angle < 0) angle + 360 else angle
 //   }

    private fun handleEdgeProximity(x: Float, y: Float) {
        val edgeThreshold = 50  // The distance from the edge to trigger scrolling
        val speedFactor = 2.0f  // Increase this factor to make scrolling faster

        if (x < edgeThreshold) {
            translationX += (edgeThreshold - x) * speedFactor / scaleFactor
        } else if (x > width - edgeThreshold) {
            translationX -= (x - (width - edgeThreshold)) * speedFactor / scaleFactor
        }

        if (y < edgeThreshold) {
            translationY += (edgeThreshold - y) * speedFactor / scaleFactor
        } else if (y > height - edgeThreshold) {
            translationY -= (y - (height - edgeThreshold)) * speedFactor / scaleFactor
        }
    }

    private fun checkForHandleSelection2(x: Float, y: Float) {

        for (line in lines) {
            if (isCloseToHandle(line.startX, line.startY, x, y)) {
                selectedLine = line
                selectedHandle = Handle.START
                break
            } else if (isCloseToHandle(line.endX, line.endY, x, y)) {
                selectedLine = line
                selectedHandle = Handle.END
                break
            } else {
                val (midX, midY) = line.getMidPoint()
                if (isCloseToHandle(midX, midY, x, y)) {
                    selectedLine = line
                    selectedHandle = Handle.MIDDLE
                    break
                }
            }
        }

        for (rectangle in rectangles) {
            if (isCloseToHandle(rectangle.startX, rectangle.startY, x, y)) {
                selectedRectangle = rectangle
                selectedHandle = Handle.START
                break
            } else if (isCloseToHandle(rectangle.endX, rectangle.endY, x, y)) {
                selectedRectangle = rectangle
                selectedHandle = Handle.END
                break
            } else {
                val midX = (rectangle.startX + rectangle.endX) / 2
                val midY = (rectangle.startY + rectangle.endY) / 2
                if (isCloseToHandle(midX, midY, x, y)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.MIDDLE
                    break
                }
            }
        }

        for (circle in circles) {
            val handleX = circle.centerX + circle.radius
            if (isCloseToHandle(handleX, circle.centerY, x, y)) {
                selectedCircle = circle
                selectedHandle = Handle.END
                break
            } else if (isCloseToHandle(circle.centerX, circle.centerY, x, y)) {
                selectedCircle = circle
                selectedHandle = Handle.START
                break
            }
        }

        selectedText = selectText(x, y)
        resetSelections()

    }

    private fun handleMoveSelection2(x: Float, y: Float) {
        selectedLine?.let {
            when (selectedHandle) {
                Handle.START -> {
                    it.startX = x
                    it.startY = y
                }
                Handle.END -> {
                    it.endX = x
                    it.endY = y
                }
                Handle.MIDDLE -> {
                    val dx = x - (it.startX + it.endX) / 2
                    val dy = y - (it.startY + it.endY) / 2
                    it.startX += dx
                    it.startY += dy
                    it.endX += dx
                    it.endY += dy
                }
                else -> { /* no-op */ }
            }
        }

        selectedRectangle?.let {
            when (selectedHandle) {
                Handle.START -> {
                    it.startX = x
                    it.startY = y
                }
                Handle.END -> {
                    it.endX = x
                    it.endY = y
                }
                Handle.MIDDLE -> {
                    val dx = x - (it.startX + it.endX) / 2
                    val dy = y - (it.startY + it.endY) / 2
                    it.startX += dx
                    it.startY += dy
                    it.endX += dx
                    it.endY += dy
                }
                else -> { /* no-op */ }
            }
        }

        selectedCircle?.let {
            when (selectedHandle) {
                Handle.START -> {
                    it.centerX = x
                    it.centerY = y
                }
                Handle.END -> {
                    val dx = x - it.centerX
                    it.radius = Math.abs(dx)
                }
                else -> { /* no-op */ }
            }
        }
        selectedText?.let {
            Handle.LEFT
            it.x = x
            it.y = y
            invalidate()
        }
    }

    fun resetSelections() {
        selectedLine = null
        selectedRectangle = null
        selectedCircle = null
        selectedArc = null
        selectedHandle = null
        selectedText = null
        isLongPressTriggered = false
        currentDrawingMode = DrawingMode.NONE
        selectedShape = null
    }


    private fun handleLongPress2(e: MotionEvent) {
        Log.d("TAG", "Long Press")
        val x = (e.x - translationX) / scaleFactor
        val y = (e.y - translationY) / scaleFactor

        // Clear previous selection
        selectedShape = null
        selectedLine = null
        selectedRectangle = null
        selectedCircle = null
        selectedText = null
        selectedHandle = null
        areGripsDisplayed = false
        invalidate()

        var shapeSelected = false

        // Check if the long press is near a shape (line, rectangle, circle, text)
        for (line in lines) {
            if (isPointNearLine(x, y, line)) {
                selectedLine = line
                selectedShape = line
                shapeSelected = true
                break
            }
        }

        if (!shapeSelected) {
            for (rectangle in rectangles) {
                if (isPointNearRectangle(x, y, rectangle)) {
                    selectedRectangle = rectangle
                    selectedShape = rectangle
                    shapeSelected = true
                    break
                }
            }
        }

        if (!shapeSelected) {
            for (circle in circles) {
                if (isPointNearCircle(x, y, circle)) {
                    selectedCircle = circle
                    selectedShape = circle
                    shapeSelected = true
                    break
                }
            }
        }

        if (!shapeSelected) {
            for (text in texts) {
                val textElement = isPointNearText(x, y)
                if (textElement != null) {
                    selectedText = textElement
                    selectedShape = textElement
                    shapeSelected = true
                    break
                }
            }
        }

        if (shapeSelected) {
            // Grips will be shown but no specific grip is selected yet
            areGripsDisplayed = true
            selectedHandle = null  // No grip selected on long press
            isHandleMode = true
            isDragging = false  // Reset dragging state during long press
            invalidate()
        } else {
            resetSelections()  // Clear all selections if no shape was found
        }
    }
    fun onLongPress(e: MotionEvent) {
        if (areGripsDisplayed && selectedShape != null) {
            val x = (e.x - translationX) / scaleFactor
            val y = (e.y - translationY) / scaleFactor

            // Check if a grip is selected for dragging
            selectedHandle = getGripAtPosition(x, y, selectedShape!!)
            if (selectedHandle != null) {
                isDragging = true; // Set dragging mode
                invalidate(); // Redraw the canvas
            } else {
                // Optionally, reset grip display if nothing is selected
                resetSelections();
            }
        }
    }

    private fun handleLongPress(e: MotionEvent) {
        Log.d("TAG", "Long Press")
        val x = (e.x - translationX) / scaleFactor
        val y = (e.y - translationY) / scaleFactor

        isSnapping = false // Disable snapping during long press to allow precise selection

        var handleSelected = false

        // Check if a handle of a line, rectangle, or circle is selected
        for (line in lines) {
            selectedShape = line
            if (isPointNearHandle(x, y, line.startX, line.startY)) {
                Log.d("TAG", "Long Press the start")
                selectedLine = line
                selectedHandle = Handle.LSTART
                handleSelected = true
                break
            } else if (isPointNearHandle(x, y, line.endX, line.endY)) {
                Log.d("TAG", "Long Press the end")
                selectedLine = line
                selectedHandle = Handle.LEND
                handleSelected = true
                break
            } else if (isPointNearHandle(x, y, (line.startX + line.endX) / 2, (line.startY + line.endY) / 2)) {
                Log.d("TAG", "Long Press the middle")
                selectedLine = line
                selectedHandle = Handle.LMIDDLE
                handleSelected = true
                break
            } else if (isPointNearLine(x, y, line)) {
                Log.d("TAG", "Long Press the line")
                selectedLine = line
                selectedHandle = Handle.LMIDDLE
                handleSelected = true
                break
            }
        }

        if (!handleSelected) {
            for (rectangle in rectangles) {
                selectedShape = rectangle
                if (isPointNearHandle(x, y, rectangle.startX, rectangle.startY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.TOP_LEFT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, rectangle.endX, rectangle.startY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.TOP_RIGHT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, rectangle.startX, rectangle.endY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.BOTTOM_LEFT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, rectangle.endX, rectangle.endY)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.BOTTOM_RIGHT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, (rectangle.startX + rectangle.endX) / 2, (rectangle.startY + rectangle.endY) / 2)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.RMIDDLE
                    handleSelected = true
                    break
                }else if (isPointNearRectangle(x, y, rectangle)) {
                    selectedRectangle = rectangle
                    selectedHandle = Handle.RMIDDLE // or a different handle for sides
                    handleSelected = true
                    break

                }
            }
        }

        if (!handleSelected) {
            for (circle in circles) {
                selectedShape = circle
                if (isPointNearHandle(x, y, circle.centerX, circle.centerY)) {
                    selectedCircle = circle
                    selectedHandle = Handle.MIDDLE
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX, circle.centerY - circle.radius)) {
                    selectedCircle = circle
                    selectedHandle = Handle.TOP
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX + circle.radius, circle.centerY)) {
                    selectedCircle = circle
                    selectedHandle = Handle.RIGHT
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX, circle.centerY + circle.radius)) {
                    selectedCircle = circle
                    selectedHandle = Handle.BOTTOM
                    handleSelected = true
                    break
                } else if (isPointNearHandle(x, y, circle.centerX - circle.radius, circle.centerY)) {
                    selectedCircle = circle
                    selectedHandle = Handle.LEFT
                    handleSelected = true
                    break
                }
            }
        }

        if (!handleSelected) {
            for (text in texts) {
                val textElement = isPointNearText(x, y)
                if (textElement != null) {
                    selectedText = textElement
                    selectedShape = textElement  // Set selectedShape to the selected text

                    // Determine if touch is near the start, middle, or end of the text
                    val textEndX = textElement.x + textPaint.measureText(textElement.text)
                    val textMiddleX = textElement.x + (textEndX - textElement.x) / 2

                    when {
                        isPointNearHandle(x, y, textElement.x, textElement.y) -> {
                            selectedHandle = Handle.TLEFT  // Start of the text
                            selectedText = textElement
                            handleSelected = true
                            break
                        }
                        isPointNearHandle(x, y, textMiddleX, textElement.y) -> {
                            selectedHandle = Handle.TMIDDLE  // Middle of the text
                            selectedText = textElement
                            handleSelected = true
                            break
                        }
                        isPointNearHandle(x, y, textEndX, textElement.y) -> {
                            selectedHandle = Handle.TRIGHT  // End of the text
                            selectedText = textElement
                            handleSelected = true
                            break
                        }
                    }
                }
            }
        }

        if (handleSelected) {
            isHandleMode = true
            areGripsDisplayed = true
            isDragging = false  // Reset dragging state during long press

            invalidate()  // Redraw the canvas with grips

        } else {
            // No handle or shape was selected, reset selections
            resetSelections()
        }
    }


    private fun isPointNearLine(x: Float, y: Float, line: Line): Boolean {
        val lthreshold = 10f
        val dx = line.endX - line.startX
        val dy = line.endY - line.startY
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared == 0f) return isPointNearHandle(x, y, line.startX, line.startY)
        val t = ((x - line.startX) * dx + (y - line.startY) * dy) / lengthSquared
        if (t < 0f) return false
        if (t > 1f) return false
        val nearX = line.startX + t * dx
        val nearY = line.startY + t * dy
        return isPointNearHandle(x, y, nearX, nearY)
    }

    private fun isPointNearCircle(x: Float, y: Float, circle: Circle): Boolean {
        val dx = x - circle.centerX
        val dy = y - circle.centerY
        val distanceToCenter = sqrt(dx * dx + dy * dy)

        // Check if point is near the center handle
        if (distanceToCenter <= threshold) return true

        // Check if point is near the top handle
        val topHandleDistance = sqrt((x - circle.centerX) * (x - circle.centerX) + (y - (circle.centerY - circle.radius)) * (y - (circle.centerY - circle.radius)))
        if (topHandleDistance <= threshold) return true

        // Check if point is near the right handle
        val rightHandleDistance = sqrt((x - (circle.centerX + circle.radius)) * (x - (circle.centerX + circle.radius)) + (y - circle.centerY) * (y - circle.centerY))
        if (rightHandleDistance <= threshold) return true

        // Check if point is near the bottom handle
        val bottomHandleDistance = sqrt((x - circle.centerX) * (x - circle.centerX) + (y - (circle.centerY + circle.radius)) * (y - (circle.centerY + circle.radius)))
        if (bottomHandleDistance <= threshold) return true

        // Check if point is near the left handle
        val leftHandleDistance = sqrt((x - (circle.centerX - circle.radius)) * (x - (circle.centerX - circle.radius)) + (y - circle.centerY) * (y - circle.centerY))
        if (leftHandleDistance <= threshold) return true

        // Check if point is near the circle's edge
        return abs(distanceToCenter - circle.radius) <= threshold
    }


    private fun isPointNearText(x: Float, y: Float): Text? {
        val touchPadding = 20 // Adjust this value as needed for easier selection
        return texts.find { text ->
            val textBounds = RectF(
                (text.x - threshold).toFloat(),
                (text.y - threshold).toFloat(),
                (text.x + text.width + threshold).toFloat(),
                (text.y + text.height + threshold).toFloat()
            )
            textBounds.contains(x, y)
        }
    }

    private fun isPointNearHandle(x: Float, y: Float, handleX: Float, handleY: Float): Boolean {
        val dx = x - handleX
        val dy = y - handleY

        // Use screen DPI to scale the threshold appropriately for different screens.
        val adaptiveThreshold = threshold * scaleFactor
        return dx * dx + dy * dy <= adaptiveThreshold * adaptiveThreshold
    }



    private fun findLineAtPoint(x: Float, y: Float): Line? {
        for (line in lines) {
            if (isCloseToHandle(line.startX, line.startY, x, y) || isCloseToHandle(line.endX, line.endY, x, y)) {
                return line
            }
        }
        return null
    }

    private fun findRectangleAtPoint(x: Float, y: Float): Rectangle? {
        for (rectangle in rectangles) {
            if (isCloseToHandle(rectangle.startX, rectangle.startY, x, y) || isCloseToHandle(rectangle.endX, rectangle.endY, x, y)) {
                return rectangle
            }
        }
        return null
    }

    private fun findCircleAtPoint(x: Float, y: Float): Circle? {
        for (circle in circles) {
            if (isCloseToHandle(circle.centerX, circle.centerY, x, y)) {
                return circle
            }
        }
        return null
    }

    private fun findTextAtPoint(x: Float, y: Float): Text? {
        for (text in texts) {
            if (isCloseToHandle(text.x, text.y, x, y)) {
                return text
            }
        }
        return null
    }

    private fun isCloseToHandle(handleX: Float, handleY: Float, x: Float, y: Float): Boolean {
        val handleRadius = 20f
        return (Math.abs(handleX - x) <= handleRadius && Math.abs(handleY - y) <= handleRadius)

    }




    private fun addShape(shape: Shape) {
        when (shape) {
            is Line -> lines.add(shape)
            is Rectangle -> rectangles.add(shape)
            is Circle -> circles.add(shape)
            is Text -> texts.add(shape)
            is TextElement -> textElements.add(shape)
            // Add other shape types as needed
        }
    }


    private fun handleActionMove2(event: MotionEvent, x: Float, y: Float) {
        val (snappedX, snappedY) = snapToGrid((event.x - translationX) / scaleFactor,
            (event.y- translationY) / scaleFactor)

        if (!isMultiTouch && currentDrawingMode != DrawingMode.NONE && selectedHandle == null && selectedText == null) {
            if (isDrawing) {
                currentX = snappedX
                currentY = snappedY

                handleEdgeProximity(event.x, event.y)

                when (currentDrawingMode) {
                    DrawingMode.FREEHAND -> {
                        freehandPath.lineTo(snappedX, snappedY)
                        freehandLines.add(Line(startX, startY, snappedX, snappedY, PointF(startX, startY), PointF(snappedX, snappedY)))
                        startX = snappedX
                        startY = snappedY
                    }
                    DrawingMode.ERASER_EFFECT -> {
                        eraserEffectPath.lineTo(snappedX, snappedY)
                        eraseElementsAlongPath(eraserEffectPath)
                        eraserTrail.add(PointF(snappedX, snappedY))
                    }
                    else -> {
                        lastX = snappedX
                        lastY = snappedY
                    }
                }

                invalidate()
            }

        }

        if (selectedHandle != null) {

            when {
                selectedLine != null -> handleLineMove(snappedX, snappedY)
                selectedRectangle != null -> handleRectangleMove(snappedX, snappedY)
                selectedCircle != null -> handleCircleMove(snappedX, snappedY)
            }

            invalidate()
            Log.d("TAG","handleActionMove after handleLineMove X $snappedX - Y $snappedY")
        }

        selectedText?.let {
            it.x = snappedX
            it.y = snappedY
            invalidate()
        }
    }


    // Separate functions for each shape type
    private fun handleLineMove(snappedX: Float, snappedY: Float) {
        selectedLine?.let { line ->
            when (selectedHandle) {
                Handle.START -> {
                    val (newX, newY) = snapToHorizontalOrVertical(snappedX, snappedY, line.endX, line.endY)
                   line.startX = newX
                    line.startY = newY
                }
                Handle.END -> {
                    val (newX, newY) = snapToHorizontalOrVertical(line.startX, line.startY, snappedX, snappedY)
                    line.endX = newX
                    line.endY = newY
                }
                Handle.MIDDLE -> {
                    val dx = snappedX - (line.startX + line.endX) / 2
                    val dy = snappedY - (line.startY + line.endY) / 2
                    line.startX += dx
                    line.startY += dy
                    line.endX += dx
                    line.endY += dy
                }
                else -> {}
            }
            invalidate()
        }
    }

    private fun handleRectangleMove(snappedX: Float, snappedY: Float) {
        selectedRectangle?.let { rectangle ->when (selectedHandle) {
            Handle.TOP_LEFT -> {
                rectangle.startX = snappedX
                rectangle.startY = snappedY
            }
            Handle.TOP_RIGHT -> {
                rectangle.endX = snappedX
                rectangle.startY = snappedY
            }Handle.BOTTOM_LEFT -> {
                rectangle.startX = snappedX
                rectangle.endY = snappedY
            }
            Handle.BOTTOM_RIGHT -> {
                rectangle.endX = snappedX
                rectangle.endY = snappedY
            }
            Handle.MIDDLE -> {
                val dx = snappedX - (rectangle.startX + rectangle.endX) / 2
                val dy = snappedY - (rectangle.startY + rectangle.endY) / 2
                rectangle.startX += dx
                rectangle.startY += dy
                rectangle.endX +=dx
                rectangle.endY += dy
            }
            else -> {}
        }
        }
    }

    private fun handleCircleMove(snappedX: Float, snappedY: Float) {
        selectedCircle?.let { circle ->
            when (selectedHandle) {
                Handle.TOP -> {
                    circle.radius = Math.abs(circle.centerY - snappedY)
                }
                Handle.RIGHT -> {
                    circle.radius = Math.abs(circle.centerX - snappedX)
                }
                Handle.BOTTOM -> {
                    circle.radius = Math.abs(circle.centerY - snappedY)
                }
                Handle.LEFT -> {
                    circle.radius = Math.abs(circle.centerX - snappedX)
                }
                Handle.MIDDLE -> {
                    circle.centerX = snappedX
                    circle.centerY = snappedY
                }
                else -> {}
            }
        }
    }

    @SuppressLint("SuspiciousIndentation")
    private fun handleActionDown(event: MotionEvent, x: Float, y: Float) {
        if (isHandleMode) {resetSelections()
        }

        isDrawing = true



        val (snappedX, snappedY) = snapToGrid(x, y)
        startX = snappedX
        startY = snappedY
        lastX = snappedX
        lastY = snappedY


        // Handle shape creation logic here (only if no handle is selected)
        when (currentDrawingMode) {
            DrawingMode.AUTO -> addShape(Line(startX, startY, startX, startY, PointF(startX, startY), PointF(startX, startY)))
            DrawingMode.RECTANGLE -> addShape(Rectangle(startX, startY, startX, startY, startX, startY, startX, startY))
            DrawingMode.FREEHAND -> {
                path.moveTo(snappedX, snappedY)
                freehandPath.moveTo(snappedX, snappedY)
            }
            DrawingMode.ERASER_EFFECT -> eraserEffectPath.moveTo(snappedX, snappedY)
            DrawingMode.TEXT -> {
                val textElement = isPointNearText(snappedX, snappedY)
                if (textElement != null) {
                    selectedText = textElement
                }
            }
            DrawingMode.CIRCLE -> {
                val circle = Circle(snappedX, snappedY, 0f, PointF(snappedX, snappedY), PointF(snappedX, snappedY), PointF(snappedX, snappedY), PointF(snappedX, snappedY))
                addShape(circle)
                selectedCircle = circle
            }
            else -> {}
        }

    }

    private fun handleActionMove(event: MotionEvent, x: Float, y: Float) {

        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor
        val (snappedX, snappedY) = snapToGrid(x, y)


        // Only start moving the handle if the movement exceeds the threshold
        if (!isDragging && selectedHandle != null) {
            // Calculate the movement distance
            val distance = Math.sqrt(((snappedX - initialX).pow(2) + (snappedY - initialY).pow(2)).toDouble())
            if (distance > MOVE_THRESHOLD) {
                isDragging = true
            }
        }

        if (isDragging && selectedHandle != null) {
            selectedLine?.let {
                when (selectedHandle) {
                    Handle.LSTART -> {
                        val (newX, newY) = snapToHorizontalOrVertical(snappedX, snappedY, it.endX, it.endY)
                        it.startX = newX
                        it.startY = newY
                    }
                    Handle.LEND -> {
                        val (newX, newY) = snapToHorizontalOrVertical(it.startX, it.startY, snappedX, snappedY)
                        it.endX = newX
                        it.endY = newY
                    }
                    Handle.LMIDDLE -> {
                        val dx = snappedX - (it.startX + it.endX) / 2
                        val dy = snappedY - (it.startY + it.endY) / 2
                        it.startX += dx
                        it.startY += dy
                        it.endX += dx
                        it.endY += dy
                    }
                    else -> {}
                }
                invalidate()
            }

            selectedRectangle?.let {
                when (selectedHandle) {
                    Handle.TOP_LEFT -> {
                        it.startX = snappedX
                        it.startY = snappedY
                    }
                    Handle.TOP_RIGHT -> {
                        it.endX = snappedX
                        it.startY = snappedY
                    }
                    Handle.BOTTOM_LEFT -> {
                        it.startX = snappedX
                        it.endY = snappedY
                    }
                    Handle.BOTTOM_RIGHT -> {
                        it.endX = snappedX
                        it.endY = snappedY
                    }
                    Handle.RMIDDLE -> {
                        val dx = snappedX - (it.startX + it.endX) / 2
                        val dy = snappedY - (it.startY + it.endY) / 2
                        it.startX += dx
                        it.startY += dy
                        it.endX += dx
                        it.endY += dy
                    }
                    else -> {}
                }
                invalidate()
            }

            selectedCircle?.let {
                when (selectedHandle) {
                    Handle.TOP -> {
                        it.radius = Math.abs(it.centerY - snappedY)
                    }
                    Handle.RIGHT -> {
                        it.radius = Math.abs(it.centerX - snappedX)
                    }
                    Handle.BOTTOM -> {
                        it.radius = Math.abs(it.centerY - snappedY)
                    }
                    Handle.LEFT -> {
                        it.radius = Math.abs(it.centerX - snappedX)
                    }
                    Handle.MIDDLE -> {
                        it.centerX = snappedX
                        it.centerY = snappedY
                    }
                    else -> {}
                }
                invalidate()
            }
        }

        // Only move the text if dragging has started
        selectedText?.let {
            if (isDragging) {
                when (selectedHandle) {
                    Handle.TLEFT -> {
                        // Drag the start (left) of the text
                        it.x = snappedX
                        it.y = snappedY
                    }
                    Handle.TMIDDLE -> {
                        // Drag the middle of the text (move the whole text)
                        val textWidth = textPaint.measureText(it.text)
                        it.x = snappedX - textWidth / 2  // Center the text
                        it.y = snappedY
                    }
                    Handle.TRIGHT -> {
                        // Drag the end (right) of the text
                        val textWidth = textPaint.measureText(it.text)
                        it.x = snappedX - textWidth  // Adjust so that the end is dragged
                        it.y = snappedY
                    }

                    else -> {}
                }
                invalidate()  // Refresh the canvas
            }
        }


        // Reset dragging state if no handle is selected
        if (selectedHandle == null) {
            resetSelections()
            isDragging = false
        }
    }


    private fun handleActionUp(event: MotionEvent) {
        Log.d("TAG", "handleActionUp")
        val x = (event.x - translationX) / scaleFactor
        val y = (event.y - translationY) / scaleFactor

        val (snappedX, snappedY) = snapToGrid(x, y) // Snap to grid here


        startX = snappedX
        startY = snappedY
        lastX = snappedX
        lastY = snappedY

        if (isDrawing) {
            isDrawing = false



            when (currentDrawingMode) {
                DrawingMode.AUTO -> {
                    val line = lines.last()
                    line.endX = snappedX
                    line.endY = snappedY
                }
                DrawingMode.RECTANGLE -> {
                    val rectangle = rectangles.last()
                    rectangle.endX = snappedX
                    rectangle.endY = snappedY
                    rectangle.updateBounds()
                }
                DrawingMode.FREEHAND -> {
                    freehandPath.lineTo(snappedX, snappedY)
                    path.lineTo(snappedX, snappedY)
                }
                DrawingMode.ERASER_EFFECT -> {
                    // Finalize erasing effect if necessary
                }
                DrawingMode.CIRCLE -> {
                    val circle = circles.last()
                    val radius = distance(circle.centerX, circle.centerY, snappedX, snappedY)
                    circle.radius = radius
                }
                else -> {}
            }

            invalidate()
            return
        }

        if (selectedHandle != null) {
            selectedHandle = null
        }

        // resetSelections()
    }

    private fun findHandleAt(x: Float, y: Float): Handle? {
        // Implement logic to find a handle at the given coordinates

        return null
    }

    @SuppressLint("DrawAllocation")
    override fun onDraw(canvas: Canvas) {

        Log.d("TAG", "onDraw")
        super.onDraw(canvas)
        canvas.save()
        canvas.translate(translationX, translationY)
        canvas.scale(scaleFactor, scaleFactor)

        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }

        canvas.drawRect(0f, 0f, canvasWidth.toFloat(), canvasHeight.toFloat(), borderPaint)

        // Draw your grid and other elements here
        drawGraphLines(canvas)

        // Draw the shapes and elastic effect
        canvas.save()
        canvas.drawPath(path, paint)

        // Define your grid size
       val gridSize = 10f

        // Draw the title at the top of the canvas
        if (pageTitle.isNotEmpty()) {
            canvas.drawText(pageTitle, 20f, 60f, ttextPaint)
        }

        Log.d("TAG", "in draw selected shape = $selectedShape")
// Draw lines with snapping
        lines.forEach { line ->
            // Snap the start and end points to the nearest line endpoint
            val (snappedStartX, snappedStartY) = getNearestSnapPoint(line.startX, line.startY)
            val (snappedEndX, snappedEndY) = getNearestSnapPoint(line.endX, line.endY)

            // Snap the line to horizontal or vertical orientations if close to those angles
            val (finalStartX, finalStartY, finalEndX, finalEndY) = snapLineToHorizontalOrVertical(
                snappedStartX, snappedStartY, snappedEndX, snappedEndY
            )

            val linePath = Path().apply {
                moveTo(line.startX, line.startY)
                lineTo(line.endX, line.endY)
            }

            if (isHandDrawnModeEnabled) {
                drawShakyPath(canvas, linePath, paint)
             //   drawHandDrawnLine(canvas, line, paint)
            } else {
                canvas.drawLine(line.startX, line.startY, line.endX, line.endY, paint)
            }
            // Draw the line with the snapped coordinates
            //canvas.drawLine(finalStartX, finalStartY, finalEndX, finalEndY, paint)

            // Draw handles if the shape is selected
            if (line == selectedShape) {
                drawHandle(canvas, finalStartX, finalStartY)
                drawHandle(canvas, finalEndX, finalEndY)
                val (midX, midY) = snapToGrid(line.getMidPoint().first, line.getMidPoint().second, gridSize)
                drawHandle(canvas, midX, midY)
            }
        }


        texts.forEach {
            val (snappedStartX, snappedStartY) = getNearestSnapPoint(it.x, it.y)

            // Draw the text
            canvas.drawText(it.text, snappedStartX, snappedStartY, textPaint)

            if (it == selectedShape) {
                // Start point (left) grip
                drawHandle(canvas, it.x, it.y)

                // Middle grip
                val textMiddleX = it.x + textPaint.measureText(it.text) / 2
                drawHandle(canvas, textMiddleX, it.y)

                // End point (right) grip
                val textEndX = it.x + textPaint.measureText(it.text)
                drawHandle(canvas, textEndX, it.y)
            }
        }


        // Draw circles with snapping
        circles.forEach {
            val (snappedCenterX, snappedCenterY) = getNearestSnapPoint(it.centerX, it.centerY)

            if (isHandDrawnModeEnabled) {
                drawHandDrawnCircle(canvas, snappedCenterX, snappedCenterY, it.radius, paint)
            } else {
                canvas.drawCircle(snappedCenterX, snappedCenterY, it.radius, paint)
            }


            if (it == selectedShape) {
                drawHandle(canvas, snappedCenterX, snappedCenterY)
                drawHandle(canvas, snappedCenterX, snappedCenterY - it.radius)
                drawHandle(canvas, snappedCenterX + it.radius, snappedCenterY)
                drawHandle(canvas, snappedCenterX, snappedCenterY + it.radius)
                drawHandle(canvas, snappedCenterX - it.radius, snappedCenterY)
            }
        }

        // Draw rectangles with snapping
        rectangles.forEach {
            val (snappedStartX, snappedStartY) = getNearestSnapPoint(it.startX, it.startY)
            val (snappedEndX, snappedEndY) = getNearestSnapPoint(it.endX, it.endY)

            val rectPath = Path().apply {
                moveTo(snappedStartX, snappedStartY)  // Start point
                lineTo(snappedEndX, snappedStartY)    // Top line
                lineTo(snappedEndX, snappedEndY)      // Right line
                lineTo(snappedStartX, snappedEndY)    // Bottom line
                close()                               // Close the rectangle path
            }

            if (isHandDrawnModeEnabled) {
                drawShakyPath(canvas, rectPath, paint)
               // drawHandDrawnRectangle(canvas, snappedStartX, snappedStartY, snappedEndX, snappedEndY, paint)
            } else {

            canvas.drawRect(snappedStartX, snappedStartY, snappedEndX, snappedEndY, paint)
        }
            if (it == selectedShape) {
                drawHandle(canvas, snappedStartX, snappedStartY)
                drawHandle(canvas, snappedEndX, snappedEndY)
                drawHandle(canvas, snappedStartX, snappedEndY)
                drawHandle(canvas, snappedEndX, snappedStartY)
                val midX = (snappedStartX + snappedEndX) / 2
                val midY = (snappedStartY + snappedEndY) / 2
                drawHandle(canvas, midX, midY)
            }
        }

        // Draw arcs with snapping
        arcs.forEach { arc ->
            val (snappedCenterX, snappedCenterY) = getNearestSnapPoint(arc.centerX, arc.centerY)
            val rectF = RectF(
                snappedCenterX - arc.radiusX,
                snappedCenterY - arc.radiusY,
                snappedCenterX + arc.radiusX,
                snappedCenterY + arc.radiusY
            )
            canvas.drawArc(rectF, arc.startAngle, arc.sweepAngle, false, paint)
        }

        // Draw eraser trail
        eraserTrail.forEach {
            canvas.drawCircle(it.x, it.y, 10f, eraserTrailPaint)
        }


        // Draw snapping indicator
        if (isSnapping) {
            val (snappedX, snappedY) = getNearestSnapPoint(snapPointX, snapPointY)
            canvas.drawCircle(snappedX, snappedY, snapIndicatorRadius, snapIndicatorPaint)
        }

        // Elastic effect for drawing
        if (isDrawing) {
            val paintElastic = Paint(paint).apply {
                color = Color.GRAY
                strokeWidth = 3f
                style = Paint.Style.STROKE
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
            }
            when (currentDrawingMode) {
                DrawingMode.AUTO -> {
                    val (snappedX, snappedY) = getNearestSnapPoint(currentX, currentY)
                    val (finalX, finalY) = snapToHorizontalOrVertical(startX, startY, snappedX, snappedY)

                    canvas.drawLine(startX, startY, finalX, finalY, paintElastic)
                }

                DrawingMode.RECTANGLE -> {
                    val (snappedX, snappedY) = getNearestSnapPoint(currentX, currentY)
                    canvas.drawRect(startX, startY, snappedX, snappedY, paintElastic)
                }

                DrawingMode.CIRCLE -> {
                    val radius = distance(startX, startY, currentX, currentY)
                    canvas.drawCircle(startX, startY, radius, paintElastic)
                }

                DrawingMode.ARC -> {
                    val radius = distance(startX, startY, currentX, currentY)
                    val sweepAngle = calculateSweepAngle(startX, startY, currentX, currentY)
                    canvas.drawArc(
                        startX - radius,
                        startY - radius,
                        startX + radius,
                        startY + radius,
                        0f,
                        sweepAngle,
                        false,
                        paintElastic
                    )
                }
                DrawingMode.TEXT -> {
                    val (snappedX, snappedY) = getNearestSnapPoint(currentX, currentY)
                    val (finalX, finalY) = snapToHorizontalOrVertical(startX, startY, snappedX, snappedY)

                    canvas.drawLine(startX, startY, finalX, finalY, paintElastic)
                }

                else -> {}
            }
        }

        canvas.restore()
    }


    private fun calculateAngle(startX: Float, startY: Float, endX: Float, endY: Float): Float {
        val deltaX = endX - startX
        val deltaY = endY - startY
        return Math.toDegrees(atan2(deltaY, deltaX).toDouble()).toFloat()
    }

    private fun isPointNear(px: Float, py: Float, x: Float, y: Float, threshold: Float): Boolean {
        val dx = px - x
        val dy = py - y
        return dx * dx + dy * dy <= threshold * threshold
    }

    private val eraserTrailPaint = Paint().apply {
        color = Color.LTGRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val eraserTrailRadius = 10f  // Adjust the radius as needed

    private fun findArcCenterPoints(startX: Float, startY: Float, endX: Float, endY: Float, radius: Float): List<PointF> {
        val midX = (startX + endX) / 2
        val midY = (startY + endY) / 2
        val dx = endX - startX
        val dy = endY - startY
        val dist = distance(startX, startY, endX, endY) / 2

        if (dist > radius) {
            // No valid arc if the radius is less than half the distance between points
            return emptyList()
        }

        val h = Math.sqrt((radius * radius - dist * dist).toDouble()).toFloat()
        val offsetX = h * (dy / dist)
        val offsetY = h * (-dx / dist)

        val center1 = PointF(midX + offsetX, midY + offsetY)
        val center2 = PointF(midX - offsetX, midY - offsetY)

        return listOf(center1, center2)
    }

    private fun snapToHorizontalOrVertical2(startX: Float, startY: Float, endX: Float, endY: Float): Pair<Float, Float> {
        val deltaX = endX - startX
        val deltaY = endY - startY
        val angle = Math.toDegrees(atan2(deltaY.toDouble(), deltaX.toDouble())).toFloat()

        return when {
            angle in -30f..30f -> Pair(endX, startY)  // Snap to horizontal
            angle in 60f..120f || angle in -120f..-60f -> Pair(startX, endY)  // Snap to vertical
            else -> Pair(endX, endY)  // No snapping
        }
    }

    private fun snapLineToHorizontalOrVertical(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): SnapResult {
        val (newStartX, newStartY) = snapToHorizontalOrVertical(startX, startY, endX, endY)
        val (newEndX, newEndY) = snapToHorizontalOrVertical(endX, endY, startX, startY)
        return SnapResult(newStartX, newStartY, newEndX, newEndY)
    }

    private fun snapToHorizontalOrVertical(startX: Float, startY: Float, endX: Float, endY: Float): Pair<Float, Float> {
        val dx = endX - startX
        val dy = endY - startY
        val angle = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()

        Log.d("TAG", "snapToHorizontalOrVertical angle $angle")
        //Calculate distance between start and end points
        val distance = distance(startX, startY, endX, endY)
        if (distance < minimumDistanceThreshold) {
            return Pair(startX, startY) // Don't snap if too close
        }

        return when {
            // Snap to horizontal right (0 degrees) or left (180 degrees)
            angle in -5f..5f || angle in 175f..185f ->
                if (distance > threshold) Pair(endX, startY) else Pair(startX, startY) // Prevent snapping if too close

            // Snap to vertical down (90 degrees)
            angle in 85f..95f ->
                if (distance > threshold) Pair(startX, endY) else Pair(startX, startY) // Prevent snapping if too close

            // Snap to vertical up (270 degrees)
            angle in -95f..-85f || angle in 265f..275f ->
                if (distance > threshold) Pair(startX, endY) else Pair(startX, startY) // Prevent snapping if too close

            else -> Pair(endX, endY) // No snapping, return the original end points
        }
    }


    val gridSize = 18f // Example grid size in points (0.25 inches * 72 points per inch)

    private fun drawGraphLines(canvas: Canvas) {
        if (gridSize <= 0) {
            throw IllegalArgumentException("Grid size must be positive, was: $gridSize")
        }

        // Draw vertical lines
        var x = 0f
        while (x <= canvasWidth) {
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = when {
                    x % (gridSize * 8) == 0f -> Color.RED
                    x % (gridSize * 4) == 0f -> Color.BLUE
                    else -> Color.LTGRAY
                }
            }
            canvas.drawLine(x, 0f, x, canvasHeight.toFloat(), paint)
            x += gridSize
        }

        // Draw horizontal lines
        var y = 0f
        while (y <= canvasHeight) {
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f
                color = when {
                    y % (gridSize * 8) == 0f -> Color.RED
                    y % (gridSize * 4) == 0f -> Color.BLUE
                    else -> Color.LTGRAY
                }
            }
            canvas.drawLine(0f, y, canvasWidth.toFloat(), y, paint)
            y += gridSize
        }
    }

    fun snapToGrid(x: Float, y: Float, gridSize: Float): Pair<Float, Float> {
 //       val snappedX = Math.round(x / gridSize) * gridSize
 //       val snappedY = Math.round(y / gridSize) * gridSize

 //       return Pair(snappedX, snappedY)

        val snappedX = (x / gridSize).roundToInt() * gridSize
        val snappedY = (y / gridSize).roundToInt() * gridSize
        return Pair(snappedX.toFloat(), snappedY.toFloat())
    }

    private fun snapToGrid(x: Float, y: Float): Pair<Float, Float> {
        if (currentDrawingMode == DrawingMode.FREEHAND) {
            return Pair(x, y)
        }

        val snappedX = (x / gridSize).roundToInt() * gridSize
        val snappedY = (y / gridSize).roundToInt() * gridSize
        return Pair(snappedX.toFloat(), snappedY.toFloat())
    }

    private fun inchesToPixels(inches: Float, densityDpi: Float): Int {
        return (inches * densityDpi).toInt()
    }

    private fun addTextDirectly(startX: Float, startY: Float) {
        val dialog = AlertDialog.Builder(context)
        val layout = LinearLayout(context)
        layout.orientation = LinearLayout.VERTICAL

        val editText = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            requestFocus()
        }

        editText.setOnEditorActionListener { v, actionId, event ->
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                texts.add(Text(startX, startY, text, textSize))
                invalidate()
                hideKeyboard(editText)
            }
            true
        }

        layout.addView(editText)

        dialog.setView(layout)
        dialog.setPositiveButton("OK") { _, _ ->
            val text = editText.text.toString()
            if (text.isNotEmpty()) {
                // Add the text element with the first point as startX, startY
                texts.add(Text(startX, startY, text, textSize))
                invalidate()
            }
        }

        val alertDialog = dialog.create()
        alertDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

        alertDialog.show()

        // Simulate a popup keyboard by requesting focus and showing the soft keyboard
        showKeyboard(editText)
        setDrawingMode(DrawingMode.TEXT)
    }

    private fun showKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        view.postDelayed({
            imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
        }, 0) // Delay to ensure proper focus handling in landscape mode
    }
    private fun hideKeyboard(view: View) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)

        if (view is EditText) {
            (parent as ViewGroup).removeView(view) // Remove EditText from the layout only if it's the EditText
        }

        this.requestFocus() // Focus back on the custom view
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // Adjust layout parameters or re-request focus on EditText if needed
            if (currentDrawingMode == DrawingMode.TEXT) {
                val imm =
                    context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
            }
        }
    }

    private fun eraseElement2(x: Float, y: Float): Boolean {

        var elementErased = false


        // Iterate through lines
        val lineIterator = lines.iterator()
        while (lineIterator.hasNext()) {
            val line = lineIterator.next()
            if (isPointNearLine(x, y, line)) {
                lineIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through texts
        val textIterator = texts.iterator()
        while (textIterator.hasNext()) {
            val text = textIterator.next()
            if (isPointNearText(x, y) != null) {
                textIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through circles
        val circleIterator = circles.iterator()
        while (circleIterator.hasNext()) {
            val circle = circleIterator.next()
            if (isPointNearCircle(x, y, circle)) {
                circleIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through arcs
        val arcIterator = arcs.iterator()
        while (arcIterator.hasNext()) {
            val arc = arcIterator.next()
            if (isPointNearArc(x, y, arc)) {
                arcIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through rectangles
        val rectangleIterator = rectangles.iterator()
        while (rectangleIterator.hasNext()) {
            val rectangle = rectangleIterator.next()
            if (isPointNearRectangle(x, y, rectangle)) {
                rectangleIterator.remove()
                elementErased = true
                break
            }
        }

        // Iterate through paths
        val pathIterator = paths.iterator()
        while (pathIterator.hasNext()) {
            val path = pathIterator.next()
            if (isPointNearPath(x, y, path)) {
                pathIterator.remove()
                elementErased = true
                break
            }
        }

        if (elementErased) {
            invalidate()
        }
        return elementErased
    }


    private fun distanceFromPointToLineSegment(x: Float, y: Float, start: PointF, end: PointF): Float {
        val lineLengthSquared = (end.x - start.x).pow(2) + (end.y - start.y).pow(2)
        if (lineLengthSquared == 0f) return distanceBetweenPoints(x, y, start.x, start.y)

        val t = ((x - start.x) * (end.x - start.x) + (y - start.y) * (end.y - start.y)) / lineLengthSquared
        val clampedT = t.coerceIn(0f, 1f)

        val projection = PointF(start.x + clampedT * (end.x - start.x), start.y + clampedT * (end.y - start.y))
        return distanceBetweenPoints(x, y, projection.x, projection.y)
    }

    private fun distanceBetweenPoints(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x1 - x2).pow(2) + (y1 - y2).pow(2))
    }

    private fun distanceFromPointToLine(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val A = px - x1
        val B = py - y1
        val C = x2 - x1
        val D = y2 - y1

        val dot = A * C + B * D
        val lenSq = C * C + D * D
        val param = if (lenSq != 0f) dot / lenSq else -1f

        val xx: Float
        val yy: Float

        if (param < 0) {
            xx = x1
            yy = y1
        } else if (param > 1) {
            xx = x2
            yy = y2
        } else {
            xx = x1 + param * C
            yy = y1 + param * D
        }

        val dx = px - xx
        val dy = py - yy
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }




    private fun isPointNearArc(x: Float, y: Float, arc: Arc): Boolean {
        val dx = x - arc.centerX
        val dy = y - arc.centerY

        // Calculate the angle of the point relative to the arc's center
        val pointAngle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        val normalizedPointAngle = (pointAngle + 360) % 360

        // Calculate the radii for the given angle
        val angleInRadians = Math.toRadians(normalizedPointAngle.toDouble())
        val radiusX = arc.radiusX
        val radiusY = arc.radiusY
        val radiusAtPoint = (radiusX * radiusY) / sqrt((radiusY * cos(angleInRadians)).pow(2) + (radiusX * sin(angleInRadians)).pow(2))

        // Calculate the distance from the arc's center to the point
        val distance = sqrt(dx * dx + dy * dy)
   //     val threshold = 20 // Adjust this threshold value as needed

        // Check if the point is within the arc's sweep angle
        val startAngle = arc.startAngle
        val endAngle = (startAngle + arc.sweepAngle) % 360
        val isWithinAngles = if (startAngle < endAngle) {
            normalizedPointAngle in startAngle..endAngle
        } else {
            normalizedPointAngle >= startAngle || normalizedPointAngle <= endAngle
        }

        // Check if the point is near the calculated radius for the given angle
        val isWithinRadius = kotlin.math.abs(distance - radiusAtPoint) <= threshold

        return isWithinRadius && isWithinAngles
    }

    private fun isPointNearRectangle(x: Float, y: Float, rectangle: Rectangle): Boolean {
        val rectLeft = rectangle.startX.coerceAtMost(rectangle.endX)
        val rectRight = rectangle.startX.coerceAtLeast(rectangle.endX)
        val rectTop = rectangle.startY.coerceAtMost(rectangle.endY)
        val rectBottom = rectangle.startY.coerceAtLeast(rectangle.endY)

        val centerX = (rectLeft + rectRight) / 2
        val centerY = (rectTop + rectBottom) / 2

        // Check if the point is near any side of the rectangle
        val nearLeft = abs(x - rectLeft) <= threshold && y in rectTop..rectBottom
        val nearRight = abs(x - rectRight) <= threshold && y in rectTop..rectBottom
        val nearTop = abs(y - rectTop) <= threshold && x in rectLeft..rectRight
        val nearBottom = abs(y - rectBottom) <= threshold && x in rectLeft..rectRight

        // Check if the point is near the center of the rectangle
        val nearCenter = distanceBetweenPoints(x, y, centerX, centerY) <= threshold

        return nearLeft || nearRight || nearTop || nearBottom || nearCenter
    }

    private fun distanceToLine(
        px: Float,
        py: Float,
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float
    ): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lengthSquared = dx * dx + dy * dy
        val t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared
        val nearestX = if (t < 0) x1 else if (t > 1) x2 else x1 + t * dx
        val nearestY = if (t < 0) y1 else if (t > 1) y2 else y1 + t * dy
        return distance(px, py, nearestX, nearestY)
    }

    private fun isPointNearPath(x: Float, y: Float, path: Path): Boolean {
        val bounds = RectF()
        path.computeBounds(bounds, true)
        // Adjust the hit detection logic as needed
        return bounds.contains(x, y)
    }

    private fun distance(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        return sqrt((x2 - x1).pow(2) + (y2 - y1).pow(2))
    }

    private fun getNearestSnapPoint(x: Float, y: Float): Pair<Float, Float> {
        var nearestX = x
        var nearestY = y
        var minDistance = 20f

        for (line in lines) {
            val startDistance = distance(x, y, line.startX, line.startY)
            if (startDistance < minDistance) {
                minDistance = startDistance
                nearestX = line.startX
                nearestY = line.startY
            }

            val endDistance = distance(x, y, line.endX, line.endY)
            if (endDistance < minDistance) {
                minDistance = endDistance
                nearestX = line.endX
                nearestY = line.endY
            }
        }
        return if (minDistance < snapRadius) Pair(nearestX, nearestY) else Pair(x, y)
    }

    private fun calculateSweepAngle(startX: Float, startY: Float, x: Float, y: Float): Float {
        val dx = x - startX
        val dy = y - startY
        return (Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble())) % 360).toFloat()
    }


    fun getGripAtPosition(x: Float, y: Float, shape: Shape): Handle? {
        // Implement logic to check which grip was tapped based on shape type (line, rectangle, circle, etc.)
        // If grip is near the (x, y) coordinates, return the corresponding handle
        return when (shape) {
            is Line -> shape.getNearestGrip(x, y)
            is Rectangle -> shape.getNearestGrip(x, y)
            is Circle -> shape.getNearestGrip(x, y)
            is TextElement -> shape.getNearestGrip(x, y)
            else -> null
        }
    }
    fun Line.getNearestGrip(x: Float, y: Float): Handle? {
        val threshold = 20  // You can adjust this threshold based on your needs

        return when {
            isPointNearHandle(x, y, startX, startY, threshold) -> Handle.START
            isPointNearHandle(x, y, endX, endY, threshold) -> Handle.END
            isPointNearHandle(x, y, (startX + endX) / 2, (startY + endY) / 2, threshold) -> Handle.MIDDLE
            else -> null
        }
    }
    fun Rectangle.getNearestGrip(x: Float, y: Float): Handle? {
        val threshold = 20  // Adjust the threshold

        return when {
            isPointNearHandle(x, y, startX, startY, threshold) -> Handle.TOP_LEFT
            isPointNearHandle(x, y, endX, startY, threshold) -> Handle.TOP_RIGHT
            isPointNearHandle(x, y, startX, endY, threshold) -> Handle.BOTTOM_LEFT
            isPointNearHandle(x, y, endX, endY, threshold) -> Handle.BOTTOM_RIGHT
            isPointNearHandle(x, y, (startX + endX) / 2, (startY + endY) / 2, threshold) -> Handle.MIDDLE
            else -> null
        }
    }
    fun Circle.getNearestGrip(x: Float, y: Float): Handle? {
        val threshold = 20  // Adjust the threshold

        return when {
            isPointNearHandle(x, y, centerX, centerY, threshold) -> Handle.MIDDLE
            isPointNearHandle(x, y, centerX, centerY - radius, threshold) -> Handle.TOP
            isPointNearHandle(x, y, centerX + radius, centerY, threshold) -> Handle.RIGHT
            isPointNearHandle(x, y, centerX, centerY + radius, threshold) -> Handle.BOTTOM
            isPointNearHandle(x, y, centerX - radius, centerY, threshold) -> Handle.LEFT
            else -> null
        }
    }
    fun TextElement.getNearestGrip(x: Float, y: Float): Handle? {
        val threshold = 20  // Adjust the threshold

        // Assuming the text has only one handle for dragging
        return if (isPointNearHandle(x, y, startX, startY, threshold)) {
            Handle.LEFT  // You can adjust this based on your handle logic for text
        } else {
            null
        }
    }
    fun isPointNearHandle(x: Float, y: Float, handleX: Float, handleY: Float, threshold: Int = 20): Boolean {
        val dx = x - handleX
        val dy = y - handleY
        return dx * dx + dy * dy <= threshold * threshold
    }
    // Handle double-tap gesture


    fun clearCanvas() {
        path.reset()
        lines.clear()
        texts.clear()
        circles.clear()
        arcs.clear()
        rectangles.clear()
        invalidate()
    }

    override fun onSaveInstanceState(): Parcelable {
        val superState = super.onSaveInstanceState()
        val savedState = SavedState(superState)
        savedState.lines = lines
        savedState.texts = texts
        savedState.circles = circles
        savedState.arcs = arcs
        savedState.rectangles = rectangles

        return savedState


    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is SavedState) {
            super.onRestoreInstanceState(state.superState)
            lines = state.lines
            texts = state.texts
            circles = state.circles
            arcs = state.arcs
            rectangles = state.rectangles
        } else {
            super.onRestoreInstanceState(state)
        }
    }


    private fun calculateZoomToFitScaleFactor(): Float {
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
  //      val canvasWidth = sheetWidth.toFloat()
  //      val canvasHeight = sheetHeight.toFloat()

        val scaleX = viewWidth / canvasWidth
        val scaleY = viewHeight / canvasHeight

        return minOf(scaleX, scaleY)
    }

    fun saveDrawingContent(filename: String) {
        val gson = Gson()
        val content = mapOf(
            "lines" to lines,
            "texts" to texts,
            "circles" to circles,
            "arcs" to arcs,
            "rectangles" to rectangles
        )
        val json = gson.toJson(content)
        val file = File(context.filesDir, filename)
        file.writeText(json)
    }

    fun loadDrawingContent(filename: String) {
        val file = File(context.filesDir, filename)
        if (file.exists()) {
            val json = file.readText()
            val gson = Gson()
            val content = gson.fromJson<Map<String, List<Any>>>(json, Map::class.java)
            lines = content["lines"] as MutableList<Line>
            texts = content["texts"] as MutableList<Text>
            circles = content["circles"] as MutableList<Circle>
            arcs = content["arcs"] as MutableList<Arc>
            rectangles = content["rectangles"] as MutableList<Rectangle>
            invalidate()
        }
    }

    fun setCurrentPageNumber(pageNumber: Int) {
        currentPageNumber = pageNumber
        invalidate() // Redraw the view
    }

    private fun drawPageNumber(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 48f
            textAlign = Paint.Align.CENTER
        }
        setCurrentPageNumber(currentPageNumber)
        val pageNumberText = "$currentPageNumber"

        // Calculate the position for the text based on the canvas size
        val xPos = (width / 2).toFloat()
        val yPos =
            (height / 2 + canvasHeightInPixels / 2 - 50).toFloat() // Adjust this value to position the text

        //       Draw a border around the 8.5x11 canvas
        val borderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

        val left = (width / 2 - canvasWidthInPixels / 2).toFloat()
        val top = (height / 2 - canvasHeightInPixels / 2).toFloat()
        val right = (width / 2 + canvasWidthInPixels / 2).toFloat()
        val bottom = (height / 2 + canvasHeightInPixels / 2).toFloat()
        canvas.drawRect(left, top, right, bottom, borderPaint)

        // Draw the page number text
        //    canvas.drawText(pageNumberText, xPos, yPos, paint)
    }



    fun setDrawingMode(mode: DrawingMode) {
        // Clear the grips and previous shape when switching to a new mode
        if (areGripsDisplayed || selectedShape != null) {
            areGripsDisplayed = false
            selectedShape = null // Clear the previously selected shape

            invalidate() // Redraw the canvas without grips
        }
             currentDrawingMode = mode
              invalidate()
        //      setDrawingModeWithTimeout(mode, 0) // Set timeout to 5 seconds (or any desired duration)
    }

    private fun startModeTimer() {
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = Runnable {
            currentDrawingMode = DrawingMode.NONE
            invalidate()
        }
        handler.postDelayed(timerRunnable!!, 5000) // 5 seconds timeout
    }

    fun setScaleAndTranslation(scaleFactor: Float, translationX: Float, translationY: Float) {
        this.scaleFactor = scaleFactor
        this.translationX = translationX
        this.translationY = translationY
        matrix.setScale(scaleFactor, scaleFactor)
        matrix.postTranslate(translationX, translationY)
        invalidate()
    }

    //  override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
    //      super.onSizeChanged(w, h, oldw, oldh)
    //      zoomToFitPage()
    //  }

    fun zoomToFitPage() {
        // Calculate the scale factor to fit the page width
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()
        val pageWidth = (canvasWidth + 5f)
        val pageHeight = (canvasHeight + 5f)

        val scaleX = viewWidth / pageWidth
        val scaleY = viewHeight / pageHeight
        scaleFactor = minOf(scaleX, scaleY)

        // Center the page within the view
        translationX = (viewWidth - pageWidth * scaleFactor) / 2
        translationY = (viewHeight - pageHeight * scaleFactor) / 2

        invalidate()
    }

    companion object {
        private const val SNAP_THRESHOLD = 20f // Adjust this value as needed
        private const val TAG = "DrawingView"
    }

    private fun findNearestEndpoint(x: Float, y: Float): Pair<Float, Float>? {
        var nearestPoint: Pair<Float, Float>? = null
        var minDistance = Float.MAX_VALUE

        lines.forEach { line ->
            val points = listOf(line.startX to line.startY, line.endX to line.endY)
            points.forEach { (px, py) ->
                val distance = distance(x, y, px, py)
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    nearestPoint = px to py
                }
            }
        }

        rectangles.forEach { rect ->
            val points = listOf(rect.startX to rect.startY, rect.endX to rect.endY)
            points.forEach { (px, py) ->
                val distance = distance(x, y, px, py)
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    nearestPoint = px to py
                }
            }
        }

        circles.forEach { circle ->
            val points = listOf(circle.centerX to circle.centerY)
            points.forEach { (px, py) ->
                val distance = distance(x, y, px, py)
                if (distance < SNAP_THRESHOLD && distance < minDistance) {
                    minDistance = distance
                    nearestPoint = px to py
                }
            }
        }

        return nearestPoint
    }

    private fun eraseElementsAlongPath(path: Path) {

        selectedShape = null
        invalidate()

        val eraserWidth = eraserEffectPaint.strokeWidth / 2

        val lineIterator = lines.iterator()
        while (lineIterator.hasNext()) {
            val line = lineIterator.next()
            if (isLineIntersectingPath(line, path, eraserWidth)) {
                lineIterator.remove()
            }
        }

        // Remove rectangles that intersect with the eraser path
        val rectIterator = rectangles.iterator()
        while (rectIterator.hasNext()) {
            val rect = rectIterator.next()
            if (isRectangleIntersectingPath(rect, path, eraserWidth)) {
                rectIterator.remove()
            }
        }

        // Remove circles that intersect with the eraser path
        val circleIterator = circles.iterator()
        while (circleIterator.hasNext()) {
            val circle = circleIterator.next()
            if (isCircleIntersectingPath(circle, path, eraserWidth)) {
                circleIterator.remove()
            }
        }

        // Remove arcs that intersect with the eraser path
        val arcIterator = arcs.iterator()
        while (arcIterator.hasNext()) {
            val arc = arcIterator.next()
            if (isArcIntersectingPath(arc, path, eraserWidth)) {
                arcIterator.remove()
            }
        }

        // Remove texts that intersect with the eraser path
        val textIterator = texts.iterator()
        while (textIterator.hasNext()) {
            val text = textIterator.next()
            if (isTextIntersectingPath(text, path, eraserWidth)) {
                textIterator.remove()
            }
        }

        // Remove freehand lines that intersect with the eraser path
        val freehandLineIterator = freehandLines.iterator()
        while (freehandLineIterator.hasNext()) {
            val freehandLine = freehandLineIterator.next()
            if (isLineIntersectingPath(freehandLine, path, eraserWidth)) {
                freehandLineIterator.remove()
            }
        }

        // Trigger a redraw of the canvas
        invalidate()
    }

    private fun isLineIntersectingPath(line: Line, path: Path, eraserWidth: Float): Boolean {
        val pathSegments = getPathSegments(path)
        for (segment in pathSegments) {
            if (doLineSegmentsIntersect(
                    PointF(line.startX, line.startY),
                    PointF(line.endX, line.endY),
                    segment.first,
                    segment.second,
                    eraserWidth
                )) {
                return true
            }
        }
        return false
    }



    private fun isRectangleIntersectingPath(rect: Rectangle, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val rectBounds = RectF(rect.left, rect.top, rect.right, rect.bottom)
        return RectF.intersects(pathBounds, rectBounds)
    }

    private fun isCircleIntersectingPath(circle: Circle, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val circleBounds = RectF(
            circle.centerX - circle.radius,
            circle.centerY - circle.radius,
            circle.centerX + circle.radius,
            circle.centerY + circle.radius
        )
        return pathBounds.intersect(circleBounds)
    }

    private fun isArcIntersectingPath(arc: Arc, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)

        // Use radiusX and radiusY for the bounding rectangle of the arc
        val arcBounds = RectF(
            arc.centerX - arc.radiusX,
            arc.centerY - arc.radiusY,
            arc.centerX + arc.radiusX,
            arc.centerY + arc.radiusY
        )
        return pathBounds.intersect(arcBounds)
    }


    private fun isTextIntersectingPath(text: Text, path: Path, eraserWidth: Float): Boolean {
        val pathBounds = RectF()
        path.computeBounds(pathBounds, true)
        val textBounds = RectF(text.x, text.y - text.height, text.x + text.width, text.y)
        return pathBounds.intersect(textBounds)
    }

    private fun getPathSegments(path: Path): List<Pair<PointF, PointF>> {
        val segments = mutableListOf<Pair<PointF, PointF>>()
        val pathMeasure = PathMeasure(path, false)
        val coords = FloatArray(2)
        var startX = 0f
        var startY = 0f
        var distance = 0f
        var segmentStarted = false

        while (distance < pathMeasure.length) {
            pathMeasure.getPosTan(distance, coords, null)
            val x = coords[0]
            val y = coords[1]

            if (segmentStarted) {
                segments.add(PointF(startX, startY) to PointF(x, y))
            } else {
                segmentStarted = true
            }

            startX = x
            startY = y
            distance += 20f // Increment this value for more precision
        }

        return segments
    }

    private fun doLineSegmentsIntersect(
        p1: PointF, p2: PointF,
        q1: PointF, q2: PointF,
        eraserWidth: Float
    ): Boolean {
        // Implementation of line segment intersection algorithm with eraser width
        val expandedP1 = expandPoint(p1, eraserWidth)
        val expandedP2 = expandPoint(p2, eraserWidth)
        val expandedQ1 = expandPoint(q1, eraserWidth)
        val expandedQ2 = expandPoint(q2, eraserWidth)

        val o1 = orientation(expandedP1, expandedP2, expandedQ1)
        val o2 = orientation(expandedP1, expandedP2, expandedQ2)
        val o3 = orientation(expandedQ1, expandedQ2, expandedP1)
        val o4 = orientation(expandedQ1, expandedQ2, expandedP2)

        if (o1 != o2 && o3 != o4) {
            return true
        }

        if (o1 == 0 && onSegment(expandedP1, expandedQ1, expandedP2)) return true
        if (o2 == 0 && onSegment(expandedP1, expandedQ2, expandedP2)) return true
        if (o3 == 0 && onSegment(expandedQ1, expandedP1, expandedQ2)) return true
        if (o4 == 0 && onSegment(expandedQ1, expandedP2, expandedQ2)) return true

        return false
    }
    private fun orientation(p: PointF, q: PointF, r: PointF): Int {
        val value = (q.y - p.y) * (r.x - q.x) - (q.x - p.x) * (r.y - q.y)
        return when {
            value == 0f -> 0
            value > 0 -> 1
            else -> 2
        }
    }

    private fun expandPoint(point: PointF, eraserWidth: Float): PointF {
        // Adjust the point coordinates based on eraser width
        return PointF(point.x - eraserWidth, point.y - eraserWidth)
    }

    private fun onSegment(p: PointF, q: PointF, r: PointF): Boolean {
        return q.x <= max(p.x, r.x) && q.x >= min(p.x, r.x) && q.y <= max(p.y, r.y) && q.y >= min(p.y, r.y)
    }

    private fun drawHandle(canvas: Canvas, x: Float, y: Float) {
        val handleSize = 10f
        val handlePaint = Paint().apply {
            color = Color.RED
            style = Paint.Style.FILL
        }
        canvas.drawCircle(x, y, handleSize, handlePaint)
    }
    private fun selectText(x: Float, y: Float): Text? {
        for (text in texts) {
            val textBounds = getTextBounds(text)
            if (textBounds.contains(x, y)) {
                return text
            }
        }
        return null
    }
    // Helper function to get the bounding rectangle of the text
    private fun getTextBounds(text: Text): RectF {
        val paint = Paint()
        paint.textSize = text.textSize
        val textWidth = paint.measureText(text.text)
        val textHeight = paint.descent() - paint.ascent()
        return RectF(text.x, text.y - textHeight, text.x + textWidth, text.y)
    }

    fun isTablet(context: Context): Boolean {
        val configuration = context.resources.configuration
        val screenLayout = configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        val isLarge = screenLayout == Configuration.SCREENLAYOUT_SIZE_LARGE
        val isXLarge = screenLayout == Configuration.SCREENLAYOUT_SIZE_XLARGE
        return isLarge || isXLarge
    }



    private fun showAlertDialog(message: String) {
        val builder = AlertDialog.Builder(context)
        builder.setMessage(message)
            .setPositiveButton("OK") { dialog, id ->
                // User clicked OK button
                dialog.dismiss() // Close the dialog
            }
        val alert = builder.create()
        alert.show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (isTyping) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    // Finalize text input and hide keyboard on Enter key press
                    finalizeTextInput()
                    hideKeyboard(this) // Hide the keyboard after finalizing input
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    // Handle backspace: remove the last character
                    if (inputText.isNotEmpty()) {
                        inputText = inputText.dropLast(1)
                        invalidate() // Redraw the input box with updated text
                    }
                    return true
                }
                else -> {
                    // Add the character to the input text
                    inputText += event.displayLabel
                    invalidate() // Redraw the input box with updated text
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun startTextInput(x: Float, y: Float) {
        Log.d("TAG", "fun startTextInput")
        isTyping = true
        inputBoxRect = RectF(x, y, x + 300, y + 100)
        inputText = ""
        invalidate()

        requestFocus()
        showKeyboard(this) // Show the keyboard when typing starts
    }

    private fun finalizeTextInput2() {
        isTyping = false
        inputBoxRect = null

        // Save the text to the list of shapes
        val newText = Text(x = inputBoxRect?.left ?: 0f, y = inputBoxRect?.top ?: 0f, text = inputText, textSize = textPaint.textSize)
        shapes.add(newText)

        // Clear input text and update the canvas
        inputText = ""
        invalidate()
        clearFocus()

        // Hide the keyboard
        hideKeyboard(this)
    }
    private fun finalizeTextInput() {
        // Finalize the text input and render it on the canvas
        Log.d("TAG", "fun finalizeTextInput - isTyping $isTyping inputBoxRect $inputBoxRect")
        isTyping = false

        // Save the current text and position as a TextElement or Text object
        inputBoxRect?.let {

            val newText = TextElement(
                text = currentInputText, // Replace with the variable holding the current input text
                x = it.left,
                y = it.top,
                paint = Paint().apply {
                    textSize = currentTextSize // Replace with the current text size variable
                    isAntiAlias = true
                    color = currentTextColor // Replace with the current text color variable
                }
            )

            // Add the new text to your list of shapes/text elements to be drawn on the canvas
            textElements.add(newText) // Assuming you have a shapes list where you store drawable elements
        }

        // Clear the input box rectangle and text input field
     //   inputBoxRect = null
     //   currentInputText = "" // Reset the text input variable

        // Refresh the canvas to show the newly added text
        invalidate()

        // Clear focus from the input box
       clearFocus()
        // Hide the keyboard
        hideKeyboard(this)
    }
  //  private fun recognizeText() {
  //      // Convert the drawn path into a bitmap to recognize
  //      val bitmap = createBitmapFromView(this)
  //      val inputImage = InputImage.fromBitmap(bitmap, 0)

        // Run text recognition using ML Kit
  //      textRecognizer?.process(inputImage)
  //          ?.addOnSuccessListener { visionText ->
                // Draw recognized text on the canvas or handle it
  //              handleRecognizedText(visionText.text)
  //          }
  //          ?.addOnFailureListener { e ->
                // Handle failure
  //              Toast.makeText(context, "Text Recognition failed", Toast.LENGTH_SHORT).show()
 //           }
 //   }

    private fun handleRecognizedText(text: String) {
        // Handle recognized text - show it on the canvas or store it
        Toast.makeText(context, "Recognized text: $text", Toast.LENGTH_SHORT).show()
    }

    private fun createBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    fun drawHandDrawnLine(canvas: Canvas, line: Line, paint: Paint) {
        // Multiple slightly offset lines for the sketchy effect
        for (i in 1..3) {
            val offsetX = (Math.random() - 0.5).toFloat() * 5
            val offsetY = (Math.random() - 0.5).toFloat() * 5
            canvas.drawLine(line.startX + offsetX, line.startY + offsetY, line.endX + offsetX, line.endY + offsetY, paint)
        }
    }
    fun drawHandDrawnRectangle(
        canvas: Canvas,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
        paint: Paint
    ) {
        // Create a Path for the hand-drawn rectangle
        val path = Path()

        // Add slight randomness to each corner
        val randomOffset = 10f // Adjust for a more hand-drawn effect
        val topLeftX = startX + getRandomOffset(randomOffset)
        val topLeftY = startY + getRandomOffset(randomOffset)
        val topRightX = endX + getRandomOffset(randomOffset)
        val topRightY = startY + getRandomOffset(randomOffset)
        val bottomRightX = endX + getRandomOffset(randomOffset)
        val bottomRightY = endY + getRandomOffset(randomOffset)
        val bottomLeftX = startX + getRandomOffset(randomOffset)
        val bottomLeftY = endY + getRandomOffset(randomOffset)

        // Move to the first point (top-left)
        path.moveTo(topLeftX, topLeftY)

        // Draw top line (top-left to top-right)
        path.lineTo(topRightX, topRightY)

        // Draw right line (top-right to bottom-right)
        path.lineTo(bottomRightX, bottomRightY)

        // Draw bottom line (bottom-right to bottom-left)
        path.lineTo(bottomLeftX, bottomLeftY)

        // Draw left line (bottom-left to top-left)
        path.lineTo(topLeftX, topLeftY)

        // Draw the path on the canvas
        canvas.drawPath(path, paint)
    }

    fun getRandomOffset(maxOffset: Float): Float {
        return (Math.random().toFloat() * maxOffset * 2) - maxOffset
    }
    fun drawShakyPath(
        canvas: Canvas,
        path: Path,
        paint: Paint,
        maxOffset: Float = 0.5f // Adjust this value for more/less shakiness
    ) {
        // Create a new Path with random offsets to simulate shakiness
        val shakyPath = Path()

        // Use a PathMeasure to walk along the original path
        val pathMeasure = PathMeasure(path, false)
        val length = pathMeasure.length
        val segmentLength = 10f // Step size to break down the path into small segments

        var distance = 0f
        val pos = FloatArray(2)

        while (distance <= length) {
            // Get the position on the original path at this distance
            pathMeasure.getPosTan(distance, pos, null)

            // Apply a random offset to simulate shakiness
            val offsetX = getRandomOffset(maxOffset)
            val offsetY = getRandomOffset(maxOffset)

            if (distance == 0f) {
                // Move to the starting point with a random offset
                shakyPath.moveTo(pos[0] + offsetX, pos[1] + offsetY)
            } else {
                // Line to the next point with a random offset
                shakyPath.lineTo(pos[0] + offsetX, pos[1] + offsetY)
            }

            // Increment distance by the segment length
            distance += segmentLength
        }

        // Draw the shaky path on the canvas
        canvas.drawPath(shakyPath, paint)
    }

    fun drawShakyPath(canvas: Canvas, startX: Float, startY: Float, endX: Float, endY: Float, paint: Paint) {
        val path = Path()
        path.moveTo(startX, startY)
        val controlX = (startX + endX) / 2 + (Math.random() - 0.5).toFloat() * 20
        val controlY = (startY + endY) / 2 + (Math.random() - 0.5).toFloat() * 20
        path.quadTo(controlX, controlY, endX, endY)

        canvas.drawPath(path, paint)
    }

    fun drawHandDrawnCircle(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        paint: Paint
    ) {
        // Create a Path for the hand-drawn circle
        val path = Path()

        // Define the number of segments to approximate the hand-drawn circle
        val segments = 200
        val randomOffset = radius * 0.01f // Random offset as a small percentage of the radius

        // Calculate the angle step between each segment
        val angleStep = 360f / segments

        for (i in 0..segments) {
            // Calculate the current angle in radians
            val angle = Math.toRadians((i * angleStep).toDouble())

            // Get the x and y position on the circle with a slight random offset
            val x = (centerX + (radius + getRandomOffset(randomOffset)) * Math.cos(angle)).toFloat()
            val y = (centerY + (radius + getRandomOffset(randomOffset)) * Math.sin(angle)).toFloat()

            if (i == 0) {
                // Move to the first point (do not draw yet)
                path.moveTo(x, y)
            } else {
                // Draw lines to the next points
                path.lineTo(x, y)
            }
        }

        // Close the path to form the circle
        path.close()

        // Draw the path on the canvas
        canvas.drawPath(path, paint)
    }

    val sketchyPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.BLACK
        strokeWidth = 3f
        pathEffect = DashPathEffect(floatArrayOf(10f, 5f), 0f) // Create dashed line
        isAntiAlias = true
    }
    fun setHandDrawnMode(enabled: Boolean) {
        isHandDrawnModeEnabled = enabled
        invalidate() // Trigger a redraw
    }
    // Method to update the page title from MainActivity
    fun updatePageTitle(newTitle: String) {
        pageTitle = newTitle
        invalidate() // Redraw the view if necessary
    }
}


